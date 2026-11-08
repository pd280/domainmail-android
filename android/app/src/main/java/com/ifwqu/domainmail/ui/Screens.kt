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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.ifwqu.domainmail.Mailbox
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

@Composable
fun DomainMailApp() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember { MailState(context, scope) }
    LaunchedEffect(Unit) { state.start() }

    DmTheme {
        val palette = dm()
        Surface(modifier = Modifier.fillMaxSize(), color = palette.appBg) {
            Box(Modifier.fillMaxSize()) {
                when (state.screen) {
                    is Screen.Login -> LoginScreen(state)
                    is Screen.Detail -> DetailScreen(state)
                    is Screen.Compose -> ComposeScreen(state)
                    is Screen.List -> MainScreen(state)
                }

                if (state.busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = palette.blue
                    )
                }

                val error = state.error
                if (error != null) {
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        containerColor = palette.danger,
                        contentColor = Color.White,
                        action = { TextButton(onClick = { state.dismissError() }) { Text("关闭", color = Color.White) } }
                    ) { Text(error) }
                } else {
                    val message = state.toast
                    if (message != null) {
                        Snackbar(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            action = { TextButton(onClick = { state.dismissToast() }) { Text("好") } }
                        ) { Text(message) }
                    }
                }
            }
        }
    }
}

// ---------------- 品牌头 ----------------

@Composable
private fun BrandMark(size: Int = 34) {
    val palette = dm()
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(palette.blue, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MailOutline,
            contentDescription = null,
            tint = palette.onBlue,
            modifier = Modifier.size((size * 0.55f).dp)
        )
    }
}

// ---------------- 登录 ----------------

