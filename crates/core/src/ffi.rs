#![allow(non_snake_case)]

//! JNI 边界：整个 App 只导出一个函数 `nativeCall(method, payloadJson) -> json`。
//! 新增 REST 能力只改 Rust 的 dispatch，Kotlin 侧零改动。

use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring, JNI_VERSION_1_6};
use jni::{JNIEnv, JavaVM};
use std::ffi::c_void;
use std::panic::{catch_unwind, AssertUnwindSafe};

#[no_mangle]
pub extern "system" fn JNI_OnLoad(_vm: JavaVM, _reserved: *mut c_void) -> jint {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("domainmail-rs"),
    );
    JNI_VERSION_1_6
}

#[no_mangle]
pub extern "system" fn Java_com_ifwqu_domainmail_RustBridge_nativeCall(
    mut env: JNIEnv,
    _class: JClass,
    method: JString,
    payload: JString,
) -> jstring {
    let method: String = env.get_string(&method).map(Into::into).unwrap_or_default();
    let payload: String = env
        .get_string(&payload)
        .map(Into::into)
        .unwrap_or_else(|_| "{}".to_string());

    // panic 绝不能越过 FFI 边界炸掉整个进程，统一转成错误 JSON。
    let output = catch_unwind(AssertUnwindSafe(|| {
        crate::api::global_call(&method, &payload)
    }))
    .unwrap_or_else(|_| {
        r#"{"ok":false,"error":{"kind":"panic","message":"Rust 内部异常"}}"#.to_string()
    });

    match env.new_string(output) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
