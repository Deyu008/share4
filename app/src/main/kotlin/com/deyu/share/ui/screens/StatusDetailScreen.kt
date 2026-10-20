package com.deyu.share.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.Avatar
import com.deyu.share.ui.components.CardNav
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share.ui.components.formatCount
import com.deyu.share.ui.components.formatRelativeTime
import com.deyu.share.ui.components.stripHtml
import com.deyu.share4.protocol.WeiboComment
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** 微博详情 UI 状态 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Ready(
        val mblog: WeiboMblog,
        val comments: List<WeiboComment>,
        val endReached: Boolean = false,
    ) : DetailUiState
}

/**
 * 微博详情:正文卡片(cardlist mid 容器)+ 评论流(comments/show 分页)
 * + 底部评论框(comments/create,成功后本地置顶)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDetailScreen(mid: String, nav: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // 详情页里的卡片再点“详情”会自跳,置空;其余导航照常
    val cardNav = remember {
        val base = cardNavOf(nav)
        CardNav(
            openProfile = base.openProfile,
            openDetail = {},
            openPics = base.openPics,
            openVideo = base.openVideo,
        )
    }
    var state by remember { mutableStateOf<DetailUiState>(DetailUiState.Loading) }
    var page by remember { mutableStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            state = DetailUiState.Loading
            try {
                val detail = async { AppState.client.statusDetail(mid) }
                val comments = async { runCatching { AppState.client.comments(mid) }
                    .getOrDefault(emptyList()) }
                val m = detail.await() ?: throw IllegalStateException("微博不存在或已删除")
                state = DetailUiState.Ready(m, comments.await(), comments.await().isEmpty())
            } catch (e: Exception) {
                state = DetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }
    LaunchedEffect(mid) { load() }

    fun loadMoreComments() {
        val ready = state as? DetailUiState.Ready ?: return
        if (loadingMore || ready.endReached) return
        loadingMore = true
        scope.launch {
            val nextPage = page + 1
            val more = runCatching { AppState.client.comments(mid, nextPage) }
                .getOrDefault(emptyList())
            page = nextPage
            state = ready.copy(
                comments = ready.comments +
                    more.filter { n -> n.mid !in ready.comments.map { it.mid } },
                endReached = more.isEmpty(),
            )
            loadingMore = false
        }
    }

    fun sendComment() {
        val text = commentDraft.trim()
        val ready = state as? DetailUiState.Ready ?: return
        if (text.isBlank() || sending) return
        sending = true
        scope.launch {
            val r = runCatching { AppState.client.commentCreate(mid, text) }
                .getOrNull()
            val ok = r != null && (r.errno == null || r.errno == 0)
            if (ok) {
                val me = AppState.session
                state = ready.copy(comments = listOf(WeiboComment(
                    mid = mid, text = text, createdAt = "刚刚",
                    userName = me?.screenName ?: "我",
                    avatar = me?.avatarHd)) + ready.comments)
                commentDraft = ""
            }
            Toast.makeText(ctx, if (ok) "评论成功" else (r?.errmsg ?: "评论失败"),
                Toast.LENGTH_SHORT).show()
            sending = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("微博正文") },
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
        bottomBar = {
            if (state is DetailUiState.Ready) {
                Row(Modifier.fillMaxWidth().imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentDraft, onValueChange = { commentDraft = it },
                        placeholder = { Text("评论…") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { sendComment() },
                        enabled = commentDraft.isNotBlank() && !sending) {
                        Icon(Icons.Filled.Send, "发送")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DetailUiState.Loading -> Box(Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                }
                is DetailUiState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { load() }) { Text("重试") }
                }
                is DetailUiState.Ready -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val shouldLoadMore by androidx.compose.runtime.derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        !loadingMore && !s.endReached && s.comments.size >= 20 &&
                            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) loadMoreComments()
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
                        item(key = "detail") {
                            WeiboCard(s.mblog, cardNav)
                            if (s.mblog.source.isNotBlank()) {
                                Text("来自 ${s.mblog.source}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 68.dp, bottom = 6.dp))
                            }
                            HorizontalDivider(thickness = 8.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        item(key = "cmt_title") {
                            Text("评论 ${formatCount(s.mblog.comments)}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                        if (s.comments.isEmpty()) {
                            item(key = "cmt_empty") {
                                Box(Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text("暂无评论,抢首评",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(s.comments, key = { it.mid + it.hashCode() }) { c ->
                            CommentItem(c)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 68.dp))
                        }
                        item(key = "cmt_footer") {
                            Box(Modifier.fillMaxWidth().padding(14.dp),
                                contentAlignment = Alignment.Center) {
                                Text(if (s.endReached) "— 已经到底啦 —" else "上滑加载更多",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 评论条目:小头像 + 昵称 + 时间 + 内容 + 赞数 */
@Composable
private fun CommentItem(c: WeiboComment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Avatar(c.avatar, c.userName, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.userName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text("· ${formatRelativeTime(c.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(stripHtml(c.text), style = MaterialTheme.typography.bodyMedium)
            if (c.likeCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text("赞 ${formatCount(c.likeCount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
