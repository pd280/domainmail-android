use crate::client::{ClientConfig, DmClient};
use crate::error::{Error, Result};
use crate::model::{
    get_bool, get_i64, get_opt_str, get_str, get_str_list, InitConfig, MessageQuery, FOLDERS,
};
use crate::util::{filename_from_disposition, safe_filename, urlencode};
use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use reqwest::Method;
use serde_json::{json, Map, Value};
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// DomainMail REST API 的门面。Android(JNI) 与桌面 CLI 共用同一套实现。
pub struct Api {
    client: DmClient,
    data_dir: Option<PathBuf>,
}

impl Api {
    pub fn new(cfg: InitConfig) -> Result<Self> {
        let data_dir = cfg.data_dir.as_ref().map(PathBuf::from);
        let store_path = data_dir.as_ref().map(|dir| dir.join("session.json"));
        let timeout = cfg.timeout_seconds.unwrap_or(20).clamp(1, 120) as u64;
        let client = DmClient::new(ClientConfig {
            base_url: cfg.base_url,
            allow_insecure_tls: cfg.allow_insecure_tls,
            timeout: Duration::from_secs(timeout),
            store_path,
        })?;
        Ok(Self { client, data_dir })
    }

    pub fn is_logged_in(&self) -> bool {
        self.client.is_logged_in()
    }

    pub fn base_url(&self) -> &str {
        self.client.base_url()
    }

    fn download_dir(&self) -> PathBuf {
        self.data_dir
            .clone()
            .unwrap_or_else(|| std::env::temp_dir().join("domainmail"))
            .join("downloads")
    }

    /// 附件 / .eml 由 Rust 直接落盘，JNI 边界只传一个小 JSON，不搬运大字节数组。
    fn save_download(&self, name: &str, bytes: &[u8], mime: &str) -> Result<Value> {
        let dir = self.download_dir();
        std::fs::create_dir_all(&dir)?;
        let safe = safe_filename(name);
        let stamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        let path = dir.join(format!("{stamp}_{safe}"));
        std::fs::write(&path, bytes)?;
        Ok(json!({
            "path": path.to_string_lossy(),
            "filename": safe,
            "mime": mime,
            "size": bytes.len()
        }))
    }

