use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error("网络请求失败: {0}")]
    Http(#[from] reqwest::Error),

    #[error("服务器返回 {status}: {message}")]
    Api { status: u16, message: String },

    #[error("JSON 处理失败: {0}")]
    Json(#[from] serde_json::Error),

    #[error("文件操作失败: {0}")]
    Io(#[from] std::io::Error),

    #[error("尚未初始化，请先调用 init")]
    NotInitialized,

    #[error("会话已失效，请重新登录")]
    Unauthorized,

    #[error("参数错误: {0}")]
    Invalid(String),

    #[error("Base64 解码失败: {0}")]
    Base64(#[from] base64::DecodeError),

    #[error("{0}")]
    Other(String),
}

pub type Result<T> = std::result::Result<T, Error>;
