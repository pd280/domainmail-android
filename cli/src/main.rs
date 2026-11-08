//! 桌面验证工具：用与 Android 完全相同的 Rust 核心连真实 DomainMail 服务器。
//! 没有 Android SDK 也能在本机把接口对接跑通，排障时先看这里的输出。

use domainmail_core::global_call;
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

struct Cli {
    pos: Vec<String>,
    kv: HashMap<String, String>,
    flags: HashSet<String>,
}

fn parse(args: &[String], booleans: &[&str]) -> Cli {
    let mut pos = Vec::new();
    let mut kv = HashMap::new();
    let mut flags = HashSet::new();
    let mut i = 0;
    while i < args.len() {
        let arg = &args[i];
        if let Some(key) = arg.strip_prefix("--") {
            if booleans.contains(&key) {
                flags.insert(key.to_string());
                i += 1;
                continue;
            }
            if i + 1 < args.len() && !args[i + 1].starts_with("--") {
                kv.insert(key.to_string(), args[i + 1].clone());
                i += 2;
            } else {
                kv.insert(key.to_string(), String::new());
                i += 1;
            }
        } else {
            pos.push(arg.clone());
            i += 1;
        }
    }
    Cli { pos, kv, flags }
}

fn main() {
    let raw: Vec<String> = std::env::args().skip(1).collect();
    if raw.is_empty() || raw[0] == "--help" || raw[0] == "-h" {
        usage();
        return;
    }

    let mut url = std::env::var("DOMAINMAIL_URL").unwrap_or_default();
    let mut insecure = false;
    let mut data_dir: Option<PathBuf> = None;
    let mut cmd_start = raw.len();
    let mut i = 0;
    while i < raw.len() {
        match raw[i].as_str() {
            "--url" | "-u" => {
                i += 1;
                url = raw.get(i).cloned().unwrap_or_default();
            }
            "--data-dir" => {
                i += 1;
                data_dir = Some(PathBuf::from(raw.get(i).cloned().unwrap_or_default()));
            }
            "--insecure" | "-k" => insecure = true,
            _ => {
                cmd_start = i;
                break;
            }
        }
        i += 1;
    }
    if cmd_start >= raw.len() {
        usage();
        return;
    }
    if url.trim().is_empty() {
        eprintln!("缺少服务器地址：用 --url https://mail.example.com:8080 或设置 DOMAINMAIL_URL");
        std::process::exit(2);
    }

    let dir = data_dir.unwrap_or_else(default_data_dir);
    let init = json!({
        "baseUrl": url.trim(),
        "dataDir": dir.to_string_lossy(),
        "allowInsecureTls": insecure,
        "timeoutSeconds": 20
    });
    let init_result: Value = serde_json::from_str(&global_call("init", &init.to_string())).unwrap();
    if init_result["ok"] != true {
        fail(&init_result);
    }

    let args = &raw[cmd_start..];
    let command = args[0].as_str();
    let cli = parse(&args[1..], &["unread", "keepCopy", "active", "inline"]);

    let (method, payload): (&str, Value) = match command {
        "ping" => ("ping", json!({})),
        "login" => {
            let email = cli
                .pos
                .first()
                .cloned()
                .or_else(|| cli.kv.get("email").cloned())
                .unwrap_or_else(|| {
                    die("用法: login <email> <password>（也可用 DOMAINMAIL_PASSWORD）")
                });
            let password = cli
                .pos
                .get(1)
                .cloned()
                .or_else(|| std::env::var("DOMAINMAIL_PASSWORD").ok())
                .unwrap_or_else(|| die("缺少密码"));
            ("login", json!({ "email": email, "password": password }))
        }
        "logout" => ("logout", json!({})),
        "me" => ("me", json!({})),
        "folders" => ("folders", json!({ "mailbox": opt(&cli, "mailbox") })),
        "messages" => (
            "messages",
            json!({
                "folder": val(&cli, "folder", "INBOX"),
                "limit": val(&cli, "limit", "30").parse::<i64>().unwrap_or(30),
                "offset": val(&cli, "offset", "0").parse::<i64>().unwrap_or(0),
                "q": val(&cli, "q", ""),
                "unread": has(&cli, "unread"),
                "mailbox": opt(&cli, "mailbox")
            }),
        ),
        "message" => ("message", json!({ "id": need(&cli, 0, "id") })),
        "raw" => ("rawMessage", json!({ "id": need(&cli, 0, "id") })),
        "attachment" => (
            "attachment",
            json!({ "id": need(&cli, 0, "id"), "filename": opt(&cli, "filename") }),
        ),
        "send" => {
            let to = val(&cli, "to", "");
            if to.is_empty() {
                die("用法: send --to a@b.com --subject '标题' --text '正文' [--attach /path]");
            }
            let attachments = match opt(&cli, "attach") {
                Some(path) => {
                    let p = PathBuf::from(&path);
                    let filename = p
                        .file_name()
                        .and_then(|s| s.to_str())
                        .unwrap_or("attachment")
                        .to_string();
                    vec![json!({
                        "filename": filename,
                        "mime": guess_mime(&p),
                        "path": path
                    })]
                }
                None => Vec::new(),
            };
            (
                "send",
                json!({
                    "to": to,
                    "cc": val(&cli, "cc", ""),
                    "bcc": val(&cli, "bcc", ""),
                    "from": opt(&cli, "from"),
                    "subject": val(&cli, "subject", ""),
                    "text": val(&cli, "text", ""),
                    "html": val(&cli, "html", ""),
                    "attachments": attachments
                }),
            )
        }
        "mailboxes" => ("mailboxes", json!({ "current": opt(&cli, "current") })),
        "aliases" => ("aliases", json!({})),
        "alias" => (
            "createAlias",
            json!({ "prefix": need(&cli, 0, "prefix"), "domain": opt(&cli, "domain") }),
        ),
        "alias-batch" => (
            "createAliasesBatch",
            json!({
                "count": val(&cli, "count", "1").parse::<i64>().unwrap_or(1),
                "prefix": val(&cli, "prefix", ""),
                "domain": opt(&cli, "domain")
            }),
        ),
        "delete-alias" => ("deleteAlias", json!({ "id": need(&cli, 0, "id") })),
        "mark-read" => ("markRead", json!({ "id": need(&cli, 0, "id") })),
        "mark-unread" => ("markUnread", json!({ "id": need(&cli, 0, "id") })),
        "star" => ("star", json!({ "id": need(&cli, 0, "id") })),
        "unstar" => ("unstar", json!({ "id": need(&cli, 0, "id") })),
        "move" => (
            "moveMessage",
            json!({ "id": need(&cli, 0, "id"), "folder": need(&cli, 1, "folder") }),
        ),
        "delete" => ("deleteMessage", json!({ "id": need(&cli, 0, "id") })),
        "empty" => ("emptyFolder", json!({ "folder": need(&cli, 0, "folder") })),
        "passwd" => (
            "changePassword",
            json!({
                "oldPassword": need(&cli, 0, "oldPassword"),
                "newPassword": need(&cli, 1, "newPassword")
            }),
        ),
        "settings" => ("settings", json!({})),
        "client-config" => ("clientConfig", json!({})),
        "forward" => (
            "setForward",
            json!({ "target": val(&cli, "target", ""), "keepCopy": has(&cli, "keepCopy") }),
        ),
        "admin-stats" => ("adminStats", json!({})),
        "admin-domains" => ("adminDomains", json!({})),
        "admin-users" => ("adminUsers", json!({})),
        "admin-outbox" => ("adminOutbox", json!({})),
        "admin-audit" => ("adminAudit", json!({})),
        "admin-config" => ("adminConfig", json!({})),
        other => die(&format!("未知命令: {other}")),
    };

    let output = global_call(method, &payload.to_string());
    let value: Value = serde_json::from_str(&output).unwrap_or(Value::Null);
    if value["ok"] == true {
        println!("{}", serde_json::to_string_pretty(&value["data"]).unwrap());
    } else {
        fail(&value);
    }
}