@Composable
private fun LoginScreen(state: MailState) {
    val palette = dm()
    var base by remember { mutableStateOf(state.savedBaseUrl.ifBlank { "https://" }) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedBaseUrl) {
        if (state.savedBaseUrl.isNotBlank() && base == "https://") base = state.savedBaseUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        BrandMark(56)
        Spacer(Modifier.height(16.dp))
        Text("DomainMail", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = palette.ink)
        Text("你的自建域名邮箱", fontSize = 13.sp, color = palette.muted)
        Spacer(Modifier.height(28.dp))

        DmCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                DmSectionTitle("服务器")
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("地址") },
                    placeholder = { Text("https://mail.example.com:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                DmSectionTitle("账号")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
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
                    Text("允许自签证书 / 不安全 TLS", fontSize = 13.sp, color = palette.ink2)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { state.login(base, email, password, insecure) },
            enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = palette.blue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.MailOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        Text("会话只保存 dm_session，不保存密码", fontSize = 11.sp, color = palette.muted)
    }
}

// ---------------- 主框架 ----------------

@Composable
private fun MainScreen(state: MailState) {
    val palette = dm()
    Scaffold(
        containerColor = palette.appBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(30)
                        Spacer(Modifier.width(10.dp))
                        Text("DomainMail", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.appBg),
                actions = {
                    IconButton(onClick = { state.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = palette.ink2)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = palette.cardBg, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = state.tab == 0,
                    onClick = { state.tab = 0 },
                    icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                    label = { Text("邮件", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { state.openCompose() },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("写信", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = state.tab == 1,
                    onClick = { state.tab = 1 },
                    icon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    label = { Text("邮箱", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = state.tab == 2,
                    onClick = { state.tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置", fontSize = 11.sp) }
                )
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
    val palette = dm()
    var showMailboxSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                MailboxSwitcherCard(state) { showMailboxSheet = true }
                Spacer(Modifier.height(12.dp))
                FolderRow(state)
                Spacer(Modifier.height(10.dp))
                FilterRow(state)
            }
        }

        items(state.messages, key = { it.id }) { message ->
            MessageRow(state, message)
        }

        item {
            when {
                state.messages.isEmpty() -> EmptyState("还没有邮件", "下拉刷新或切换文件夹看看")
                state.messages.size < state.total -> TextButton(
                    onClick = { state.loadMore() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) { Text("加载更多 (${state.messages.size}/${state.total})", color = palette.blue) }

                else -> Box(
                    Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) { Text("— 到底了 —", color = palette.muted, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showMailboxSheet) {
        MailboxSheet(state) { showMailboxSheet = false }
    }
}

/** 整行身份卡：完整显示地址，不再挤在小按钮里被截断。 */
@Composable
private fun MailboxSwitcherCard(state: MailState, onClick: () -> Unit) {
    val palette = dm()
    val current = state.currentMailbox ?: state.user?.email ?: ""
    val active: Mailbox? = state.mailboxes.firstOrNull { it.address == current }
    val title = active?.label?.takeIf { it.isNotBlank() }
        ?: state.user?.displayName?.takeIf { it.isNotBlank() }
        ?: "主邮箱"

    DmCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DmAvatar(title, current.ifBlank { title }, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("当前邮箱", fontSize = 11.sp, color = palette.muted)
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 完整地址：允许换行，绝不省略
                Text(
                    current.ifBlank { "尚未登录" },
                    fontSize = 12.sp,
                    color = palette.muted2,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            active?.let { DmBadge(it.unread) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ExpandMore, contentDescription = "切换邮箱", tint = palette.muted)
        }
    }
}

/** 底部弹层：邮箱列表完整铺开、可滚动，长地址不再被 DropdownMenu 裁掉。 */
@Composable
private fun MailboxSheet(state: MailState, onDismiss: () -> Unit) {
    val palette = dm()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.cardBg,
        contentColor = palette.ink
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("切换邮箱", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.ink)
            Text(
                "共 ${state.mailboxes.size} 个身份 · 列表、未读数与发件人随之切换",
                fontSize = 12.sp,
                color = palette.muted
            )
            Spacer(Modifier.height(14.dp))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .padding(bottom = 28.dp)
        ) {
            items(state.mailboxes, key = { it.address }) { mailbox ->
                MailboxSheetRow(state, mailbox, onDismiss)
            }
            if (state.mailboxes.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无可用邮箱", color = palette.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun MailboxSheetRow(state: MailState, mailbox: Mailbox, onDismiss: () -> Unit) {
    val palette = dm()
    val selected = mailbox.address == (state.currentMailbox ?: state.user?.email)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.blueSoft else Color.Transparent)
            .clickable {
                state.setMailbox(mailbox.address)
                onDismiss()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DmAvatar(mailbox.label, mailbox.address, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                mailbox.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink
            )
            // 完整地址，允许换行
            Text(mailbox.address, fontSize = 13.sp, color = palette.muted2, lineHeight = 17.sp)
            Text("${mailbox.total} 封 · ${mailbox.unread} 未读", fontSize = 11.sp, color = palette.muted)
        }
        Spacer(Modifier.width(8.dp))
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "当前", tint = palette.blue)
        } else {
            DmBadge(mailbox.unread)
        }
    }
    HorizontalDivider(color = palette.lineLight, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun FolderRow(state: MailState) {
    val palette = dm()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        state.folders.forEach { folder ->
            FilterChip(
                selected = state.currentFolder == folder.name,
                onClick = { state.setFolder(folder.name) },
                label = {
                    Text(
                        folderLabel(folder.name) + if (folder.unread > 0) " ${folder.unread}" else ""
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.blue,
                    selectedLabelColor = palette.onBlue,
                    containerColor = palette.cardBg,
                    labelColor = palette.ink2
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun FilterRow(state: MailState) {
    val palette = dm()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.showSearch) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                placeholder = { Text("搜索邮件") },
                singleLine = true,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            )
            TextButton(onClick = { state.search() }) { Text("搜索", color = palette.blue) }
        } else {
            Text(
                "共 ${state.total} 封",
                fontSize = 12.sp,
                color = palette.muted,
                modifier = Modifier.weight(1f)
            )
        }
        FilterChip(
            selected = state.unreadOnly,
            onClick = { state.toggleUnread() },
            label = { Text("未读") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.blueSoft,
                selectedLabelColor = palette.blue,
                containerColor = palette.cardBg,
                labelColor = palette.ink2
            )
        )
        IconButton(onClick = { state.showSearch = !state.showSearch }) {
            Icon(
                if (state.showSearch) Icons.Default.Close else Icons.Default.Search,
                contentDescription = "搜索",
                tint = palette.ink2
            )
        }
    }
}

@Composable
private fun MessageRow(state: MailState, message: MessageSummary) {
    val palette = dm()
    val sender = message.fromName.ifBlank { message.from.ifBlank { "(未知发件人)" } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (message.seen) Color.Transparent else palette.blueSoft.copy(alpha = 0.45f))
            .clickable { state.open(message.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DmAvatar(sender, message.from, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sender,
                    fontWeight = if (message.seen) FontWeight.Normal else FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatDate(message.date), fontSize = 11.sp, color = palette.muted)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.subject,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (message.seen) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = palette.ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (message.hasAttachment) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "有附件",
                        tint = palette.muted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (message.snippet.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    message.snippet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = palette.muted2
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        if (!message.seen) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(palette.blue, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        }
        IconButton(onClick = { state.toggleStar(message) }, modifier = Modifier.size(36.dp)) {
            Icon(
                if (message.flagged) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "星标",
                tint = if (message.flagged) palette.star else palette.muted.copy(alpha = 0.6f)
            )
        }
    }
    HorizontalDivider(color = palette.lineLight, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    val palette = dm()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(palette.blueSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MailOutline, null, tint = palette.blue, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = palette.muted)
    }
}

// ---------------- 邮件详情 ----------------

@Composable
private fun DetailScreen(state: MailState) {
    val palette = dm()
    Scaffold(
        containerColor = palette.appBg,
        topBar = {
            TopAppBar(
                title = { Text("邮件详情", color = palette.ink, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = { state.backToList() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.appBg),
                actions = {
                    IconButton(onClick = { state.detail?.let { state.deleteMessage(it.id) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = palette.danger)
                    }
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.blue)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                item {
                    DmCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text(detail.subject, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DmAvatar(detail.fromName.ifBlank { detail.from }, detail.from, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        detail.fromName.ifBlank { detail.from },
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.ink,
                                        fontSize = 14.sp
                                    )
                                    Text(detail.from, fontSize = 12.sp, color = palette.muted2)
                                }
                                Text(formatDate(detail.date), fontSize = 11.sp, color = palette.muted)
                            }
                            Spacer(Modifier.height(14.dp))
                            InfoRow("收件人", detail.to)
                            if (detail.cc.isNotBlank()) InfoRow("抄送", detail.cc)
                            InfoRow("文件夹", folderLabel(detail.folder))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    DmCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
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
                                Text(
                                    detail.text.ifBlank { "(无正文)" },
                                    fontSize = 14.sp,
                                    color = palette.ink2,
                                    lineHeight = 25.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (detail.attachments.isNotEmpty()) {
                    item {
                        DmCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                DmSectionTitle("附件 (${detail.attachments.size})")
                                detail.attachments.forEach { attachment ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Description, null, tint = palette.blue)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(attachment.filename, fontSize = 13.sp, color = palette.ink)
                                            Text(
                                                "${attachment.size} B · ${attachment.mime}",
                                                fontSize = 11.sp,
                                                color = palette.muted
                                            )
                                        }
                                        TextButton(onClick = {
                                            state.downloadAttachment(attachment.id, attachment.filename)
                                        }) { Text("下载", color = palette.blue) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                item {
                    state.savedFilePath?.let { path ->
                        Button(
                            onClick = { openDownloaded(state.appContext, path, state.savedFileMime) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.blue),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("打开已下载文件") }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { state.markUnread(detail.id); state.backToList() },
                            modifier = Modifier.weight(1f)
                        ) { Text("标为未读") }
                        OutlinedButton(
                            onClick = { state.moveMessage(detail.id, "Trash") },
                            modifier = Modifier.weight(1f)
                        ) { Text("移到回收站") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ---------------- 写邮件 ----------------

@Composable
private fun ComposeScreen(state: MailState) {
    val palette = dm()
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
        containerColor = palette.appBg,
        topBar = {
            TopAppBar(
                title = { Text("写邮件", color = palette.ink, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = { state.backToList() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.appBg),
                actions = {
                    TextButton(
                        onClick = { state.sendMail(from, to, cc, subject, body, picked) },
                        enabled = !state.busy
                    ) {
                        Icon(Icons.Default.Send, null, tint = palette.blue)
                        Spacer(Modifier.width(4.dp))
                        Text("发送", color = palette.blue, fontWeight = FontWeight.SemiBold)
                    }
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
            DmCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { fromExpanded = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null, tint = palette.muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("发件人", fontSize = 11.sp, color = palette.muted)
                                Text(from, fontSize = 14.sp, color = palette.ink, lineHeight = 18.sp)
                            }
                            Icon(Icons.Default.ExpandMore, null, tint = palette.muted)
                        }
                        DropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                            state.mailboxes.forEach { mailbox ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(mailbox.label, fontSize = 14.sp)
                                            Text(mailbox.address, fontSize = 12.sp, color = palette.muted)
                                        }
                                    },
                                    onClick = { from = mailbox.address; fromExpanded = false }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = palette.lineLight, modifier = Modifier.padding(vertical = 8.dp))
                    LabeledInput("收件人", to, { to = it }, "逗号分隔多个地址", KeyboardType.Email)
                    LabeledInput("抄送", cc, { cc = it }, "可选", KeyboardType.Email)
                    LabeledInput("主题", subject, { subject = it }, "", KeyboardType.Text)
                }
            }
            Spacer(Modifier.height(12.dp))
            DmCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        placeholder = { Text("写点什么…", color = palette.muted) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            color = palette.ink,
                            lineHeight = 25.sp
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DmCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { picker.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(6.dp))
                        Text("添加附件")
                    }
                    picked.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, tint = palette.muted)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                file.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp
                            )
                            IconButton(onClick = { picked = picked.filter { it != file } }) {
                                Icon(Icons.Default.Close, contentDescription = "移除", tint = palette.muted)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    type: KeyboardType
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = dm().muted, modifier = Modifier.width(56.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------- 别名 ----------------

@Composable
private fun AliasesScreen(state: MailState) {
    val palette = dm()
    LaunchedEffect(Unit) { state.loadSettings() }
    var prefix by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Text("无限别名", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.ink)
            Text("前缀回车即建，全部投递到主邮箱", fontSize = 12.sp, color = palette.muted)
            Spacer(Modifier.height(16.dp))
            DmCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    DmSectionTitle("新建别名")
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("前缀") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(domain.ifBlank { "默认域名" })
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
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (prefix.isNotBlank()) {
                                state.createAlias(prefix, domain)
                                prefix = ""
                            }
                        },
                        enabled = state.aliasAllowCreate && !state.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.blue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("创建别名") }
                    if (!state.aliasAllowCreate) {
                        Spacer(Modifier.height(8.dp))
                        Text("管理员已关闭自助创建别名", color = palette.muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            DmSectionTitle("已有 ${state.aliases.size} 个别名")
            Spacer(Modifier.height(8.dp))
        }

        items(state.aliases, key = { it.id }) { alias: Alias ->
            DmCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DmAvatar(alias.address, alias.address, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alias.address, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.ink)
                        Text(
                            "→ ${alias.targets.joinToString(", ")}" + if (alias.active) "" else "（已停用）",
                            fontSize = 11.sp,
                            color = palette.muted
                        )
                    }
                    IconButton(onClick = { state.deleteAlias(alias.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = palette.danger)
                    }
                }
            }
        }

        if (state.aliases.isEmpty()) {
            item { EmptyState("还没有别名", "在上面输入前缀创建一个吧") }
        }
    }
}

// ---------------- 设置 ----------------

@Composable
private fun SettingsScreen(state: MailState) {
    val palette = dm()
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
        Text("设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.ink)
        Spacer(Modifier.height(16.dp))

        DmCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DmAvatar(state.user?.displayName ?: "我", state.user?.email ?: "", 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.user?.displayName?.takeIf { it.isNotBlank() } ?: "DomainMail 用户",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = palette.ink
                    )
                    Text(state.user?.email ?: "", fontSize = 12.sp, color = palette.muted2)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        DmCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DmSectionTitle("账号信息")
                InfoRow("服务器", state.savedBaseUrl)
                InfoRow("已用容量", "${state.settings.long("usedMb")} / ${state.settings.long("quotaMb")} MB")
                InfoRow("管理员", if (state.user?.isAdmin == true) "是" else "否")
            }
        }

        Spacer(Modifier.height(12.dp))
        DmCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DmSectionTitle("客户端配置")
                InfoRow("IMAP", "${imap.str("host")}:${imap.long("port")} ${imap.str("security")}")
                InfoRow("SMTP", "${smtp.str("host")}:${smtp.long("port")} ${smtp.str("security")}")
                InfoRow("Webmail", state.clientConfig.str("webmail"))
            }
        }

        Spacer(Modifier.height(12.dp))
        DmCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DmSectionTitle("修改密码")
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("当前密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (oldPassword.isNotBlank() && newPassword.isNotBlank()) {
                            state.changePassword(oldPassword, newPassword)
                            oldPassword = ""
                            newPassword = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.blue),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("修改密码") }
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = { state.logout() },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Logout, null, tint = palette.danger)
            Spacer(Modifier.width(8.dp))
            Text("退出登录", color = palette.danger)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "DomainMail Android · 业务核心由 Rust 实现",
            fontSize = 11.sp,
            color = palette.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = dm()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = palette.muted, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Text(value, fontSize = 12.sp, color = palette.ink2, modifier = Modifier.weight(1f))
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
