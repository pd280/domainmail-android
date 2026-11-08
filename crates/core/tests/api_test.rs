//! 端到端契约测试：用 std::net 起一个假的 DomainMail HTTP 服务，
//! 不依赖外网、不依赖 mock crate，验证 Cookie 会话、查询串、发信体、下载与错误映射。

use domainmail_core::api::{envelope, Api};
use domainmail_core::error::Error;
use domainmail_core::model::InitConfig;
use serde_json::{json, Value};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;

static SEQ: AtomicU64 = AtomicU64::new(0);

fn temp_dir(tag: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!(
        "domainmail-test-{tag}-{}-{}",
        std::process::id(),
        SEQ.fetch_add(1, Ordering::Relaxed)
    ));
    let _ = std::fs::remove_dir_all(&path);
    std::fs::create_dir_all(&path).unwrap();
    path
}

fn api(base: &str, data_dir: Option<&PathBuf>) -> Api {
    Api::new(InitConfig {
        base_url: base.to_string(),
        data_dir: data_dir.map(|p| p.to_string_lossy().into_owned()),
        allow_insecure_tls: false,
        timeout_seconds: Some(5),
    })
    .unwrap()
}

#[derive(Clone, Default)]
struct Recorder {
    requests: Arc<Mutex<Vec<String>>>,
}

impl Recorder {
    fn all(&self) -> Vec<String> {
        self.requests.lock().unwrap().clone()
    }

    fn last(&self) -> String {
        self.all().last().cloned().unwrap_or_default()
    }
}

fn spawn_mock() -> (String, Recorder) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap();
    let recorder = Recorder::default();
    let shared = recorder.requests.clone();
    thread::spawn(move || {
        for stream in listener.incoming() {
            let Ok(mut stream) = stream else { continue };
            let sink = shared.clone();
            thread::spawn(move || handle_connection(&mut stream, sink));
        }
    });
    (format!("http://{addr}"), recorder)
}

fn handle_connection(stream: &mut TcpStream, sink: Arc<Mutex<Vec<String>>>) {
    let mut buf: Vec<u8> = Vec::new();
    let mut chunk = [0u8; 2048];
    let header_end = loop {
        match stream.read(&mut chunk) {
            Ok(0) | Err(_) => return,
            Ok(n) => {
                buf.extend_from_slice(&chunk[..n]);
                if let Some(pos) = find_bytes(&buf, b"\r\n\r\n") {
                    break pos + 4;
                }
            }
        }
    };

    let head = String::from_utf8_lossy(&buf[..header_end]).into_owned();
    let content_len = head
        .lines()
        .filter_map(|line| line.split_once(':'))
        .find(|(k, _)| k.eq_ignore_ascii_case("content-length"))
        .and_then(|(_, v)| v.trim().parse::<usize>().ok())
        .unwrap_or(0);

    while buf.len() < header_end + content_len {
        match stream.read(&mut chunk) {
            Ok(0) | Err(_) => break,
            Ok(n) => buf.extend_from_slice(&chunk[..n]),
        }
    }
    let body = String::from_utf8_lossy(&buf[header_end..]).into_owned();
    sink.lock().unwrap().push(format!("{head}{body}"));

    let first = head.lines().next().unwrap_or("GET /");
    let mut parts = first.split_whitespace();
    let method = parts.next().unwrap_or("GET");
    let target = parts.next().unwrap_or("/");
    let (path, query) = target.split_once('?').unwrap_or((target, ""));

    let (status, headers, payload) = route(method, path, query, &head, &body);
    let reason = if status == 200 {
        "OK"
    } else if status == 401 {
        "Unauthorized"
    } else {
        "Not Found"
    };
    let mut response = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Length: {}\r\nConnection: close\r\n",
        payload.len()
    );
    for (key, value) in headers {
        response.push_str(&format!("{key}: {value}\r\n"));
    }
    response.push_str("\r\n");
    let _ = stream.write_all(response.as_bytes());
    let _ = stream.write_all(&payload);
    let _ = stream.flush();
}