fn fail(value: &Value) -> ! {
    eprintln!(
        "错误 [{}]: {}",
        value["error"]["kind"].as_str().unwrap_or("unknown"),
        value["error"]["message"].as_str().unwrap_or("未知错误")
    );
    std::process::exit(1);
}

fn die(message: &str) -> ! {
    eprintln!("{message}");
    std::process::exit(2);
}

fn need(cli: &Cli, index: usize, name: &str) -> String {
    cli.pos
        .get(index)
        .cloned()
        .unwrap_or_else(|| die(&format!("缺少参数: {name}")))
}

fn opt(cli: &Cli, key: &str) -> Option<String> {
    cli.kv.get(key).filter(|v| !v.is_empty()).cloned()
}

fn val(cli: &Cli, key: &str, default: &str) -> String {
    opt(cli, key).unwrap_or_else(|| default.to_string())
}

fn has(cli: &Cli, key: &str) -> bool {
    cli.flags.contains(key)
}

fn default_data_dir() -> PathBuf {
    let home = std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .unwrap_or_else(|_| ".".to_string());
    PathBuf::from(home).join(".domainmail-cli")
}

fn guess_mime(path: &Path) -> &'static str {
    match path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_ascii_lowercase()
        .as_str()
    {
        "txt" | "log" | "md" | "csv" => "text/plain",
        "html" | "htm" => "text/html",
        "json" => "application/json",
        "pdf" => "application/pdf",
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "gif" => "image/gif",
        "zip" => "application/zip",
        _ => "application/octet-stream",
    }
}

fn usage() {
    println!(
        r#"DomainMail Rust CLI —— 与 Android App 共用同一套核心

用法:
  domainmail-cli [--url URL] [--insecure] [--data-dir DIR] <命令> [参数]

全局参数:
  --url, -u       服务器地址，如 https://mail.example.com:8080（或环境变量 DOMAINMAIL_URL）
  --insecure, -k  跳过 TLS 证书校验（自签证书）
  --data-dir      会话存放目录（默认 ~/.domainmail-cli）

常用命令:
  login <email> <password>          登录（密码也可放 DOMAINMAIL_PASSWORD 环境变量）
  me                                当前用户
  folders [--mailbox ADDR]          文件夹与未读数
  messages [--folder INBOX] [--limit 30] [--offset 0] [--q 关键词] [--unread] [--mailbox ADDR]
  message <id>                      邮件详情
  raw <id>                          下载 .eml 原文，返回本地路径
  attachment <id> [--filename 名称]  下载附件，返回本地路径
  send --to a@b.com --subject 标题 --text 正文 [--attach /path] [--from 别名@域名]
  mailboxes                         可切换的邮箱身份
  aliases / alias <前缀> [--domain 域名] / delete-alias <id>
  mark-read <id> / star <id> / move <id> <folder> / delete <id>
  settings / client-config / passwd <旧> <新>
  admin-stats / admin-domains / admin-users / admin-outbox / admin-audit / admin-config

示例:
  export DOMAINMAIL_URL=https://mail.example.com:8080
  cargo run -p domainmail-cli -- login you@example.com 'your-password'
  cargo run -p domainmail-cli -- --insecure messages --folder INBOX --limit 20
  cargo run -p domainmail-cli -- send --to bob@example.com --subject 你好 --text 正文 --attach ./a.txt
"#
    );
}
