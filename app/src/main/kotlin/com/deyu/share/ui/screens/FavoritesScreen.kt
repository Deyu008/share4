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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/** 我的收藏(仿 StatusFavoriteActivity):收藏微博流,触底翻页 */
@Composable
fun FavoritesScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun load(reset: Boolean = true) {
        scope.launch {
            if (reset) { loading = true; error = null; page = 1 } else loadingMore = true
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
            loading = false; loadingMore = false
        }
    }
    LaunchedEffect(Unit) { load(true) }

    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loadingMore && !endReached && items.size >= 20 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) { page += 1; load(false) }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }
            error != null && items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = { load(true) }) { Text("重试") }
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center) {
                Text("还没有收藏的微博", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                items(items, key = { it.mid }) { m ->
                    WeiboCard(m, cardNav)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}
