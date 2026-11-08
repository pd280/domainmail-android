use crate::error::{Error, Result};
use crate::session::Session;
use crate::util::{parse_dm_session_value, truncate};
use reqwest::blocking::{Client, RequestBuilder, Response};
use reqwest::header::{CONTENT_TYPE, COOKIE, SET_COOKIE};
use reqwest::{Method, StatusCode};
use serde_json::Value;
use std::path::PathBuf;
use std::time::Duration;

pub struct ClientConfig {
    pub base_url: String,
    pub allow_insecure_tls: bool,
    pub timeout: Duration,
    pub store_path: Option<PathBuf>,
}

/// 与 DomainMail Web 服务（默认 :8080）对话的 HTTP 客户端。
/// Cookie 会话手动管理：登录响应里取 dm_session，之后每个请求带回。
pub struct DmClient {
    http: Client,
    base_url: String,
    session: Session,
}

impl DmClient {
    pub fn new(cfg: ClientConfig) -> Result<Self> {
        let base_url = cfg.base_url.trim().trim_end_matches('/').to_string();
        if !(base_url.starts_with("http://") || base_url.starts_with("https://")) {
            return Err(Error::Invalid(
                "baseUrl 必须以 http:// 或 https:// 开头，例如 https://mail.example.com:8080"
                    .into(),
            ));
        }

        // rustls 纯 Rust TLS，Android 交叉编译不需要 OpenSSL。
        let http = Client::builder()
            .timeout(cfg.timeout)
            .connect_timeout(Duration::from_secs(10))
            .danger_accept_invalid_certs(cfg.allow_insecure_tls)
            .redirect(reqwest::redirect::Policy::none())
            .build()?;

        let mut session = Session::default();
        if let Some(path) = cfg.store_path.as_deref() {
            session.attach(path);
        }

        Ok(Self {
            http,
            base_url,
            session,
        })
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    pub fn is_logged_in(&self) -> bool {
        self.session.token().is_some()
    }

    pub fn clear_token(&mut self) {
        self.session.set_token(None);
    }

    fn url(&self, path: &str) -> String {
        if path.starts_with("http://") || path.starts_with("https://") {
            path.to_string()
        } else {
            format!("{}{}", self.base_url, path)
        }
    }

    fn builder(&self, method: Method, path: &str) -> RequestBuilder {
        let mut rb = self.http.request(method, self.url(path));
        if let Some(token) = self.session.token() {
            rb = rb.header(COOKIE, format!("dm_session={token}"));
        }
        rb
    }

    /// 所有 JSON 接口走这里；返回原始 JSON 以便服务端新增字段时客户端无需改动。
    pub fn request_json(
        &mut self,
        method: Method,
        path: &str,
        query: &[(String, String)],
        body: Option<&Value>,
    ) -> Result<Value> {
        let mut rb = self.builder(method, path);
        if !query.is_empty() {
            rb = rb.query(query);
        }
        if let Some(payload) = body {
            // .json() 自己会设置 Content-Type: application/json，不要重复插入同名头
            rb = rb.json(payload);
        }
        let response = rb.send()?;
        self.finish(response)
    }

    /// 下载 .eml 原文 / 附件，返回 (字节, content-type, content-disposition)。
    pub fn request_bytes(&mut self, path: &str) -> Result<(Vec<u8>, String, String)> {
        let response = self.builder(Method::GET, path).send()?;
        let status = response.status();
        self.capture_cookies(&response);

        if status == StatusCode::UNAUTHORIZED {
            self.clear_token();
            return Err(Error::Unauthorized);
        }
        if !status.is_success() {
            let text = response.text().unwrap_or_default();
            let message =
                extract_error(&text).unwrap_or_else(|| format!("HTTP {}", status.as_u16()));
            return Err(Error::Api {
                status: status.as_u16(),
                message,
            });
        }

        let mime = response
            .headers()
            .get(CONTENT_TYPE)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("application/octet-stream")
            .to_string();
        let disposition = response
            .headers()
            .get("content-disposition")
            .and_then(|v| v.to_str().ok())
            .unwrap_or("")
            .to_string();
        let bytes = response.bytes()?.to_vec();
        Ok((bytes, mime, disposition))
    }

    fn capture_cookies(&mut self, response: &Response) {
        let mut next: Option<String> = None;
        let mut cleared = false;
        for raw in response.headers().get_all(SET_COOKIE).iter() {
            let Ok(text) = raw.to_str() else { continue };
            if text.contains("dm_session=") && text.contains("Max-Age=0") {
                cleared = true;
            } else if let Some(token) = parse_dm_session_value(text) {
                next = Some(token);
            }
        }
        if cleared {
            self.session.set_token(None);
        } else if let Some(token) = next {
            self.session.set_token(Some(token));
        }
    }

    fn finish(&mut self, response: Response) -> Result<Value> {
        let status = response.status();
        self.capture_cookies(&response);

        if status == StatusCode::UNAUTHORIZED {
            self.clear_token();
            return Err(Error::Unauthorized);
        }

        let text = response.text().unwrap_or_default();
        if !status.is_success() {
            let message = extract_error(&text).unwrap_or_else(|| {
                if text.trim().is_empty() {
                    format!("HTTP {}", status.as_u16())
                } else {
                    truncate(&text, 300)
                }
            });
            return Err(Error::Api {
                status: status.as_u16(),
                message,
            });
        }

        if text.trim().is_empty() {
            return Ok(Value::Object(Default::default()));
        }
        serde_json::from_str::<Value>(&text).map_err(|_| Error::Api {
            status: status.as_u16(),
            message: format!("响应不是合法 JSON: {}", truncate(&text, 200)),
        })
    }
}

fn extract_error(text: &str) -> Option<String> {
    let value: Value = serde_json::from_str(text).ok()?;
    value.get("error")?.as_str().map(str::to_string)
}
