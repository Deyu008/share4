package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboClient
import com.deyu.share4.protocol.WeiboMblog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * X 式时间线 + HyperOS「高级材质」等价玻璃:
 * 列表从悬浮的顶栏/底栏下方穿过,hazeEffect 实时模糊(RenderEffect,API31+,
 * 低版本自动回退半透明)。模糊参数对标系统亚克力:大半径、轻着色、低噪声。
 */
@Composable
fun TimelineScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    val logged = AppState.session != null
    val haze = remember { HazeState() }
    var page by remember { mutableIntStateOf(0) }   // 0=首页 1=搜索 2=通知 3=我
    var webViewMode by remember { mutableStateOf(false) }
    var pageNo by remember { mutableIntStateOf(1) }  // 时间线分页号(实测 page=1/2/3 页间零重叠)
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun load(reset: Boolean = true) {
        scope.launch {
            loading = true; banner = null
            if (reset) { pageNo = 1; endReached = false }
            if (!logged) {
                items = mockTimeline
                banner = "未登录 · 演示数据"
            } else {
                try {
                    if (reset) android.util.Log.d("Share4Tl", "load tab=$tab page=$pageNo")
                    val list = if (tab == 0)
                        AppState.client.friendsTimeline(page = pageNo)
                    else
                        AppState.client.timeline(WeiboClient.CID_HOT, page = pageNo)
                    android.util.Log.d("Share4Tl", "page $pageNo ok: ${list.size} items")
                    items = if (reset) list else {
                        val seen = items.map { it.mid }.toSet()
                        items + list.filter { it.mid !in seen }
                    }
                    if (list.isEmpty()) endReached = true
                    webViewMode = false
                    banner = null
                    if (reset && list.isEmpty()) banner = "时间线为空"
                } catch (e: Exception) {
                    android.util.Log.d("Share4Tl", "native FAIL: ${e.message}")
                    banner = "原生通道失败(${e.message?.take(50)}) · 切换网页模式"
                    webViewMode = true
                }
            }
            loading = false
        }
    }
    LaunchedEffect(tab, logged) { load(true) }

    // 触底自动加载下一页(2026-10-20 实测:page 递增即真分页)
    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loading && !endReached && items.size >= 10 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && logged && page == 0 && tab == 0) {
            pageNo += 1
            load(false)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---------- 内容层:在玻璃栏下方穿过 ----------
        if (webViewMode && page == 0) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(top = 104.dp, bottom = 96.dp),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
                        AppState.session?.webSub?.let { sub ->
                            android.webkit.CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setCookie("https://m.weibo.cn", "SUB=$sub")
                                setCookie("https://weibo.cn", "SUB=$sub")
                                flush()
                            }
                        }
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl("https://m.weibo.cn/")
                    }
                },
            )
        } else if (page != 0) {
            Box(Modifier.fillMaxSize().padding(top = 104.dp)) {
                when (page) {
                    1 -> SearchPage(nav)
                    2 -> NotificationsPage(nav)
                    3 -> MePage(nav)
                }
            }
        } else
            AnimatedContent(targetState = Pair(tab, items), transitionSpec = {
                fadeIn(tween(260)).togetherWith(fadeOut(tween(160)))
            }, label = "tabContent") { (t, list) ->
                if (list.isEmpty() && loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().hazeSource(haze),
                        state = listState,
                        contentPadding = PaddingValues(top = 104.dp, bottom = 96.dp),
                    ) {
                        // 状态条(未登录/错误)
                        banner?.let { b ->
                            item(key = "banner$t") {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    Text(b, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(list, key = { it.mid + t }) { mblog ->
                            WeiboCard(mblog, cardNav, Modifier.animateItem())
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 76.dp))
                        }
                        item {
                            Box(Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center) {
                                Text(
                                    if (!logged) "— 登录后查看真实微博 —"
                                    else if (endReached) "— 已经到底啦 —"
                                    else if (loading) "加载中…"
                                    else "上滑加载更多",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

        // ---------- 玻璃顶栏 ----------
        Box(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .hazeEffect(haze, style = HazeStyle(
                    blurRadius = 32.dp,
                    tint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = 0.62f)),
                    backgroundColor = MaterialTheme.colorScheme.background,
                ))
        ) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.navigate(Routes.PROTOCOL) },
                        modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.Outlined.BugReport, "开发者",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        SegmentedTabs(tab) { if (it != tab) tab = it }
                    }
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ---------- 玻璃底栏 ----------
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .hazeEffect(haze, style = HazeStyle(
                    blurRadius = 32.dp,
                    tint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = 0.62f)),
                    backgroundColor = MaterialTheme.colorScheme.background,
                ))
        ) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars) {
                NavigationBarItem(page == 0, { page = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("首页") })
                NavigationBarItem(page == 1, { page = 1 }, icon = { Icon(Icons.Outlined.Search, null) }, label = { Text("搜索") })
                NavigationBarItem(page == 2, { page = 2 }, icon = { Icon(Icons.Outlined.NotificationsNone, null) }, label = { Text("通知") })
                NavigationBarItem(page == 3, { page = 3 }, icon = { Icon(Icons.Filled.Person, null) }, label = { Text("我") })
            }
        }

        // FAB(悬浮于玻璃底栏上方)
        FloatingActionButton(
            onClick = { nav.navigate(Routes.COMPOSER) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp + 16.dp),
        ) { Icon(Icons.Filled.Edit, "发微博") }
    }
}

@Composable
private fun SegmentedTabs(tab: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabLabel("关注", tab == 0) { onSelect(0) }
        Spacer(Modifier.width(20.dp))
        TabLabel("热门", tab == 1) { onSelect(1) }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(text,
            fontSize = if (selected) 19.sp else 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
