@file:OptIn(ExperimentalMaterial3Api::class)
package com.ifwqu.domainmail.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.ifwqu.domainmail.Alias
import com.ifwqu.domainmail.MailState
import com.ifwqu.domainmail.MessageSummary
import com.ifwqu.domainmail.PickedFile
import com.ifwqu.domainmail.Screen
import com.ifwqu.domainmail.folderLabel
import com.ifwqu.domainmail.formatDate
import com.ifwqu.domainmail.formatDateFull
import com.ifwqu.domainmail.long
import com.ifwqu.domainmail.obj
import com.ifwqu.domainmail.str
import java.io.File

private val BrandBlue = Color(0xFF2563EB)
private val Muted = Color(0xFF6B7280)

@Composable
fun DomainMailApp() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember { MailState(context, scope) }
    LaunchedEffect(Unit) { state.start() }

    MaterialTheme(colorScheme = lightColorScheme(primary = BrandBlue)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (state.screen) {
                    is Screen.Login -> LoginScreen(state)
                    is Screen.Detail -> DetailScreen(state)
                    is Screen.Compose -> ComposeScreen(state)
                    is Screen.List -> MainScreen(state)
                }

                if (state.busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }

                val error = state.error
                if (error != null) {
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        action = { TextButton(onClick = { state.dismissError() }) { Text("关闭") } }
                    ) { Text(error) }
                } else {
                    val message = state.toast
                    if (message != null) {
                        Snackbar(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            action = { TextButton(onClick = { state.dismissToast() }) { Text("好") } }
                        ) { Text(message) }
                    }
                }
            }
        }
    }
}

// ---------------- 登录 ----------------

