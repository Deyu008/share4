package com.deyu.share.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.Avatar
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.LoadingState
import com.deyu.share.ui.components.formatRelativeTime
import com.deyu.share.ui.components.stripHtml
import com.deyu.share4.protocol.WeiboConversation
import com.deyu.share4.protocol.WeiboDirectMessage
import kotlinx.coroutines.launch

/** 私信会话列表(仿 DirectMessageUsersActivity) */
@Composable
fun DmUsersScreen(nav: NavController) {
    var users by remember { mutableStateOf<List<WeiboConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            users = AppState.client.dmUsers()
        } catch (e: Exception) {
            error = e.message?.take(50) ?: "加载失败"
        }
        loading = false
    }

    when {
        loading -> LoadingState()
        error != null && users.isEmpty() -> EmptyState(
            text = "私信加载失败", icon = Icons.Outlined.MailOutline, hint = error)
        users.isEmpty() -> EmptyState(
            text = "暂无私信会话",
            icon = Icons.Outlined.MailOutline,
            hint = "在用户主页可以给 TA 发私信",
        )
        else -> LazyColumn {
            items(users, key = { it.uid }) { u ->
                Row(Modifier.fillMaxWidth()
                    .clickable { nav.navigate(Routes.dmChat(u.uid, u.name)) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.avatar, u.name, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name, fontSize = 15.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Text(formatRelativeTime(u.lastAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stripHtml(u.lastText), fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                            if (u.unread > 0) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.size(18.dp)
                                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (u.unread > 99) "99+" else "${u.unread}",
                                        color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 74.dp))
            }
        }
    }
}

/** 私信聊天页(仿 DirectMessageChatActivity):气泡消息流 + 底部输入框,发送乐观上屏 */
@Composable
fun DmChatScreen(uid: String, name: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<WeiboDirectMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val me = AppState.session?.uid

    LaunchedEffect(uid) {
        try {
            // conversation 最新在前 → 聊天要时间升序
            messages = AppState.client.dmConversation(uid).reversed()
        } catch (e: Exception) {
            error = e.message?.take(50) ?: "加载失败"
        }
        loading = false
    }
    // 新消息到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val text = draft.trim()
        if (text.isBlank() || sending) return
        sending = true
        sendError = null
        scope.launch {
            val sent = AppState.client.dmSend(uid, text)
            if (sent != null) {
                messages = messages + sent
                draft = ""
            } else {
                sendError = "发送失败:对方只接受互相关注用户的私信,或已被限制"
            }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (sendError != null) {
            Text(sendError ?: "", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingState()
                error != null && messages.isEmpty() ->
                    EmptyState("私信加载失败", icon = Icons.Outlined.MailOutline, hint = error)
                messages.isEmpty() -> EmptyState(
                    text = "还没有聊过天",
                    icon = Icons.Outlined.MailOutline,
                    hint = "发一条消息打个招呼吧",
                )
                else -> LazyColumn(state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages, key = { it.id }) { m ->
                        MessageBubble(m, mine = m.senderId == me)
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("发私信…", fontSize = 14.sp) }, singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { send() },
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.size(40.dp)) {
                    if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Filled.Send, "发送", Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 消息气泡:自己在右(主色),对方在左(浅面) */
@Composable
private fun MessageBubble(m: WeiboDirectMessage, mine: Boolean) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            Avatar(m.senderAvatar, m.senderName, size = 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box(
                Modifier
                    .widthIn(max = 272.dp)
                    .clip(RoundedCornerShape(
                        topStart = if (mine) 16.dp else 4.dp,
                        topEnd = if (mine) 4.dp else 16.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(stripHtml(m.text), fontSize = 15.sp, lineHeight = 21.sp,
                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
            }
            Text(formatRelativeTime(m.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
        if (mine) {
            Spacer(Modifier.width(8.dp))
            Avatar(m.senderAvatar, m.senderName, size = 32.dp)
        }
    }
}
