//! domainmail-core：DomainMail 自建邮箱的 Rust 客户端核心。
//!
//! - `Api` / `global_call`：REST 门面，Android JNI 与桌面 CLI 共用
//! - `client`：HTTP + dm_session Cookie 会话 + 错误映射
//! - `session`：会话持久化（只存 token，不存密码）
//! - `ffi`：`cfg(target_os = "android")` 下才编译的 JNI 导出

pub mod api;
pub mod client;
pub mod error;
pub mod model;
pub mod session;
pub mod util;

#[cfg(target_os = "android")]
pub mod ffi;

pub use api::{global_call, Api};
pub use error::{Error, Result};