@Composable
private fun LoginScreen(state: MailState) {
    var base by remember { mutableStateOf(state.savedBaseUrl.ifBlank { "https://" }) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedBaseUrl) {
        if (state.savedBaseUrl.isNotBlank()) base = state.savedBaseUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DomainMail", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
        Spacer(Modifier.height(6.dp))
        Text("连接你的自建域名邮箱", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = base,
            onValueChange = { base = it },
            label = { Text("服务器地址") },
            placeholder = { Text("https://mail.example.com:8080") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱账号") },
            placeholder = { Text("you@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { insecure = !insecure },
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Checkbox(checked = insecure, onCheckedChange = { insecure = it })
            Text("允许自签证书 / 不安全 TLS（自建服务器常用）", fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { state.login(base, email, password, insecure) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("登录") }
        Spacer(Modifier.height(12.dp))
        Text("会话只保存 dm_session，不保存密码", fontSize = 11.sp, color = Muted)
    }
}

// ---------------- 主框架 ----------------

@Composable
private fun MainScreen(state: MailState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DomainMail") },
                actions = {
                    IconButton(onClick = { state.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == 0,
                    onClick = { state.tab = 0 },
                    icon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                    label = { Text("邮件") }
                )
                NavigationBarItem(
                    selected = state.tab == 1,
                    onClick = { state.tab = 1 },
                    icon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    label = { Text("别名") }
                )
                NavigationBarItem(
                    selected = state.tab == 2,
                    onClick = { state.tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        },
        floatingActionButton = {
            if (state.tab == 0) {
                FloatingActionButton(onClick = { state.openCompose() }) {
                    Icon(Icons.Default.Add, contentDescription = "写邮件")
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.tab) {
                0 -> MailListScreen(state)
                1 -> AliasesScreen(state)
                else -> SettingsScreen(state)
            }
        }
    }
}

// ---------------- 邮件列表 ----------------

@Composable
private fun MailListScreen(state: MailState) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("邮箱：", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(
                        state.currentMailbox ?: "主邮箱",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.mailboxes.forEach { mailbox ->
                        DropdownMenuItem(
                            text = { Text("${mailbox.label} (${mailbox.unread})") },
                            onClick = {
                                expanded = false
                                state.setMailbox(mailbox.address)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            state.folders.forEach { folder ->
                FilterChip(
                    selected = state.currentFolder == folder.name,
                    onClick = { state.setFolder(folder.name) },
                    label = {
                        Text(
                            folderLabel(folder.name) +
                                if (folder.unread > 0) " ${folder.unread}" else ""
                        )
                    },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.showSearch) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    placeholder = { Text("搜索邮件") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { state.search() }) { Text("搜索") }
            } else {
                Text(
                    "共 ${state.total} 封",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.weight(1f)
                )
            }
            FilterChip(
                selected = state.unreadOnly,
                onClick = { state.toggleUnread() },
                label = { Text("未读") }
            )
            IconButton(onClick = { state.showSearch = !state.showSearch }) {
                Icon(
                    if (state.showSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "搜索"
                )
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(state.messages, key = { it.id }) { message ->
                MessageRow(state, message)
            }
            item {
                when {
                    state.messages.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("暂无邮件", color = Muted) }

                    state.messages.size < state.total -> TextButton(
                        onClick = { state.loadMore() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("加载更多 (${state.messages.size}/${state.total})") }

                    else -> Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("— 到底了 —", color = Muted, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(state: MailState, message: MessageSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.open(message.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (message.seen) Color.Transparent else BrandBlue,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.fromName.ifBlank { message.from.ifBlank { "(未知发件人)" } },
                    fontWeight = if (message.seen) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatDate(message.date), fontSize = 11.sp, color = Muted)
            }
            Text(
                message.subject,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (message.seen) FontWeight.Normal else FontWeight.SemiBold,
                fontSize = 14.sp
            )
            if (message.snippet.isNotBlank()) {
                Text(
                    message.snippet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
        IconButton(onClick = { state.toggleStar(message) }) {
            Icon(
                Icons.Default.Star,
                contentDescription = "星标",
                tint = if (message.flagged) Color(0xFFF59E0B) else Color(0xFFD1D5DB)
            )
        }
    }
    HorizontalDivider()
}

// ---------------- 邮件详情 ----------------

@Composable
private fun DetailScreen(state: MailState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("邮件详情") },
                navigationIcon = {
                    IconButton(onClick = { state.backToList() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { state.detail?.let { state.deleteMessage(it.id) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(detail.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                InfoRow("发件人", "${detail.fromName} <${detail.from}>".trim())
                InfoRow("收件人", detail.to)
                if (detail.cc.isNotBlank()) InfoRow("抄送", detail.cc)
                InfoRow("时间", formatDateFull(detail.date))
                InfoRow("文件夹", folderLabel(detail.folder))
                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                if (detail.html.isNotBlank()) {
                    key(detail.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 900.dp),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.blockNetworkLoads = true
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(
                                    null, detail.html, "text/html", "UTF-8", null
                                )
                            }
                        )
                    }
                } else {
                    Text(detail.text.ifBlank { "(无正文)" }, fontSize = 14.sp)
                }

                if (detail.attachments.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("附件 (${detail.attachments.size})", fontWeight = FontWeight.Bold)
                    detail.attachments.forEach { attachment ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = Muted)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(attachment.filename, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${attachment.size} B · ${attachment.mime}", fontSize = 11.sp, color = Muted)
                            }
                            TextButton(onClick = {
                                state.downloadAttachment(attachment.id, attachment.filename)
                            }) { Text("下载") }
                        }
                    }
                }

                state.savedFilePath?.let { path ->
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { openDownloaded(state.appContext, path, state.savedFileMime) }) {
                        Text("打开已下载文件")
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { state.markUnread(detail.id); state.backToList() }) {
                        Text("标为未读")
                    }
                    OutlinedButton(onClick = { state.moveMessage(detail.id, "Trash") }) {
                        Text("移到回收站")
                    }
                }
            }
        }
    }
}

// ---------------- 写邮件 ----------------

@Composable
private fun ComposeScreen(state: MailState) {
    val context = LocalContext.current
    var from by remember { mutableStateOf(state.currentMailbox ?: state.user?.email ?: "") }
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<PickedFile>()) }
    var fromExpanded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) picked = picked + copyUriToCache(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("写邮件") },
                navigationIcon = {
                    IconButton(onClick = { state.backToList() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { state.sendMail(from, to, cc, subject, body, picked) },
                        enabled = !state.busy
                    ) { Text("发送") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box {
                OutlinedButton(onClick = { fromExpanded = true }) {
                    Text("发件人：$from", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                    state.mailboxes.forEach { mailbox ->
                        DropdownMenuItem(
                            text = { Text(mailbox.address) },
                            onClick = { from = mailbox.address; fromExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("收件人（逗号分隔）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cc,
                onValueChange = { cc = it },
                label = { Text("抄送（可选）") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("主题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("正文") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { picker.launch("*/*") }) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加附件")
            }
            picked.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Muted)
                    Spacer(Modifier.width(8.dp))
                    Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { picked = picked.filter { it != file } }) {
                        Icon(Icons.Default.Close, contentDescription = "移除")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { state.sendMail(from, to, cc, subject, body, picked) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("发送")
            }
        }
    }
}

// ---------------- 别名 ----------------

@Composable
private fun AliasesScreen(state: MailState) {
    LaunchedEffect(Unit) { state.loadSettings() }
    var prefix by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("无限别名", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("前缀回车即建，全部投递到主邮箱", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("前缀") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(domain.ifBlank { "默认域名" }, maxLines = 1)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("默认域名") },
                        onClick = { domain = ""; expanded = false }
                    )
                    state.aliasDomains.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { domain = name; expanded = false }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (prefix.isNotBlank()) {
                    state.createAlias(prefix, domain)
                    prefix = ""
                }
            },
            enabled = state.aliasAllowCreate && !state.busy
        ) { Text("创建别名") }
        if (!state.aliasAllowCreate) {
            Text("管理员已关闭自助创建别名", color = Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("已有 ${state.aliases.size} 个别名", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.aliases, key = { it.id }) { alias: Alias ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(alias.address, fontWeight = FontWeight.Medium)
                        Text(
                            "→ ${alias.targets.joinToString(", ")}" +
                                if (alias.active) "" else "（已停用）",
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                    IconButton(onClick = { state.deleteAlias(alias.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// ---------------- 设置 ----------------

@Composable
private fun SettingsScreen(state: MailState) {
    LaunchedEffect(Unit) { state.loadSettings() }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val imap = state.clientConfig.obj("imap")
    val smtp = state.clientConfig.obj("smtp")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                InfoRow("账号", state.user?.email ?: "")
                InfoRow("显示名", state.user?.displayName ?: "")
                InfoRow("服务器", state.savedBaseUrl)
                InfoRow("已用容量", "${state.settings.long("usedMb")} / ${state.settings.long("quotaMb")} MB")
                InfoRow("管理员", if (state.user?.isAdmin == true) "是" else "否")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("客户端配置（Foxmail / 系统邮件 App 可直接用）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                InfoRow("IMAP", "${imap.str("host")}:${imap.long("port")} ${imap.str("security")}")
                InfoRow("SMTP", "${smtp.str("host")}:${smtp.long("port")} ${smtp.str("security")}")
                InfoRow("Webmail", state.clientConfig.str("webmail"))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("修改密码", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = oldPassword,
            onValueChange = { oldPassword = it },
            label = { Text("当前密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("新密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (oldPassword.isNotBlank() && newPassword.isNotBlank()) {
                state.changePassword(oldPassword, newPassword)
                oldPassword = ""
                newPassword = ""
            }
        }) { Text("修改密码") }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { state.logout() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("退出登录")
        }
        Spacer(Modifier.height(12.dp))
        Text("DomainMail Android · 业务核心由 Rust 实现", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

// ---------------- 工具 ----------------

private fun copyUriToCache(context: Context, uri: Uri): PickedFile {
    var name = "attachment"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index) ?: name
        }
    }
    val safe = name.map { if ("/\\:*?\"<>|".contains(it)) '_' else it }.joinToString("")
    val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
    val file = File(dir, "${System.currentTimeMillis()}_$safe")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return PickedFile(file.absolutePath, safe, guessMime(safe))
}

private fun guessMime(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "txt", "log", "md", "csv" -> "text/plain"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

private fun openDownloaded(context: Context, path: String, mime: String?) {
    try {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", File(path)
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime?.ifBlank { "*/*" } ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "打不开文件：${e.message}", Toast.LENGTH_SHORT).show()
    }
}
