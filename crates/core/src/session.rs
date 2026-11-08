use serde_json::json;
use std::path::{Path, PathBuf};

/// dm_session Cookie 的持久化。只存 token，绝不落盘密码。
#[derive(Default)]
pub struct Session {
    token: Option<String>,
    store: Option<PathBuf>,
}

impl Session {
    pub fn token(&self) -> Option<&str> {
        self.token.as_deref()
    }

    pub fn store_path(&self) -> Option<&Path> {
        self.store.as_deref()
    }

    /// 绑定会话文件并尝试恢复上次登录态（App 冷启动免登录）。
    pub fn attach(&mut self, path: &Path) {
        self.store = Some(path.to_path_buf());
        self.load();
    }

    fn load(&mut self) {
        let Some(path) = self.store.as_ref() else {
            return;
        };
        let Ok(text) = std::fs::read_to_string(path) else {
            return;
        };
        if let Ok(value) = serde_json::from_str::<serde_json::Value>(&text) {
            if let Some(token) = value.get("token").and_then(|v| v.as_str()) {
                if !token.is_empty() {
                    self.token = Some(token.to_string());
                }
            }
        }
    }

    pub fn set_token(&mut self, token: Option<String>) {
        self.token = token;
        self.persist();
    }

    fn persist(&self) {
        let Some(path) = self.store.as_ref() else {
            return;
        };
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let body = json!({ "token": self.token.clone() }).to_string();
        if let Err(err) = std::fs::write(path, body) {
            log::warn!("会话持久化失败: {err}");
            return;
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600));
        }
    }
}
