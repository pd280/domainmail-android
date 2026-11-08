package com.ifwqu.domainmail

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- org.json 宽松取值：服务端字段可能是数字 / 字符串 / null ----

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")
fun JSONObject.long(key: String): Long = if (isNull(key)) 0L else optLong(key, 0L)
fun JSONObject.int(key: String): Int = if (isNull(key)) 0 else optInt(key, 0)
fun JSONObject.bool(key: String): Boolean = optBoolean(key, false)
fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
fun JSONObject.dbl(key: String): Double = if (isNull(key)) 0.0 else optDouble(key, 0.0)

fun JSONArray?.objects(): List<JSONObject> =
    this?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } } ?: emptyList()

fun JSONArray?.strings(): List<String> =
    this?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it) } } ?: emptyList()

fun formatDate(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

fun formatDateFull(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))

val FOLDER_LABELS = mapOf(
    "INBOX" to "收件箱",
    "Sent" to "已发送",
    "Drafts" to "草稿箱",
    "Trash" to "回收站",
    "Junk" to "垃圾邮件"
)

fun folderLabel(name: String): String = FOLDER_LABELS[name] ?: name

data class PickedFile(val path: String, val name: String, val mime: String)

data class User(
    val id: Long,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean,
    val quotaMb: Long
) {
    companion object {
        fun from(o: JSONObject) = User(
            o.long("id"), o.str("email"), o.str("displayName"),
            o.bool("isAdmin"), o.long("quotaMb")
        )
    }
}

data class Folder(val name: String, val total: Int, val unread: Int) {
    companion object {
        fun from(o: JSONObject) = Folder(o.str("name"), o.int("total"), o.int("unread"))
    }
}

data class MessageSummary(
    val id: Long,
    val uid: Long,
    val folder: String,
    val mailbox: String,
    val subject: String,
    val from: String,
    val fromName: String,
    val to: String,
    val date: Long,
    val size: Long,
    val snippet: String,
    val hasAttachment: Boolean,
    val seen: Boolean,
    val flagged: Boolean,
    val spamScore: Double
) {
    companion object {
        fun from(o: JSONObject) = MessageSummary(
            o.long("id"), o.long("uid"), o.str("folder"), o.str("mailbox"),
            o.str("subject").ifBlank { "(无主题)" }, o.str("from"), o.str("fromName"),
            o.str("to"), o.long("date"), o.long("size"), o.str("snippet"),
            o.bool("hasAttachment"), o.bool("seen"), o.bool("flagged"), o.dbl("spamScore")
        )
    }
}

data class AttachmentInfo(
    val id: Long,
    val filename: String,
    val mime: String,
    val size: Long,
    val inline: Boolean
) {
    companion object {
        fun from(o: JSONObject) = AttachmentInfo(
            o.long("id"), o.str("filename"), o.str("mime"), o.long("size"), o.bool("inline")
        )
    }
}

data class MessageDetail(
    val id: Long,
    val subject: String,
    val from: String,
    val fromName: String,
    val to: String,
    val cc: String,
    val date: Long,
    val text: String,
    val html: String,
    val folder: String,
    val mailbox: String,
    val flagged: Boolean,
    val attachments: List<AttachmentInfo>
) {
    companion object {
        fun from(o: JSONObject): MessageDetail {
            val flags = o.optJSONArray("flags").strings()
            return MessageDetail(
                o.long("id"), o.str("subject").ifBlank { "(无主题)" },
                o.str("from"), o.str("fromName"), o.str("to"), o.str("cc"),
                o.long("date"), o.str("text"), o.str("html"),
                o.str("folder"), o.str("mailbox"),
                flags.any { it.equals("\\Flagged", ignoreCase = true) },
                o.optJSONArray("attachments").objects().map { AttachmentInfo.from(it) }
            )
        }
    }
}

data class Mailbox(
    val address: String,
    val type: String,
    val label: String,
    val unread: Int,
    val total: Int
) {
    companion object {
        fun from(o: JSONObject) = Mailbox(
            o.str("address"), o.str("type"), o.str("label"), o.int("unread"), o.int("total")
        )
    }
}

data class Alias(
    val id: Long,
    val address: String,
    val targets: List<String>,
    val active: Boolean,
    val createdAt: Long
) {
    companion object {
        fun from(o: JSONObject) = Alias(
            o.long("id"), o.str("address"), o.optJSONArray("targets").strings(),
            o.bool("active"), o.long("createdAt")
        )
    }
}
