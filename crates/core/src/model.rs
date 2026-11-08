use serde::Deserialize;
use serde_json::Value;

/// 服务端 SYSTEM_FOLDERS（src/store.js）：['INBOX','Sent','Drafts','Trash','Junk']
/// 中文名由 Rust 侧统一给出，Android UI 直接取，避免两端各维护一份。
pub const FOLDERS: [(&str, &str); 5] = [
    ("INBOX", "收件箱"),
    ("Sent", "已发送"),
    ("Drafts", "草稿箱"),
    ("Trash", "回收站"),
    ("Junk", "垃圾邮件"),
];

#[derive(Debug, Clone, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct InitConfig {
    #[serde(alias = "base_url")]
    pub base_url: String,
    #[serde(alias = "data_dir")]
    pub data_dir: Option<String>,
    /// 自建服务器常用自签证书，打开后跳过证书校验（仅影响 TLS，不影响业务）。
    #[serde(alias = "allow_insecure_tls")]
    pub allow_insecure_tls: bool,
    #[serde(alias = "timeout_seconds")]
    pub timeout_seconds: Option<i64>,
}

impl Default for InitConfig {
    fn default() -> Self {
        Self {
            base_url: String::new(),
            data_dir: None,
            allow_insecure_tls: false,
            timeout_seconds: Some(20),
        }
    }
}

/// GET /api/messages 的查询参数
#[derive(Debug, Clone)]
pub struct MessageQuery {
    pub folder: String,
    pub limit: i64,
    pub offset: i64,
    pub q: String,
    pub unread_only: bool,
    pub mailbox: Option<String>,
}

impl MessageQuery {
    pub fn from_value(v: &Value) -> Self {
        let folder = get_str(v, "folder");
        Self {
            folder: if folder.is_empty() {
                "INBOX".into()
            } else {
                folder
            },
            limit: get_i64(v, "limit").unwrap_or(30).clamp(1, 200),
            offset: get_i64(v, "offset").unwrap_or(0).max(0),
            q: get_str(v, "q"),
            unread_only: get_bool(v, "unread") || get_bool(v, "unreadOnly"),
            mailbox: get_opt_str(v, "mailbox"),
        }
    }
}

// ---- 统一的宽松取值：服务端字段可能是数字/字符串/null，不能因为一个类型就整包失败 ----

pub fn get_str(v: &Value, key: &str) -> String {
    match v.get(key) {
        Some(Value::String(s)) => s.clone(),
        Some(Value::Number(n)) => n.to_string(),
        Some(Value::Bool(b)) => b.to_string(),
        _ => String::new(),
    }
}

pub fn get_opt_str(v: &Value, key: &str) -> Option<String> {
    let s = get_str(v, key);
    if s.is_empty() {
        None
    } else {
        Some(s)
    }
}

pub fn get_i64(v: &Value, key: &str) -> Option<i64> {
    match v.get(key) {
        Some(Value::Number(n)) => n.as_i64().or_else(|| n.as_f64().map(|f| f as i64)),
        Some(Value::String(s)) => s.parse::<i64>().ok(),
        _ => None,
    }
}

pub fn get_bool(v: &Value, key: &str) -> bool {
    match v.get(key) {
        Some(Value::Bool(b)) => *b,
        Some(Value::Number(n)) => n.as_i64() == Some(1),
        Some(Value::String(s)) => s == "1" || s.eq_ignore_ascii_case("true"),
        _ => false,
    }
}

/// 收件人既可以是 ["a@b.com","c@d.com"]，也可以是 "a@b.com, c@d.com"
pub fn get_str_list(v: &Value, key: &str) -> Vec<String> {
    match v.get(key) {
        Some(Value::Array(items)) => items
            .iter()
            .filter_map(|x| x.as_str().map(str::trim))
            .filter(|x| !x.is_empty())
            .map(str::to_string)
            .collect(),
        Some(Value::String(text)) => text
            .split(|c: char| c == ',' || c == ';' || c.is_whitespace())
            .map(str::trim)
            .filter(|x| !x.is_empty())
            .map(str::to_string)
            .collect(),
        _ => Vec::new(),
    }
}