    /// 单个入口分发所有方法。返回值是服务端原始 JSON（外加少量本地计算结果），
    /// 服务端加字段时 App 不需要重新发版。
    pub fn dispatch(&mut self, method: &str, p: &Value) -> Result<Value> {
        match method {
            "ping" | "version" => Ok(json!({
                "version": env!("CARGO_PKG_VERSION"),
                "initialized": true,
                "baseUrl": self.client.base_url(),
                "loggedIn": self.client.is_logged_in(),
                "folderLabels": folder_labels()
            })),
            "folderLabels" => Ok(folder_labels()),

            // ---------- 认证 ----------
            "registerConfig" => {
                self.client
                    .request_json(Method::GET, "/api/auth/register-config", &[], None)
            }
            "login" => {
                let email = get_str(p, "email").trim().to_string();
                let password = get_str(p, "password");
                if email.is_empty() || password.is_empty() {
                    return Err(Error::Invalid("邮箱和密码不能为空".into()));
                }
                let body = json!({ "email": email, "password": password });
                self.client
                    .request_json(Method::POST, "/api/auth/login", &[], Some(&body))
            }
            "register" => {
                let body = json!({
                    "email": get_str(p, "email").trim(),
                    "password": get_str(p, "password"),
                    "displayName": get_str(p, "displayName")
                });
                self.client
                    .request_json(Method::POST, "/api/auth/register", &[], Some(&body))
            }
            "logout" => {
                let result = self
                    .client
                    .request_json(Method::POST, "/api/auth/logout", &[], None);
                self.client.clear_token();
                result
            }
            "me" => self
                .client
                .request_json(Method::GET, "/api/auth/me", &[], None),
            "changePassword" => {
                let body = json!({
                    "oldPassword": get_str(p, "oldPassword"),
                    "newPassword": get_str(p, "newPassword")
                });
                self.client
                    .request_json(Method::POST, "/api/auth/password", &[], Some(&body))
            }

            // ---------- 文件夹与邮件 ----------
            "folders" => {
                let mut query: Vec<(String, String)> = Vec::new();
                if let Some(mb) = get_opt_str(p, "mailbox") {
                    query.push(("mailbox".into(), mb));
                }
                self.client
                    .request_json(Method::GET, "/api/folders", &query, None)
            }
            "messages" => {
                let mq = MessageQuery::from_value(p);
                let mut query = vec![
                    ("folder".into(), mq.folder),
                    ("limit".into(), mq.limit.to_string()),
                    ("offset".into(), mq.offset.to_string()),
                ];
                if !mq.q.is_empty() {
                    query.push(("q".into(), mq.q));
                }
                if mq.unread_only {
                    query.push(("unread".into(), "1".into()));
                }
                if let Some(mb) = mq.mailbox {
                    query.push(("mailbox".into(), mb));
                }
                self.client
                    .request_json(Method::GET, "/api/messages", &query, None)
            }
            "message" => {
                let id = require_id(p)?;
                self.client
                    .request_json(Method::GET, &format!("/api/messages/{id}"), &[], None)
            }
            "rawMessage" => {
                let id = require_id(p)?;
                let (bytes, mime, disposition) = self
                    .client
                    .request_bytes(&format!("/api/messages/{id}/raw"))?;
                let name = filename_from_disposition(&disposition)
                    .unwrap_or_else(|| format!("message-{id}.eml"));
                self.save_download(&name, &bytes, &mime)
            }
            "attachment" => {
                let id = require_id(p)?;
                let (bytes, mime, disposition) = self
                    .client
                    .request_bytes(&format!("/api/attachments/{id}"))?;
                let name = get_opt_str(p, "filename")
                    .or_else(|| filename_from_disposition(&disposition))
                    .unwrap_or_else(|| format!("attachment-{id}.bin"));
                self.save_download(&name, &bytes, &mime)
            }
            "setFlags" => {
                let id = require_id(p)?;
                let body = json!({
                    "add": get_str_list(p, "add"),
                    "remove": get_str_list(p, "remove")
                });
                self.client.request_json(
                    Method::POST,
                    &format!("/api/messages/{id}/flags"),
                    &[],
                    Some(&body),
                )
            }
            "markRead" | "markUnread" | "star" | "unstar" => {
                let id = require_id(p)?;
                let (add, remove): (Vec<String>, Vec<String>) = match method {
                    "markRead" => (vec!["\\Seen".into()], vec![]),
                    "markUnread" => (vec![], vec!["\\Seen".into()]),
                    "star" => (vec!["\\Flagged".into()], vec![]),
                    _ => (vec![], vec!["\\Flagged".into()]),
                };
                let body = json!({ "add": add, "remove": remove });
                self.client.request_json(
                    Method::POST,
                    &format!("/api/messages/{id}/flags"),
                    &[],
                    Some(&body),
                )
            }
            "moveMessage" => {
                let id = require_id(p)?;
                let folder = get_str(p, "folder");
                if folder.is_empty() {
                    return Err(Error::Invalid("缺少目标文件夹".into()));
                }
                let body = json!({ "folder": folder });
                self.client.request_json(
                    Method::POST,
                    &format!("/api/messages/{id}/move"),
                    &[],
                    Some(&body),
                )
            }
            "deleteMessage" => {
                let id = require_id(p)?;
                self.client
                    .request_json(Method::DELETE, &format!("/api/messages/{id}"), &[], None)
            }
            "emptyFolder" => {
                let folder = get_str(p, "folder");
                if folder.is_empty() {
                    return Err(Error::Invalid("缺少文件夹名".into()));
                }
                self.client.request_json(
                    Method::POST,
                    &format!("/api/folders/{}/empty", urlencode(&folder)),
                    &[],
                    None,
                )
            }

            // ---------- 发信 ----------
            "send" => {
                let body = build_send_body(p)?;
                self.client
                    .request_json(Method::POST, "/api/send", &[], Some(&body))
            }

            // ---------- 无限邮箱 / 邮箱切换器 ----------
            "mailboxes" => {
                let mut query: Vec<(String, String)> = Vec::new();
                if let Some(cur) = get_opt_str(p, "current") {
                    query.push(("current".into(), cur));
                }
                self.client
                    .request_json(Method::GET, "/api/mailboxes", &query, None)
            }
            "aliases" => self
                .client
                .request_json(Method::GET, "/api/aliases", &[], None),
            "createAlias" => {
                let mut body = Map::new();
                if let Some(address) = get_opt_str(p, "address") {
                    body.insert("address".into(), Value::String(address));
                } else {
                    let prefix = get_str(p, "prefix");
                    if prefix.is_empty() {
                        return Err(Error::Invalid("请填写别名前缀".into()));
                    }
                    body.insert("prefix".into(), Value::String(prefix));
                }
                if let Some(domain) = get_opt_str(p, "domain") {
                    body.insert("domain".into(), Value::String(domain));
                }
                self.client.request_json(
                    Method::POST,
                    "/api/aliases",
                    &[],
                    Some(&Value::Object(body)),
                )
            }
            "createAliasesBatch" => {
                let count = get_i64(p, "count").unwrap_or(1);
                if !(1..=500).contains(&count) {
                    return Err(Error::Invalid("批量数量需在 1-500 之间".into()));
                }
                let mut body = Map::new();
                body.insert("count".into(), json!(count));
                body.insert("prefix".into(), Value::String(get_str(p, "prefix")));
                if let Some(domain) = get_opt_str(p, "domain") {
                    body.insert("domain".into(), Value::String(domain));
                }
                self.client.request_json(
                    Method::POST,
                    "/api/aliases/batch",
                    &[],
                    Some(&Value::Object(body)),
                )
            }
            "updateAlias" => {
                let id = require_id(p)?;
                let mut body = Map::new();
                if p.get("active").is_some() {
                    body.insert("active".into(), Value::Bool(get_bool(p, "active")));
                }
                if let Some(targets) = p.get("targets") {
                    body.insert("targets".into(), targets.clone());
                }
                self.client.request_json(
                    Method::PUT,
                    &format!("/api/aliases/{id}"),
                    &[],
                    Some(&Value::Object(body)),
                )
            }
            "deleteAlias" => {
                let id = require_id(p)?;
                self.client
                    .request_json(Method::DELETE, &format!("/api/aliases/{id}"), &[], None)
            }
            "availableDomains" => {
                self.client
                    .request_json(Method::GET, "/api/domains/available", &[], None)
            }

            // ---------- 设置 ----------
            "settings" => self
                .client
                .request_json(Method::GET, "/api/settings", &[], None),
            "setForward" => {
                let body = json!({
                    "target": get_str(p, "target"),
                    "keepCopy": get_bool(p, "keepCopy")
                });
                self.client
                    .request_json(Method::POST, "/api/settings/forward", &[], Some(&body))
            }
            "setAutoresponder" => {
                let body = json!({
                    "subject": get_str(p, "subject"),
                    "body": get_str(p, "body"),
                    "active": get_bool(p, "active")
                });
                self.client.request_json(
                    Method::POST,
                    "/api/settings/autoresponder",
                    &[],
                    Some(&body),
                )
            }
            "setProfile" => {
                let body = json!({ "displayName": get_str(p, "displayName") });
                self.client
                    .request_json(Method::POST, "/api/settings/profile", &[], Some(&body))
            }
            "clientConfig" => {
                self.client
                    .request_json(Method::GET, "/api/client-config", &[], None)
            }

            // ---------- 管理后台（仅管理员账号可用） ----------
            "adminStats" => self
                .client
                .request_json(Method::GET, "/api/admin/stats", &[], None),
            "adminDomains" => {
                self.client
                    .request_json(Method::GET, "/api/admin/domains", &[], None)
            }
            "adminUsers" => self
                .client
                .request_json(Method::GET, "/api/admin/users", &[], None),
            "adminAliases" => {
                self.client
                    .request_json(Method::GET, "/api/admin/aliases", &[], None)
            }
            "adminOutbox" => self
                .client
                .request_json(Method::GET, "/api/admin/outbox", &[], None),
            "adminAudit" => self
                .client
                .request_json(Method::GET, "/api/admin/audit", &[], None),
            "adminConfig" => self
                .client
                .request_json(Method::GET, "/api/admin/config", &[], None),
            "adminRetryOutbox" => {
                let id = require_id(p)?;
                self.client.request_json(
                    Method::POST,
                    &format!("/api/admin/outbox/{id}/retry"),
                    &[],
                    None,
                )
            }
            "adminDeleteOutbox" => {
                let id = require_id(p)?;
                self.client.request_json(
                    Method::DELETE,
                    &format!("/api/admin/outbox/{id}"),
                    &[],
                    None,
                )
            }

            other => Err(Error::Invalid(format!("未知方法: {other}"))),
        }
    }
}

