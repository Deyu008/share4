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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.FeedDivider
import com.deyu.share.ui.components.LoadingState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

/** 消息页:@我的微博 + 未读数聚合(2026-10-20 实测端点) */
@Composable
fun NotificationsPage(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var mentions by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var unread by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        scope.launch {
            unread = runCatching { AppState.client.unreadCount() }.getOrDefault(emptyMap())
            mentions = runCatching { AppState.client.mentions() }.getOrDefault(emptyList())
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        // 私信入口行(原版消息 tab 聚合通知与私信)
        Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.DM_USERS) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MailOutline, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("私信", style = MaterialTheme.typography.titleSmall)
                val dmUnread = unread["dm"] ?: 0
                Text(if (dmUnread > 0) "$dmUnread 条未读" else "查看会话",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dmUnread > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 未读概览:五等分,不再用会自动换行的 AssistChip
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            UnreadStat(Icons.Outlined.ChatBubbleOutline, "评论", unread["cmt"] ?: 0)
            UnreadStat(Icons.Outlined.AlternateEmail, "@我", unread["mention_status"] ?: 0)
            UnreadStat(Icons.Outlined.FavoriteBorder, "赞", unread["attitude"] ?: 0)
            UnreadStat(Icons.Filled.MailOutline, "私信", unread["dm"] ?: 0)
            UnreadStat(Icons.Outlined.PersonAdd, "新粉丝", unread["follower"] ?: 0)
        }
        HorizontalDivider(thickness = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("@ 我的微博", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            if (mentions.isNotEmpty()) {
                Text("${mentions.size} 条", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            loading -> LoadingState()
            mentions.isEmpty() -> EmptyState(
                text = "还没有人 @ 你",
                icon = Icons.Outlined.AlternateEmail,
                hint = "别人在微博里提到你时,会出现在这里",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(mentions, key = { it.mid }) { m ->
                    WeiboCard(m, cardNav)
                    FeedDivider()
                }
            }
        }
    }
}

/** 未读格:图标 + 计数徽标 + 名称,计数为 0 时用灰字 */
@Composable
private fun UnreadStat(icon: ImageVector, label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(icon, null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (count > 0) {
                Surface(color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(start = 14.dp)) {
                    Text(if (count > 99) "99+" else "$count",
                        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 11.sp,
            color = if (count > 0) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
