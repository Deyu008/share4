package com.deyu.share.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share.ui.components.formatCount
import com.deyu.share4.protocol.HotWord
import com.deyu.share4.protocol.WeiboClient
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/** 热门 tab · 微博榜:日榜容器 + 无限滚动 */
@Composable
fun HotWeiboList(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageNo by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun load(reset: Boolean = true) {
        scope.launch {
            loading = true; error = null
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
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(true) }

    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loading && !endReached && items.size >= 10 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) { pageNo += 1; load(false) }
    }

    when {
        items.isEmpty() && loading -> Box(Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.5.dp)
        }
        items.isEmpty() && error != null -> Box(Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.TextButton(onClick = { load(true) }) { Text("重试") }
            }
        }
        else -> LazyColumn(state = listState,
            contentPadding = PaddingValues(bottom = 12.dp)) {
            items(items, key = { it.mid }) { m ->
                WeiboCard(m, cardNav)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 76.dp))
            }
            item(key = "footer") {
                Box(Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center) {
                    Text(if (endReached) "— 已经到底啦 —" else "上滑加载更多",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 热搜榜组件:名次 + 角标 + 词 + 热度,前三名标红(搜索页与热门 tab 共用) */
@Composable
fun HotSearchBoard(words: List<HotWord>, onWordClick: (String) -> Unit) {
    if (words.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("热搜不可用(旧会话缺少网页票据,重新登录即可)",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "hot_title") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFE2413E))
                Spacer(Modifier.width(6.dp))
                Text("微博热搜", style = MaterialTheme.typography.titleMedium)
            }
        }
        items(words.size) { i ->
            val w = words[i]
            Row(Modifier.fillMaxWidth()
                .clickable { onWordClick(w.word) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (i < 3) Color(0xFFE2413E)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(30.dp))
                Text(w.word, fontSize = 15.sp,
                    fontWeight = if (i < 3) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                w.label?.let { l ->
                    Spacer(Modifier.width(6.dp))
                    Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2413E),
                        modifier = Modifier
                            .background(Color(0x1AE2413E),
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp))
                }
                Spacer(Modifier.weight(1f))
                if (w.heat > 0) {
                    Text(formatCount(w.heat), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (i < words.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 46.dp))
            }
        }
    }
}
