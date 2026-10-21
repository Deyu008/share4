package com.deyu.share.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/** 通知页:@我的微博 + 未读数聚合(2026-10-20 实测端点) */
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MailOutline, null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("私信", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            val dmUnread = unread["dm"] ?: 0
            if (dmUnread > 0) {
                Text("$dmUnread 条新消息", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // 未读数横条
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val items = listOf(
                "评论" to unread["cmt"], "@我" to unread["mention_status"],
                "赞" to unread["attitude"], "私信" to unread["dm"], "新粉丝" to unread["follower"])
            items.forEach { (label, n) ->
                AssistChip(onClick = {}, label = { Text("$label ${n ?: 0}") })
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }
        } else if (mentions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text("暂无 @我的 微博", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                items(mentions, key = { it.mid }) { m ->
                    WeiboCard(m, cardNav, Modifier.padding(horizontal = 6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}
