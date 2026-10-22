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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.ErrorState
import com.deyu.share.ui.components.FeedDivider
import com.deyu.share.ui.components.ListFooter
import com.deyu.share.ui.components.LoadingState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share.ui.components.formatCount
import com.deyu.share4.protocol.HotWord
import com.deyu.share4.protocol.WeiboClient
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/** 热门 tab · 微博榜:日榜容器 + 无限滚动 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotWeiboList(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageNo by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun load(reset: Boolean = true, pull: Boolean = false) {
        scope.launch {
            if (pull) refreshing = true else loading = true
            error = null
            if (reset) { pageNo = 1; endReached = false }
            try {
                val list = AppState.client.timeline(WeiboClient.CID_HOT, page = pageNo)
                items = if (reset) list else {
                    val seen = items.map { it.mid }.toSet()
                    items + list.filter { it.mid !in seen }
                }
                if (list.isEmpty()) endReached = true
            } catch (e: Exception) {
                error = e.message?.take(60) ?: "加载失败"
            }
            loading = false; refreshing = false
        }
    }
    LaunchedEffect(Unit) { load(true) }

    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loading && !refreshing && !endReached && items.size >= 10 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) { pageNo += 1; load(false) }
    }

    when {
        items.isEmpty() && loading -> LoadingState()
        items.isEmpty() && error != null -> ErrorState(error ?: "", { load(true) })
        items.isEmpty() -> EmptyState("热门暂时没有内容", icon = Icons.Filled.LocalFireDepartment)
        else -> PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load(true, pull = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = listState,
                contentPadding = PaddingValues(bottom = 88.dp)) {
                items(items, key = { it.mid }) { m ->
                    WeiboCard(m, cardNav)
                    FeedDivider()
                }
                item(key = "footer") {
                    ListFooter(if (endReached) "— 已经到底啦 —" else "上滑加载更多")
                }
            }
        }
    }
}

/** 热搜榜组件:名次 + 角标 + 词 + 热度,前三名标红(搜索页与热门 tab 共用) */
@Composable
fun HotSearchBoard(words: List<HotWord>, onWordClick: (String) -> Unit) {
    if (words.isEmpty()) {
        EmptyState(
            text = "热搜暂不可用",
            icon = Icons.Filled.LocalFireDepartment,
            hint = "旧会话可能缺少网页票据,重新登录即可恢复",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "hot_title") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp)
                    .background(Color(0x1AE2413E), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(17.dp),
                        tint = Color(0xFFE2413E))
                }
                Spacer(Modifier.width(8.dp))
                Text("微博热搜", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("实时", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(words.size) { i ->
            val w = words[i]
            val top3 = i < 3
            Row(Modifier.fillMaxWidth()
                .clickable { onWordClick(w.word) }
                .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (top3) Color(0xFFE2413E)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp))
                Text(w.word, fontSize = 15.sp,
                    fontWeight = if (top3) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                w.label?.let { l ->
                    Spacer(Modifier.width(6.dp))
                    Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2413E),
                        modifier = Modifier
                            .background(Color(0x1AE2413E), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp))
                }
                if (w.heat > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(formatCount(w.heat), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (i < words.lastIndex) {
                HorizontalDividerThin(Modifier.padding(start = 44.dp))
            }
        }
    }
}

@Composable
private fun HorizontalDividerThin(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        modifier = modifier)
}
