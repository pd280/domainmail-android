package com.ifwqu.domainmail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed class Screen {
    object Login : Screen()
    object List : Screen()
    data class Detail(val id: Long) : Screen()
    object Compose : Screen()
}

private const val PAGE_SIZE = 30

/** UI 状态 + 所有业务动作。这里只调 RustBridge，不做任何网络/协议处理。 */
class MailState(private val context: Context, private val scope: CoroutineScope) {
    val appContext: Context get() = context.applicationContext

    private val prefs = context.getSharedPreferences("domainmail", Context.MODE_PRIVATE)

    var screen: Screen by mutableStateOf(Screen.Login)
    var tab by mutableStateOf(0)
    var busy by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
    var toast: String? by mutableStateOf(null)

    var user: User? by mutableStateOf(null)
    var savedBaseUrl by mutableStateOf("")

    var folders: List<Folder> by mutableStateOf(emptyList())
    var mailboxes: List<Mailbox> by mutableStateOf(emptyList())
    var currentMailbox: String? by mutableStateOf(null)
    var currentFolder by mutableStateOf("INBOX")
    var messages: List<MessageSummary> by mutableStateOf(emptyList())
    var total by mutableStateOf(0)
    var unreadOnly by mutableStateOf(false)
    var query by mutableStateOf("")
    var showSearch by mutableStateOf(false)
    var detail: MessageDetail? by mutableStateOf(null)

    var aliases: List<Alias> by mutableStateOf(emptyList())
    var aliasDomains: List<String> by mutableStateOf(emptyList())
    var aliasAllowCreate by mutableStateOf(true)
    var settings: JSONObject by mutableStateOf(JSONObject())
    var clientConfig: JSONObject by mutableStateOf(JSONObject())

    var savedFilePath: String? by mutableStateOf(null)
    var savedFileMime: String? by mutableStateOf(null)

