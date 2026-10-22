package com.deyu.share.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.ErrorState
import com.deyu.share.ui.components.FeedDivider
import com.deyu.share.ui.components.ListFooter
import com.deyu.share.ui.components.LoadingState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/** 我的收藏(仿 StatusFavoriteActivity):收藏微博流,触底翻页 + 下拉刷新 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun load(reset: Boolean = true, pull: Boolean = false) {
        scope.launch {
            if (pull) refreshing = true
            else if (reset) loading = true
            else loadingMore = true
            if (reset) { error = null; page = 1; endReached = false }
            try {
                val list = AppState.client.favorites(page = page)
                items = if (reset) list else {
                    val seen = items.map { it.mid }.toSet()
                    items + list.filter { it.mid !in seen }
                }
                if (list.isEmpty()) endReached = true
            } catch (e: Exception) {
                error = e.message?.take(60) ?: "加载失败"
            }
            loading = false; refreshing = false; loadingMore = false
        }
    }
    LaunchedEffect(Unit) { load(true) }

    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loadingMore && !refreshing && !endReached && items.size >= 20 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) { page += 1; load(false) }
    }

    when {
        loading -> LoadingState()
        error != null && items.isEmpty() -> ErrorState(error ?: "", { load(true) })
        items.isEmpty() -> EmptyState(
            text = "还没有收藏的微博",
            icon = Icons.Filled.Star,
            hint = "在微博卡片上点「收藏」即可保存到这里",
        )
        else -> PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load(true, pull = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)) {
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