fn require_id(p: &Value) -> Result<i64> {
    get_i64(p, "id").ok_or_else(|| Error::Invalid("缺少有效的 id".into()))
}

fn folder_labels() -> Value {
    let mut map = Map::new();
    for (key, label) in FOLDERS {
        map.insert(key.to_string(), Value::String(label.to_string()));
    }
    Value::Object(map)
}

/// 把 App 的写信输入规范化成 /api/send 期望的形状：
/// - 收件人数组 -> 逗号分隔字符串（服务端按 [,;\s]+ 切分）
/// - 附件支持本地路径（Rust 读文件并 base64）或直接给 base64（校验后重新编码）
fn build_send_body(p: &Value) -> Result<Value> {
    let to = get_str_list(p, "to");
    let cc = get_str_list(p, "cc");
    let bcc = get_str_list(p, "bcc");
    if to.is_empty() && cc.is_empty() && bcc.is_empty() {
        return Err(Error::Invalid("至少需要一个收件人".into()));
    }

    let mut attachments: Vec<Value> = Vec::new();
    if let Some(Value::Array(items)) = p.get("attachments") {
        for item in items {
            if !item.is_object() {
                continue;
            }
            let filename = get_str(item, "filename");
            if filename.is_empty() {
                return Err(Error::Invalid("附件缺少 filename".into()));
            }
            let mime =
                get_opt_str(item, "mime").unwrap_or_else(|| "application/octet-stream".to_string());

            let encoded = if let Some(path) = get_opt_str(item, "path") {
                let bytes = std::fs::read(&path)
                    .map_err(|err| Error::Invalid(format!("读取附件 {path} 失败: {err}")))?;
                B64.encode(&bytes)
            } else {
                let raw = get_str(item, "content");
                if raw.is_empty() {
                    continue;
                }
                B64.encode(&B64.decode(raw.trim())?)
            };

            let mut obj = Map::new();
            obj.insert("filename".into(), Value::String(filename));
            obj.insert("mime".into(), Value::String(mime));
            obj.insert("content".into(), Value::String(encoded));
            obj.insert("inline".into(), Value::Bool(get_bool(item, "inline")));
            if let Some(cid) = get_opt_str(item, "contentId") {
                obj.insert("contentId".into(), Value::String(cid));
            }
            attachments.push(Value::Object(obj));
        }
    }

    let mut body = Map::new();
    body.insert("to".into(), Value::String(to.join(", ")));
    body.insert("cc".into(), Value::String(cc.join(", ")));
    body.insert("bcc".into(), Value::String(bcc.join(", ")));
    body.insert("subject".into(), Value::String(get_str(p, "subject")));
    body.insert("text".into(), Value::String(get_str(p, "text")));
    body.insert("html".into(), Value::String(get_str(p, "html")));
    body.insert("attachments".into(), Value::Array(attachments));
    for key in ["from", "inReplyTo", "references"] {
        if let Some(value) = get_opt_str(p, key) {
            body.insert(key.into(), Value::String(value));
        }
    }
    Ok(Value::Object(body))
}