fn route(
    method: &str,
    path: &str,
    _query: &str,
    head: &str,
    _body: &str,
) -> (u16, Vec<(String, String)>, Vec<u8>) {
    let mut headers = vec![(
        "Content-Type".into(),
        "application/json; charset=utf-8".into(),
    )];
    let authed = head
        .to_ascii_lowercase()
        .contains("cookie: dm_session=testtoken");

    let (status, payload): (u16, Vec<u8>) = match (method, path) {
        ("POST", "/api/auth/login") => {
            headers.push((
                "Set-Cookie".into(),
                "dm_session=TESTTOKEN; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800".into(),
            ));
            (200, r#"{"ok":true,"user":{"id":1,"email":"you@example.com","displayName":"Me","isAdmin":false,"quotaMb":1024}}"#.as_bytes().to_vec())
        }
        ("GET", "/api/auth/me") if authed => (
            200,
            r#"{"user":{"id":1,"email":"you@example.com","displayName":"Me","isAdmin":false,"quotaMb":1024}}"#.as_bytes().to_vec(),
        ),
        ("GET", "/api/auth/me") => (401, r#"{"error":"请先登录"}"#.as_bytes().to_vec()),
        ("GET", "/api/folders") => (
            200,
            r#"{"folders":[{"name":"INBOX","total":2,"unread":1},{"name":"Sent","total":0,"unread":0}],"total":2,"mailbox":null}"#.as_bytes().to_vec(),
        ),
        ("GET", "/api/messages") => (
            200,
            r#"{"messages":[{"id":9,"uid":3,"folder":"INBOX","mailbox":"you@example.com","subject":"你好","from":"a@example.com","fromName":"A","to":"you@example.com","date":1730000000000,"size":128,"snippet":"摘要","hasAttachment":false,"seen":false,"flagged":false,"spamScore":0},{"id":10,"uid":4,"folder":"INBOX","mailbox":"you@example.com","subject":"第二封","from":"b@example.com","fromName":null,"to":"you@example.com","date":null,"size":64,"snippet":null,"hasAttachment":true,"seen":true,"flagged":true,"spamScore":2}],"total":2}"#.as_bytes().to_vec(),
        ),
        ("GET", "/api/messages/9") => (
            200,
            r#"{"id":9,"subject":"你好","from":"a@example.com","fromName":"A","to":"you@example.com","cc":"","date":1730000000000,"size":128,"text":"正文","html":"<p>正文</p>","messageId":"<x@example.com>","folder":"INBOX","mailbox":"you@example.com","flags":["\\Seen"],"spamScore":0,"spamReason":"","attachments":[]}"#.as_bytes().to_vec(),
        ),
        ("GET", "/api/messages/9/raw") => {
            headers.clear();
            headers.push(("Content-Type".into(), "message/rfc822".into()));
            headers.push(("Content-Disposition".into(), "attachment; filename*=UTF-8''%E5%8E%9F%E4%BB%B6.eml".into()));
            (200, b"From: a@example.com\r\n\r\nRAW".to_vec())
        }
        ("GET", "/api/attachments/3") => {
            headers.clear();
            headers.push(("Content-Type".into(), "text/plain".into()));
            headers.push(("Content-Disposition".into(), "attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A.txt".into()));
            (200, b"ATTACHMENT-BYTES".to_vec())
        }
        ("POST", "/api/send") => (
            200,
            r#"{"ok":true,"messageId":"<sent@example.com>","local":1,"queued":1}"#.as_bytes().to_vec(),
        ),
        ("POST", "/api/messages/9/flags") => (200, r#"{"ok":true}"#.as_bytes().to_vec()),
        ("GET", "/api/mailboxes") => (
            200,
            r#"{"mailboxes":[{"address":"you@example.com","type":"primary","label":"主邮箱","unread":1,"total":2}],"current":"you@example.com"}"#.as_bytes().to_vec(),
        ),
        _ => (404, r#"{"error":"未找到"}"#.as_bytes().to_vec()),
    };
    (status, headers, payload)
}

fn find_bytes(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|w| w == needle)
}

fn request_body(recorded: &str) -> Value {
    let part = recorded.split("\r\n\r\n").nth(1).unwrap_or("{}");
    serde_json::from_str(part).unwrap_or(json!({}))
}

#[test]
fn login_captures_cookie_me_reuses_it_and_session_persists() {
    let (base, recorder) = spawn_mock();
    let dir = temp_dir("session");
    let mut client = api(&base, Some(&dir));

    let login = client
        .dispatch(
            "login",
            &json!({"email": "you@example.com", "password": "pw"}),
        )
        .unwrap();
    assert_eq!(login["user"]["email"], "you@example.com");
    assert!(client.is_logged_in());

    let me = client.dispatch("me", &json!({})).unwrap();
    assert_eq!(me["user"]["email"], "you@example.com");

    let me_request = recorder
        .all()
        .into_iter()
        .find(|r| r.contains("GET /api/auth/me"))
        .unwrap();
    assert!(
        me_request
            .to_ascii_lowercase()
            .contains("cookie: dm_session=testtoken"),
        "me 请求没有带上 dm_session: {me_request}"
    );

    let saved = std::fs::read_to_string(dir.join("session.json")).unwrap();
    assert!(saved.contains("TESTTOKEN"));

    // 新实例（模拟 App 冷启动）应自动恢复会话
    let mut restored = api(&base, Some(&dir));
    assert!(restored.is_logged_in());
    restored.dispatch("me", &json!({})).unwrap();
}

#[test]
fn messages_builds_query_and_passes_through_server_fields() {
    let (base, recorder) = spawn_mock();
    let mut client = api(&base, None);

    let result = client
        .dispatch(
            "messages",
            &json!({
                "folder": "INBOX", "limit": 25, "offset": 5,
                "q": "hello 世界", "unread": true,
                "mailbox": "shop@example.com"
            }),
        )
        .unwrap();

    let list = result["messages"].as_array().unwrap();
    assert_eq!(list.len(), 2);
    assert_eq!(result["total"], 2);
    assert_eq!(list[0]["subject"], "你好");
    assert_eq!(list[0]["date"], 1730000000000i64);
    assert_eq!(list[1]["date"], Value::Null);
    assert_eq!(list[1]["hasAttachment"], true);

    let request = recorder.last();
    assert!(request.starts_with("GET /api/messages?"), "{request}");
    for needle in [
        "folder=INBOX",
        "limit=25",
        "offset=5",
        "unread=1",
        "mailbox=shop%40example.com",
        "%E4%B8%96%E7%95%8C",
    ] {
        assert!(request.contains(needle), "查询串缺少 {needle}: {request}");
    }
}

#[test]
fn send_normalizes_recipients_and_base64_encodes_local_attachment() {
    let (base, recorder) = spawn_mock();
    let mut client = api(&base, None);
    let file = temp_dir("attach").join("a.txt");
    std::fs::write(&file, b"hello-attachment").unwrap();

    let result = client
        .dispatch(
            "send",
            &json!({
                "to": ["a@example.com", "b@example.com"],
                "cc": "c@example.com",
                "subject": "测试",
                "text": "正文",
                "from": "shop@example.com",
                "attachments": [{
                    "filename": "a.txt",
                    "mime": "text/plain",
                    "path": file.to_string_lossy()
                }]
            }),
        )
        .unwrap();
    assert_eq!(result["ok"], true);
    assert_eq!(result["queued"], 1);

    let body = request_body(&recorder.last());
    assert_eq!(body["to"], "a@example.com, b@example.com");
    assert_eq!(body["cc"], "c@example.com");
    assert_eq!(body["from"], "shop@example.com");
    assert_eq!(body["subject"], "测试");
    assert_eq!(
        body["attachments"][0]["content"],
        "aGVsbG8tYXR0YWNobWVudA=="
    );
}

#[test]
fn send_rejects_empty_recipients_before_touching_network() {
    let (base, _) = spawn_mock();
    let mut client = api(&base, None);
    let err = client
        .dispatch("send", &json!({"subject": "没有收件人"}))
        .unwrap_err();
    assert!(matches!(err, Error::Invalid(_)), "{err:?}");
}

#[test]
fn base64_content_is_validated_and_reencoded() {
    let (base, recorder) = spawn_mock();
    let mut client = api(&base, None);
    client
        .dispatch(
            "send",
            &json!({
                "to": "a@example.com",
                "attachments": [{"filename":"x.bin", "content": "aGVsbG8="}]
            }),
        )
        .unwrap();
    let body = request_body(&recorder.last());
    assert_eq!(body["attachments"][0]["content"], "aGVsbG8=");

    let err = client
        .dispatch(
            "send",
            &json!({"to":"a@example.com", "attachments":[{"filename":"x","content":"!!!not-base64!!!"}]}),
        )
        .unwrap_err();
    assert!(matches!(err, Error::Base64(_)) || matches!(err, Error::Invalid(_)));
}

#[test]
fn unauthorized_is_mapped_to_a_structured_envelope() {
    let (base, _) = spawn_mock();
    let mut client = api(&base, None);
    let err = client.dispatch("me", &json!({})).unwrap_err();
    assert!(matches!(err, Error::Unauthorized), "{err:?}");
    assert!(!client.is_logged_in());

    let value: Value = serde_json::from_str(&envelope(Err(err))).unwrap();
    assert_eq!(value["ok"], false);
    assert_eq!(value["error"]["kind"], "unauthorized");
}

#[test]
fn api_error_message_comes_from_server_error_field() {
    let (base, _) = spawn_mock();
    let mut client = api(&base, None);
    let err = client.dispatch("unknownMethod", &json!({})).unwrap_err();
    assert!(matches!(err, Error::Invalid(_)));

    let err = client.dispatch("adminStats", &json!({})).unwrap_err();
    match err {
        Error::Api { status, message } => {
            assert_eq!(status, 404);
            assert_eq!(message, "未找到");
        }
        other => panic!("期望 Api 错误，得到 {other:?}"),
    }
}

#[test]
fn binary_downloads_are_written_by_rust_and_paths_returned() {
    let (base, _) = spawn_mock();
    let dir = temp_dir("download");
    let mut client = api(&base, Some(&dir));

    let att = client.dispatch("attachment", &json!({"id": 3})).unwrap();
    let path = att["path"].as_str().unwrap();
    assert_eq!(std::fs::read(path).unwrap(), b"ATTACHMENT-BYTES");
    assert_eq!(att["filename"], "报告.txt");
    assert_eq!(att["size"], 16);

    let raw = client.dispatch("rawMessage", &json!({"id": 9})).unwrap();
    let raw_path = raw["path"].as_str().unwrap();
    assert!(std::fs::read(raw_path)
        .unwrap()
        .starts_with(b"From: a@example.com"));
    assert_eq!(raw["filename"], "原件.eml");
}

#[test]
fn flag_helpers_use_imap_flag_names() {
    let (base, recorder) = spawn_mock();
    let mut client = api(&base, None);
    client.dispatch("markRead", &json!({"id": 9})).unwrap();
    let body = request_body(&recorder.last());
    assert_eq!(body["add"][0], "\\Seen");

    client.dispatch("unstar", &json!({"id": 9})).unwrap();
    let body = request_body(&recorder.last());
    assert_eq!(body["remove"][0], "\\Flagged");
}

#[test]
fn invalid_base_url_and_unknown_method_fail_cleanly() {
    assert!(Api::new(InitConfig {
        base_url: "mail.example.com".into(),
        ..Default::default()
    })
    .is_err());

    let (base, _) = spawn_mock();
    let mut client = api(&base, None);
    assert!(matches!(
        client.dispatch("nope", &json!({})),
        Err(Error::Invalid(_))
    ));
}
