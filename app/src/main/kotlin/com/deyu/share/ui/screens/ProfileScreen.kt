package com.deyu.share.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.Avatar
import com.deyu.share.ui.components.CardNav
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.ErrorState
import com.deyu.share.ui.components.FeedDivider
import com.deyu.share.ui.components.ListFooter
import com.deyu.share.ui.components.LoadingState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share.ui.components.formatCount
import com.deyu.share4.protocol.WeiboMblog
import com.deyu.share4.protocol.WeiboUserProfile
import kotlinx.coroutines.launch

/** 用户主页 UI 状态(统一 sealed 模式) */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Error(val message: String) : ProfileUiState
    data class Ready(
        val profile: WeiboUserProfile,
        val posts: List<WeiboMblog>,
        val endReached: Boolean = false,
    ) : ProfileUiState
}

/**
 * 用户主页:资料头(users/show)+ 关注/取关按钮(friendships/create|destroy)
 * + TA 的微博流(分页)。头像可再点进嵌卡作者的主页,但不再点自己。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(uid: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    // 自己的主页里再点自己头像会重复入栈,过滤掉
    val cardNav = remember {
        val base = cardNavOf(nav)
        CardNav(
            openProfile = { if (it != uid) base.openProfile(it) },
            openDetail = base.openDetail,
            openPics = base.openPics,
            openVideo = base.openVideo,
        )
    }
    var state by remember { mutableStateOf<ProfileUiState>(ProfileUiState.Loading) }
    var following by remember { mutableStateOf<Boolean?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            state = ProfileUiState.Loading
            try {
                val profile = AppState.client.userProfile(uid)
                    ?: throw IllegalStateException("用户不存在")
                following = profile.following
                val posts = AppState.client.userTimeline(uid)
                state = ProfileUiState.Ready(profile, posts, posts.isEmpty())
            } catch (e: Exception) {
                state = ProfileUiState.Error(e.message ?: "加载失败")
            }
        }
    }
    LaunchedEffect(uid) { load() }

    // 触底加载 TA 的下一页微博
    val shouldLoadMore by derivedStateOf {
        val ready = state as? ProfileUiState.Ready
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        ready != null && !loadingMore && !ready.endReached && ready.posts.size >= 10 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (!shouldLoadMore) return@LaunchedEffect
        val ready = state as? ProfileUiState.Ready ?: return@LaunchedEffect
        loadingMore = true
        page += 1
        val more = runCatching { AppState.client.userTimeline(uid, page) }.getOrDefault(emptyList())
        state = ready.copy(
            posts = ready.posts + more.filter { n -> n.mid !in ready.posts.map { it.mid } },
            endReached = more.isEmpty(),
        )
        loadingMore = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? ProfileUiState.Ready)?.profile?.screenName ?: "用户主页",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ProfileUiState.Loading -> LoadingState()
                is ProfileUiState.Error -> ErrorState(s.message, { load() })
                is ProfileUiState.Ready -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "header") {
                        ProfileHeader(s.profile, following) { target ->
                            following = target   // 乐观更新,失败回滚
                            scope.launch {
                                val ok = AppState.client.friendship(uid, target)
                                if (!ok) following = !target
                                Toast.makeText(ctx, if (ok) "成功" else "失败",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    item(key = "posts_title") {
                        HorizontalDivider(thickness = 8.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant)
                        Row(Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("TA 的微博", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Text("${s.posts.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (s.posts.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center) {
                                Text("TA 还没有发过微博",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(s.posts, key = { it.mid }) { m ->
                        WeiboCard(m, cardNav)
                        FeedDivider()
                    }
                    item(key = "footer") {
                        ListFooter(if (s.endReached) "— 已经到底啦 —" else "上滑加载更多")
                    }
                }
            }
        }
    }
}

/** 资料头:大头像 + 昵称/认证 + 简介 + 数据三联 + 关注按钮(自己则隐藏) */
@Composable
private fun ProfileHeader(p: WeiboUserProfile, following: Boolean?, onFollow: (Boolean) -> Unit) {
    val isSelf = AppState.session?.uid == p.uid
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatarHd, p.screenName, size = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.screenName.ifBlank { "微博用户" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (p.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CheckCircle, null,
                            Modifier.size(17.dp), tint = Color(0xFFE8821E))
                    }
                }
                p.verifiedReason?.let {
                    Text(it, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = Color(0xFFE8821E))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.gender == "f" || p.gender == "m") {
                        Text(if (p.gender == "f") "女" else "男",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 5.dp, vertical = 1.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    val loc = p.location
                    if (loc.isNullOrBlank()) return@Row
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(loc, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (p.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(p.description, style = MaterialTheme.typography.bodyMedium,
                lineHeight = 19.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeaderStat(p.friends, "关注")
                HeaderStat(p.followers, "粉丝")
                HeaderStat(p.statuses, "微博")
            }
            if (!isSelf && following != null) {
                if (following) {
                    OutlinedButton(onClick = { onFollow(false) },
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                        Text("已关注", fontSize = 14.sp)
                    }
                } else {
                    Button(onClick = { onFollow(true) },
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp)) {
                        Text("关注", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderStat(value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { }) {
        Text(formatCount(value), fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
