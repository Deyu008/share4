package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.data.SettingsStore
import com.deyu.share.ui.Routes
import com.deyu.share.ui.components.Avatar
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/**
 * 主壳,结构仿原版 StatusActivity:
 * 底栏「微博 / 消息 / 热门」(热门可在设置隐藏)+ 顶栏(抽屉/搜索/发博)
 * + FAB 发博 + 左侧抽屉(账号、设置、Share高级版、关于、退出)。
 * 交互:重复点击当前 tab 刷新(可在设置关闭,同原版同名开关)。
 */
@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cardNav = remember { cardNavOf(nav) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var tab by remember { mutableIntStateOf(0) }          // 0=微博 1=消息 2=热门
    var lastTab by remember { mutableIntStateOf(-1) }
    val tabs = remember(SettingsStore.hideHotTab) {
        listOfNotNull(
            Triple(0, "微博", Icons.Filled.Home as ImageVector),
            Triple(1, "消息", Icons.Filled.Notifications),
            if (!SettingsStore.hideHotTab) Triple(2, "热门", Icons.Filled.LocalFireDepartment) else null,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = { ShareDrawerSheet(nav) },
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()) {
            // ---------- 顶栏 ----------
            Row(Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { drawer.open() } }) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, "菜单",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    when {
                        tab == 1 -> "消息"
                        tab == 2 -> "热门"
                        else -> "Share"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = { nav.navigate(Routes.search()) }) {
                    Icon(Icons.Filled.Search, "搜索",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { nav.navigate(Routes.COMPOSER) },
                    modifier = Modifier.padding(end = 6.dp)) {
                    Icon(Icons.Filled.Edit, "发微博",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---------- 内容 ----------
            Box(Modifier.weight(1f)) {
                if (tab == 1) {
                    NotificationsPage(nav)
                } else if (tab == 2) {
                    HotBoardPage(nav)
                } else {
                    WeiboFeedTab(nav, cardNav,
                        tapRefreshSignal = if (tab == lastTab && tab == 0) 1 else 0)
                }

                // FAB(悬浮右下,同原版“首页FAB按钮”)
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.COMPOSER) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp),
                ) { Icon(Icons.Filled.Edit, "发微博") }
            }

            // ---------- 底栏 ----------
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp) {
                tabs.forEach { (idx, label, icon) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = {
                            // 重复点击当前 tab → 刷新(原版“当重复点击首页的tab时刷新”)
                            if (tab == idx && SettingsStore.tapTabToRefresh && idx == 0) {
                                lastTab = -1   // 置信号,触发 WeiboFeedTab 重载
                            }
                            tab = idx
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

/** 微博 tab:全部(关注)/ 热门 / 分组 三类流,顶部分组条横向滚动 */
@Composable
private fun WeiboFeedTab(nav: NavController, cardNav: com.deyu.share.ui.components.CardNav,
                         tapRefreshSignal: Int) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<com.deyu.share4.protocol.WeiboGroup>>(emptyList()) }
    // feedKind: 0=全部(关注) 1=热门 2=分组;groupId 在 kind==2 时生效
    var feedKind by remember { mutableIntStateOf(0) }
    var groupId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var pageNo by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val logged = AppState.session != null

    LaunchedEffect(Unit) {
        groups = runCatching { AppState.client.groups() }.getOrDefault(emptyList())
    }

    fun load(reset: Boolean = true) {
        scope.launch {
            loading = true; banner = null
            if (reset) { pageNo = 1; endReached = false }
            if (!logged) {
                items = mockTimeline
                banner = "未登录 · 演示数据"
            } else {
                try {
                    val list = when (feedKind) {
                        0 -> AppState.client.friendsTimeline(page = pageNo)
                        1 -> AppState.client.timeline(com.deyu.share4.protocol.WeiboClient.CID_HOT,
                            page = pageNo)
                        else -> AppState.client.groupTimeline(groupId, page = pageNo)
                    }
                    android.util.Log.d("Share4Tl", "feed=$feedKind/$groupId page $pageNo ok: ${list.size}")
                    items = if (reset) list else {
                        val seen = items.map { it.mid }.toSet()
                        items + list.filter { it.mid !in seen }
                    }
                    if (list.isEmpty()) endReached = true
                    if (reset && list.isEmpty()) banner = when (feedKind) {
                        0 -> "还没有关注的人发微博"
                        1 -> "时间线为空"
                        else -> "分组暂无微博"
                    }
                } catch (e: Exception) {
                    android.util.Log.d("Share4Tl", "feed FAIL: ${e.message}")
                    banner = "加载失败:${e.message?.take(40)}"
                }
            }
            loading = false
        }
    }
    LaunchedEffect(feedKind, groupId, logged, tapRefreshSignal) { load(true) }

    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loading && !endReached && items.size >= 10 &&
            info.totalItemsCount > 5 && last >= info.totalItemsCount - 3
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && logged && feedKind <= 1) {
            pageNo += 1
            load(false)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部分组条:全部 / 热门 / 用户分组(横向滚动,原版为「全部+自定义分组」)
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            item(key = "all") { FeedTabLabel("全部", feedKind == 0) { feedKind = 0 } }
            item(key = "hot") { FeedTabLabel("热门", feedKind == 1) { feedKind = 1 } }
            items(groups, key = { it.id }) { g ->
                FeedTabLabel(g.name, feedKind == 2 && groupId == g.id) {
                    feedKind = 2; groupId = g.id
                }
            }
        }
        if (items.isEmpty() && loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                banner?.let { b ->
                    item(key = "banner") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(b, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(items, key = { it.mid }) { mblog ->
                    WeiboCard(mblog, cardNav, Modifier.animateItem())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 76.dp))
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center) {
                        Text(if (!logged) "— 登录后查看真实微博 —"
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
}

@Composable
private fun FeedTabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp)) {
        Text(text,
            fontSize = if (selected) 17.sp else 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 热门 tab:微博榜 / 热搜榜 子切换 */
@Composable
private fun HotBoardPage(nav: NavController) {
    var sub by remember { mutableIntStateOf(0) }   // 0=微博榜 1=热搜榜
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
            FeedTabLabel("微博榜", sub == 0) { sub = 0 }
            FeedTabLabel("热搜榜", sub == 1) { sub = 1 }
            Spacer(Modifier.weight(1f))
        }
        AnimatedContent(targetState = sub, transitionSpec = {
            fadeIn(tween(240)).togetherWith(fadeOut(tween(160)))
        }, label = "hotSub") { s ->
            if (s == 0) HotWeiboList(nav) else HotSearchPane(nav)
        }
    }
}

/** 热搜榜面板:加载词榜,点词进搜索 */
@Composable
private fun HotSearchPane(nav: NavController) {
    var hotWords by remember { mutableStateOf<List<com.deyu.share4.protocol.HotWord>>(emptyList()) }
    LaunchedEffect(Unit) {
        hotWords = runCatching { AppState.client.hotSearch() }.getOrDefault(emptyList())
    }
    HotSearchBoard(hotWords) { word -> nav.navigate(Routes.search(word)) }
}

/** 抽屉(仿原版侧栏):账号头 + 入口清单 */
@Composable
private fun ShareDrawerSheet(nav: NavController) {
    val ctx = LocalContext.current
    val session = AppState.session
    ModalDrawerSheet {
        Column(Modifier.fillMaxWidth()) {
            // 账号头(未登录显示登录入口)
            Row(Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(session?.avatarHd, session?.screenName ?: "客",
                    size = 52.dp,
                    onClick = {
                        session?.uid?.let {
                            nav.navigate(Routes.profile(it))
                        } ?: nav.navigate(Routes.LOGIN)
                    })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(session?.screenName ?: "未登录",
                        style = MaterialTheme.typography.titleMedium)
                    session?.let {
                        Text("uid ${it.uid}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DrawerItem(Icons.Filled.Person, "我的主页") {
                session?.uid?.let { nav.navigate(Routes.profile(it)) }
            }
            DrawerItem(Icons.Filled.WorkspacePremium, "Share 高级版") {
                nav.navigate(Routes.PREMIUM)
            }
            DrawerItem(Icons.Filled.Settings, "设置") {
                nav.navigate(Routes.SETTINGS)
            }
            DrawerItem(Icons.Filled.Star, "我的收藏") {
                nav.navigate(Routes.FAVORITES)
            }
            DrawerItem(Icons.Filled.Edit, "草稿箱") {
                nav.navigate(Routes.DRAFTS)
            }
            DrawerItem(Icons.Outlined.Info, "关于") {
                nav.navigate(Routes.ABOUT)
            }
            if (session != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DrawerItem(Icons.Outlined.Logout, "退出登录") {
                    AppState.logout(ctx)
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}