    private fun go(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    private suspend fun initClient(base: String, insecure: Boolean) {
        RustBridge.call("init", JSONObject().apply {
            put("baseUrl", base)
            put("dataDir", context.filesDir.absolutePath)
            put("allowInsecureTls", insecure)
            put("timeoutSeconds", 20)
        })
    }

    /** 冷启动：有保存的服务器地址就 init，Rust 侧会恢复 dm_session。 */
    fun start() = go {
        val base = prefs.getString("baseUrl", "") ?: ""
        savedBaseUrl = base
        if (base.isBlank()) {
            screen = Screen.Login
            return@go
        }
        try {
            initClient(base, prefs.getBoolean("insecure", false))
            val ping = RustBridge.call("ping")
            if (ping.bool("loggedIn")) {
                user = User.from(RustBridge.call("me").obj("user"))
                screen = Screen.List
                refreshAll()
            } else {
                screen = Screen.Login
            }
        } catch (e: Exception) {
            screen = Screen.Login
        }
    }

    fun login(base: String, email: String, password: String, insecure: Boolean) = go {
        val normalized = base.trim().trimEnd('/')
        initClient(normalized, insecure)
        prefs.edit()
            .putString("baseUrl", normalized)
            .putBoolean("insecure", insecure)
            .apply()
        savedBaseUrl = normalized
        val result = RustBridge.call(
            "login", JSONObject().put("email", email.trim()).put("password", password)
        )
        user = User.from(result.obj("user"))
        currentMailbox = user?.email
        tab = 0
        screen = Screen.List
        refreshAll()
    }

    fun refresh() = go { refreshAll() }

    private suspend fun refreshAll() {
        loadFolders()
        loadMailboxes()
        loadMessages(0)
    }

    private suspend fun loadFolders() {
        val payload = JSONObject()
        currentMailbox?.let { payload.put("mailbox", it) }
        val result = RustBridge.call("folders", payload)
        folders = result.optJSONArray("folders").objects().map { Folder.from(it) }
    }

    private suspend fun loadMailboxes() {
        val result = RustBridge.call("mailboxes")
        mailboxes = result.optJSONArray("mailboxes").objects().map { Mailbox.from(it) }
        if (currentMailbox.isNullOrBlank()) {
            currentMailbox = result.str("current").ifBlank { user?.email }
        }
    }

    private suspend fun loadMessages(offset: Int) {
        val payload = JSONObject().apply {
            put("folder", currentFolder)
            put("limit", PAGE_SIZE)
            put("offset", offset)
            if (query.isNotBlank()) put("q", query)
            if (unreadOnly) put("unread", true)
            currentMailbox?.let { put("mailbox", it) }
        }
        val result = RustBridge.call("messages", payload)
        val page = result.optJSONArray("messages").objects().map { MessageSummary.from(it) }
        messages = if (offset == 0) page else messages + page
        total = result.int("total")
    }

    fun setFolder(folder: String) = go {
        currentFolder = folder
        loadFolders()
        loadMessages(0)
    }

    fun setMailbox(address: String) = go {
        currentMailbox = address
        loadFolders()
        loadMessages(0)
    }

    fun toggleUnread() = go {
        unreadOnly = !unreadOnly
        loadMessages(0)
    }

    fun search() = go { loadMessages(0) }

    fun loadMore() = go { loadMessages(messages.size) }

    fun open(id: Long) = go {
        val result = RustBridge.call("message", JSONObject().put("id", id))
        detail = MessageDetail.from(result)
        messages = messages.map { if (it.id == id) it.copy(seen = true) else it }
        savedFilePath = null
        screen = Screen.Detail(id)
    }

    fun backToList() {
        screen = Screen.List
    }

    fun openCompose() {
        screen = Screen.Compose
    }

    fun toggleStar(message: MessageSummary) = go {
        RustBridge.call(
            if (message.flagged) "unstar" else "star",
            JSONObject().put("id", message.id)
        )
        messages = messages.map {
            if (it.id == message.id) it.copy(flagged = !message.flagged) else it
        }
        detail = detail?.copy(flagged = !message.flagged)
    }

    fun markUnread(id: Long) = go {
        RustBridge.call("markUnread", JSONObject().put("id", id))
        messages = messages.map { if (it.id == id) it.copy(seen = false) else it }
    }

    fun moveMessage(id: Long, folder: String) = go {
        RustBridge.call("moveMessage", JSONObject().put("id", id).put("folder", folder))
        messages = messages.filter { it.id != id }
        detail = null
        screen = Screen.List
    }

    fun deleteMessage(id: Long) = go {
        RustBridge.call("deleteMessage", JSONObject().put("id", id))
        messages = messages.filter { it.id != id }
        detail = null
        screen = Screen.List
    }

    fun sendMail(
        from: String,
        to: String,
        cc: String,
        subject: String,
        text: String,
        files: List<PickedFile>
    ) = go {
        val attachments = JSONArray()
        files.forEach {
            attachments.put(
                JSONObject()
                    .put("filename", it.name)
                    .put("mime", it.mime)
                    .put("path", it.path)
            )
        }
        val payload = JSONObject().apply {
            put("to", to)
            put("cc", cc)
            put("subject", subject)
            put("text", text)
            if (from.isNotBlank()) put("from", from)
            put("attachments", attachments)
        }
        RustBridge.call("send", payload)
        toast = "已提交发送"
        screen = Screen.List
        loadMessages(0)
    }

    private suspend fun loadAliases() {
        val result = RustBridge.call("aliases")
        aliases = result.optJSONArray("aliases").objects().map { Alias.from(it) }
        aliasDomains = result.optJSONArray("domains").strings()
        aliasAllowCreate = result.bool("allowSelfCreate")
    }

    fun createAlias(prefix: String, domain: String) = go {
        val payload = JSONObject().put("prefix", prefix.trim())
        if (domain.isNotBlank()) payload.put("domain", domain)
        RustBridge.call("createAlias", payload)
        loadAliases()
        loadMailboxes()
        toast = "别名已创建"
    }

    fun deleteAlias(id: Long) = go {
        RustBridge.call("deleteAlias", JSONObject().put("id", id))
        loadAliases()
        loadMailboxes()
    }

    fun loadSettings() = go {
        settings = RustBridge.call("settings")
        clientConfig = RustBridge.call("clientConfig")
        loadAliases()
    }

    fun changePassword(oldPassword: String, newPassword: String) = go {
        RustBridge.call(
            "changePassword",
            JSONObject().put("oldPassword", oldPassword).put("newPassword", newPassword)
        )
        toast = "密码已修改"
    }

    fun downloadAttachment(id: Long, filename: String) = go {
        val result = RustBridge.call(
            "attachment",
            JSONObject().put("id", id).put("filename", filename)
        )
        savedFilePath = result.str("path")
        savedFileMime = result.str("mime")
        toast = "已保存 ${result.str("filename")}"
    }

    fun logout() = go {
        try {
            RustBridge.call("logout")
        } catch (e: Exception) {
            // 即使服务端不可达也要清掉本地状态
        }
        user = null
        messages = emptyList()
        detail = null
        currentMailbox = null
        screen = Screen.Login
    }

    fun dismissError() {
        error = null
    }

    fun dismissToast() {
        toast = null
    }
}
