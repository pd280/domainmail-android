/// 极小的 URL / 字符串工具，避免为此引入额外依赖。

pub fn percent_decode(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let h = hex(bytes[i + 1]);
            let l = hex(bytes[i + 2]);
            if let (Some(h), Some(l)) = (h, l) {
                out.push(h * 16 + l);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn hex(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

/// 百分号编码，仅保留 unreserved 字符。
pub fn urlencode(input: &str) -> String {
    let mut out = String::with_capacity(input.len() + 8);
    for b in input.as_bytes() {
        match *b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(*b as char)
            }
            _ => out.push_str(&format!("%{:02X}", b)),
        }
    }
    out
}

/// 从 Set-Cookie 头里提取 dm_session 的值（服务端对 token 做过 encodeURIComponent）。
pub fn parse_dm_session_value(set_cookie: &str) -> Option<String> {
    for part in set_cookie.split(';') {
        let part = part.trim();
        if let Some(raw) = part.strip_prefix("dm_session=") {
            if raw.is_empty() {
                return None;
            }
            return Some(percent_decode(raw));
        }
    }
    None
}

/// 从 Content-Disposition 里取文件名，兼容 filename*=UTF-8''... 与普通 filename="..."
pub fn filename_from_disposition(disposition: &str) -> Option<String> {
    for part in disposition.split(';') {
        let p = part.trim();
        if let Some(rest) = p.strip_prefix("filename*=") {
            let rest = rest.trim_matches('"');
            // RFC 5987: charset'lang'value
            if let Some(pos) = rest.rfind("''") {
                let decoded = percent_decode(&rest[pos + 2..]);
                if !decoded.is_empty() {
                    return Some(decoded);
                }
            }
            let decoded = percent_decode(rest);
            if !decoded.is_empty() {
                return Some(decoded);
            }
        } else if let Some(rest) = p.strip_prefix("filename=") {
            let name = rest.trim().trim_matches('"');
            if !name.is_empty() {
                return Some(name.to_string());
            }
        }
    }
    None
}

/// 去掉路径分隔符等危险字符，防止下载时写出目录外。
/// Content-Disposition 是服务端给的，可能夹带路径穿越，所以先只取最后一段。
pub fn safe_filename(name: &str) -> String {
    let base = name
        .rsplit(|c| c == '/' || c == '\\')
        .next()
        .unwrap_or(name);
    let cleaned: String = base
        .chars()
        .map(|c| match c {
            ':' | '*' | '?' | '"' | '<' | '>' | '|' | '\0' => '_',
            c if c.is_control() => '_',
            c => c,
        })
        .collect();
    let trimmed = cleaned.trim().trim_matches(|c| c == '.' || c == '_');
    if trimmed.is_empty() {
        "download.bin".into()
    } else {
        trimmed.into()
    }
}

pub fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        format!("{}…", s.chars().take(max).collect::<String>())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_rfc5987_filename() {
        let d = "attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A.txt";
        assert_eq!(filename_from_disposition(d).as_deref(), Some("报告.txt"));
    }

    #[test]
    fn falls_back_to_plain_filename() {
        assert_eq!(
            filename_from_disposition("attachment; filename=\"a.txt\"").as_deref(),
            Some("a.txt")
        );
    }

    #[test]
    fn encodes_path_segments() {
        assert_eq!(urlencode("INBOX & x/y"), "INBOX%20%26%20x%2Fy");
    }

    #[test]
    fn strips_unsafe_filename_chars() {
        assert_eq!(safe_filename("../../etc/passwd"), "passwd");
        assert_eq!(safe_filename("..\\..\\windows\\system32\\x.dll"), "x.dll");
        assert_eq!(safe_filename("报告.txt"), "报告.txt");
        assert_eq!(safe_filename("..."), "download.bin");
    }
}