/// JNI / CLI 共用的返回包：{"ok":true,"data":...} 或 {"ok":false,"error":{kind,message}}
pub fn envelope(result: Result<Value>) -> String {
    match result {
        Ok(data) => json!({ "ok": true, "data": data }).to_string(),
        Err(err) => json!({
            "ok": false,
            "error": { "kind": error_kind(&err), "message": err.to_string() }
        })
        .to_string(),
    }
}

fn error_kind(err: &Error) -> &'static str {
    match err {
        Error::Unauthorized => "unauthorized",
        Error::Api { .. } => "api",
        Error::Http(_) => "network",
        Error::NotInitialized => "not_initialized",
        Error::Invalid(_) => "invalid",
        Error::Json(_) | Error::Io(_) | Error::Base64(_) | Error::Other(_) => "other",
    }
}

static STATE: OnceLock<Mutex<Option<Api>>> = OnceLock::new();

fn state() -> &'static Mutex<Option<Api>> {
    STATE.get_or_init(|| Mutex::new(None))
}

/// 全局单例入口。method + JSON 字符串进，JSON 字符串出，JNI 只有一个导出函数。
pub fn global_call(method: &str, payload_json: &str) -> String {
    let payload: Value =
        serde_json::from_str(payload_json).unwrap_or_else(|_| Value::Object(Map::new()));

    let result: Result<Value> = (|| {
        let mut guard = state()
            .lock()
            .map_err(|_| Error::Other("全局状态锁已失效".into()))?;

        if method == "init" {
            let cfg: InitConfig = serde_json::from_value(payload.clone())?;
            *guard = Some(Api::new(cfg)?);
            let api = guard.as_ref().expect("刚初始化");
            return Ok(json!({
                "initialized": true,
                "baseUrl": api.base_url(),
                "loggedIn": api.is_logged_in()
            }));
        }

        if method == "ping" || method == "version" {
            let (base_url, logged_in) = match guard.as_ref() {
                Some(api) => (api.base_url().to_string(), api.is_logged_in()),
                None => (String::new(), false),
            };
            return Ok(json!({
                "version": env!("CARGO_PKG_VERSION"),
                "initialized": guard.is_some(),
                "baseUrl": base_url,
                "loggedIn": logged_in,
                "folderLabels": folder_labels()
            }));
        }

        if method == "folderLabels" {
            return Ok(folder_labels());
        }

        let api = guard.as_mut().ok_or(Error::NotInitialized)?;
        api.dispatch(method, &payload)
    })();

    envelope(result)
}
