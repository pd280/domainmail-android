package com.ifwqu.domainmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DmException(val kind: String, message: String) : Exception(message)

/**
 * 与 Rust 核心的唯一通道。整个 App 只有一个 native 方法：
 * nativeCall(method, payloadJson) -> {"ok":true,"data":...} / {"ok":false,"error":{kind,message}}
 * 新增后端能力只改 Rust 的 dispatch，这里零改动。
 */
object RustBridge {
    init {
        System.loadLibrary("domainmail_core")
    }

    // @JvmStatic 让 JVM 看到的是 static native 方法，JNI 第二个参数是 jclass，
    // 与 Rust 侧 Java_com_ifwqu_domainmail_RustBridge_nativeCall(JNIEnv, JClass, ...) 对齐。
    @JvmStatic
    external fun nativeCall(method: String, payload: String): String

    private fun unwrap(raw: String): JSONObject {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw DmException("other", "Rust 返回了非法 JSON: ${e.message}")
        }
        if (root.optBoolean("ok", false)) {
            return root.optJSONObject("data") ?: JSONObject()
        }
        val error = root.optJSONObject("error") ?: JSONObject()
        throw DmException(
            error.optString("kind", "unknown"),
            error.optString("message", "未知错误")
        )
    }

    /** 所有网络调用都在 IO 线程执行，主线程只做渲染。 */
    suspend fun call(method: String, payload: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) { unwrap(nativeCall(method, payload.toString())) }

    fun callBlocking(method: String, payload: JSONObject = JSONObject()): JSONObject =
        unwrap(nativeCall(method, payload.toString()))
}
