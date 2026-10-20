package com.deyu.share.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.Avatar
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboMblog
import com.deyu.share4.protocol.WeiboUserProfile
import kotlinx.coroutines.launch

/** 「我」页:自己的资料摘要 + 我的微博流(users/show + userTimeline) */
@Composable
fun MePage(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    val session = AppState.session
    // profile/me 的 mineinfo 已不返回计数字段(2026-10-21 实测),计数统一走 users/show
    var profile by remember { mutableStateOf<WeiboUserProfile?>(null) }
    var myPosts by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(session?.uid) {
        if (session != null) {
            scope.launch {
                profile = runCatching { AppState.client.userProfile(session.uid) }.getOrNull()
                myPosts = runCatching { AppState.client.userTimeline(session.uid) }
                    .getOrDefault(emptyList())
                loading = false
            }
        } else loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text("我", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        if (session == null) {
            Text("未登录", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }) { Text("去登录") }
        } else if (loading) {
            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.padding(24.dp))
        } else {
            val p = profile
            // 头像 + 昵称
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Avatar(p?.avatarHd, p?.screenName ?: "我")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p?.screenName?.ifBlank { session.screenName ?: "" }
                            ?: (session.screenName ?: "微博用户"),
                            style = MaterialTheme.typography.titleMedium)
                        if (p?.verified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.CheckCircle, null,
                                Modifier.padding(top = 2.dp).height(15.dp),
                                tint = Color(0xFFE8821E))
                        }
                    }
                    Text("uid ${session.uid}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    p?.location?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!p?.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(p!!.description, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth())
            }
            // 数据三联
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("${p?.friends ?: 0}", "关注")
                Stat("${p?.statuses ?: 0}", "微博")
                Stat("${p?.followers ?: 0}", "粉丝")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                AppState.logout(ctx)
                nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }) { Text("退出登录") }
            Spacer(Modifier.height(8.dp))
            // 我的微博
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("我的微博", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("${myPosts.size} 条", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
                items(myPosts, key = { it.mid }) { m ->
                    WeiboCard(m, cardNav, Modifier.padding(horizontal = -10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 66.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
