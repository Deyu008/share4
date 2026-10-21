package com.deyu.share.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.Avatar
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

    Column(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }
            error != null && users.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            users.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center) {
                Text("暂无私信会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn {
                items(users, key = { it.uid }) { u ->
                    Row(Modifier.fillMaxWidth()
                        .clickable { nav.navigate(Routes.dmChat(u.uid, u.name)) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.avatar, u.name, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontSize = 15.sp,
                                style = MaterialTheme.typography.titleSmall)
                            Text(stripHtml(u.lastText), fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (u.unread > 0) {
                                Badge { Text("${u.unread}") }
                            } else {
                                Text(formatRelativeTime(u.lastAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 76.dp))
                }
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
        scope.launch {
            val sent = AppState.client.dmSend(uid, text)
            if (sent != null) {
                messages = messages + sent
                draft = ""
            }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f)) {
            when {
                loading -> CircularProgressIndicator(strokeWidth = 2.5.dp,
                    modifier = Modifier.align(Alignment.Center))
                error != null && messages.isEmpty() ->
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages, key = { it.id }) { m ->
                        MessageBubble(m, mine = m.senderId == me)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text("发私信…") }, singleLine = true,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send() },
                enabled = draft.isNotBlank() && !sending) {
                Icon(Icons.Filled.Send, "发送")
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
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(
                        topStart = if (mine) 14.dp else 3.dp,
                        topEnd = if (mine) 3.dp else 14.dp,
                        bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(stripHtml(m.text), fontSize = 15.sp, lineHeight = 20.sp,
                    color = if (mine) androidx.compose.ui.graphics.Color.White
                            else MaterialTheme.colorScheme.onSurface)
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
