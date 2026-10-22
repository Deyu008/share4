package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.ListFooter
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share.ui.theme.MaterialFab
import com.deyu.share.ui.theme.edgeLineColor
import com.deyu.share.ui.theme.glassPanel
import com.deyu.share.ui.theme.topEdgeLine
import com.deyu.share4.protocol.WeiboGroup
import com.deyu.share4.protocol.WeiboMblog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val cardNav = remember(nav) { cardNavOf(nav) }
    var tab by remember { mutableIntStateOf(0) }          // 0=微博 1=消息 2=热门
    // 重复点击当前 tab 时自增,作为刷新信号(用计数器而非布尔,连续点两次也能触发)
    var refreshTick by remember { mutableIntStateOf(0) }
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
        // 内容区单独作为 haze 源,底栏玻璃靠它取样(底栏是它的兄弟节点,不是后代)
        val contentHazeState = remember { HazeState() }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // ---------- 顶栏(玻璃面板,模糊弥散光背景,延伸进状态栏后面) ----------
                Box(Modifier.fillMaxWidth()
                    .glassPanel()
                    .topEdgeLine(edgeLineColor())) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp),
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
                }

                // ---------- 内容 ----------
                Box(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxSize().hazeSource(contentHazeState)) {
                        if (tab == 1) {
                            NotificationsPage(nav)
                        } else if (tab == 2) {
                            HotBoardPage(nav)
                        } else {
                            WeiboFeedTab(nav, cardNav, refreshTick)
                        }
                    }

                    // FAB(悬浮右下,同原版“首页FAB按钮”)
                    MaterialFab(
                        onClick = { nav.navigate(Routes.COMPOSER) },
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 18.dp, bottom = 72.dp),
                    ) { Icon(Icons.Filled.Edit, "发微博", tint = Color.White) }
                }
            }

            // ---------- 底栏(玻璃,浮在内容之上,能模糊滚过的微博) ----------
            HomeBottomBar(
                tabs = tabs, current = tab,
                hazeState = contentHazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { idx ->
                if (tab == idx && SettingsStore.tapTabToRefresh) refreshTick++
                tab = idx
            }
        }
    }
}

/** 自定义底栏:玻璃面板 + 图标/文案,选中态整列变主题蓝 */
@Composable
private fun HomeBottomBar(
    tabs: List<Triple<Int, String, ImageVector>>,
    current: Int,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Box(modifier.fillMaxWidth()
        .glassPanel(state = hazeState)
        .topEdgeLine(edgeLineColor())) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp)) {
            tabs.forEach { (idx, label, icon) ->
                val selected = current == idx
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(180), label = "tabTint")
                Column(Modifier.weight(1f).fillMaxSize()
                    .clickable { onSelect(idx) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(icon, label, Modifier.size(23.dp), tint = tint)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 11.sp, color = tint,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** 微博 tab:全部(关注)/ 热门 / 分组 三类流,顶部分组条横向滚动 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeiboFeedTab(nav: NavController,
                         cardNav: com.deyu.share.ui.components.CardNav,
                         refreshTick: Int) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<WeiboGroup>>(emptyList()) }
    // feedKind: 0=全部(关注) 1=热门 2=分组;groupId 在 kind==2 时生效
    var feedKind by remember { mutableIntStateOf(0) }
    var groupId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var pageNo by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val logged = AppState.session != null

    LaunchedEffect(Unit) {
        groups = runCatching { AppState.client.groups() }.getOrDefault(emptyList())
    }

    fun load(reset: Boolean = true, pull: Boolean = false) {
        scope.launch {
            if (pull) refreshing = true else loading = true
            banner = null
            if (reset) { pageNo = 1; endReached = false }
            if (!logged) {
                items = mockTimeline
                banner = "未登录 · 演示数据"
            } else {
                try {
                    val list = when (feedKind) {
                        0 -> AppState.client.friendsTimeline(page = pageNo)
                        1 -> AppState.client.timeline(
                            com.deyu.share4.protocol.WeiboClient.CID_HOT, page = pageNo)
                        else -> AppState.client.groupTimeline(groupId, page = pageNo)
                    }
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
                    banner = "加载失败:${e.message?.take(40)}"
                }
            }
            loading = false; refreshing = false
        }
    }
    // 切流时回到顶部,避免沿用上一条流的滚动位置
    LaunchedEffect(feedKind, groupId, logged, refreshTick) {
        listState.scrollToItem(0)
        load(true)
    }

    val shouldLoadMore by derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        !loading && !refreshing && !endReached && items.size >= 10 &&
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
        LazyRow(Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp)) {
            item(key = "all") { FeedTabLabel("全部", feedKind == 0) { feedKind = 0 } }
            item(key = "hot") { FeedTabLabel("热门", feedKind == 1) { feedKind = 1 } }
            items(groups, key = { it.id }) { g ->
                FeedTabLabel(g.name, feedKind == 2 && groupId == g.id) {
                    feedKind = 2; groupId = g.id
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        if (items.isEmpty() && loading) {
            com.deyu.share.ui.components.LoadingState()
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { load(true, pull = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (items.isEmpty()) {
                    // 空列表也要可下拉:用可滚动的空态容器
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item {
                            Column(Modifier.fillParentMaxSize()) {
                                EmptyState(
                                    text = banner ?: "这里还没有内容",
                                    icon = Icons.Filled.Home,
                                    hint = if (!logged) "登录后查看关注的人发的微博" else null,
                                    action = if (!logged) "去登录" else "刷新",
                                    onAction = {
                                        if (!logged) nav.navigate(Routes.LOGIN)
                                        else load(true, pull = true)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(state = listState,
                        // 底部留出 FAB 与底栏高度,最后一条不被遮挡
                        contentPadding = PaddingValues(bottom = 88.dp)) {
                        banner?.let { b ->
                            item(key = "banner") {
                                Box(Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    Text(b, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(items, key = { it.mid }) { mblog ->
                            WeiboCard(mblog, cardNav, Modifier.animateItem())
                        }
                        item(key = "footer") {
                            ListFooter(
                                when {
                                    !logged -> "— 登录后查看真实微博 —"
                                    endReached -> "— 已经到底啦 —"
                                    loading -> "加载中…"
                                    else -> "上滑加载更多"
                                })
                        }
                    }
                }
            }
        }
    }
}

/** 分组/榜单切换标签:选中加粗变蓝,下方一条短指示条 */
@Composable
private fun FeedTabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant, tween(160), label = "feedTabColor")
    Column(Modifier.clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = if (selected) 16.sp else 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(2.5.dp).width(if (selected) 18.dp else 0.dp)
            .background(color, RoundedCornerShape(2.dp)))
    }
}

/** 热门 tab:微博榜 / 热搜榜 子切换 */
@Composable
private fun HotBoardPage(nav: NavController) {
    var sub by remember { mutableIntStateOf(0) }   // 0=微博榜 1=热搜榜
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FeedTabLabel("微博榜", sub == 0) { sub = 0 }
            FeedTabLabel("热搜榜", sub == 1) { sub = 1 }
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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

/** 抽屉(仿原版侧栏):账号头(点击切换/添加账号)+ 入口清单 */
@Composable
private fun ShareDrawerSheet(nav: NavController) {
    val ctx = LocalContext.current
    val session = AppState.session
    var accounts by remember { mutableStateOf(AppState.accounts(ctx)) }
    var showSwitcher by remember { mutableStateOf(false) }

    // 当前会话与列表不一致(登录页 add 过)时刷新
    LaunchedEffect(session?.uid) {
        accounts = AppState.accounts(ctx)
    }

    if (showSwitcher) {
        AlertDialog(
            onDismissRequest = { showSwitcher = false },
            title = { Text("切换账号") },
            text = {
                Column {
                    accounts.forEach { acc ->
                        Row(Modifier.fillMaxWidth()
                            .clickable {
                                AppState.switchAccount(acc.uid, ctx)
                                showSwitcher = false
                            }
                            .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Avatar(acc.avatarHd, acc.screenName ?: "微", size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(acc.screenName ?: "微博用户",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (acc.uid == session?.uid) FontWeight.Bold
                                                 else FontWeight.Normal)
                            }
                            if (acc.uid == session?.uid) {
                                Icon(Icons.Filled.CheckCircle, "当前",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (accounts.size < com.deyu.share.data.AccountStore.MAX_ACCOUNTS) {
                        Row(Modifier.fillMaxWidth()
                            .clickable {
                                showSwitcher = false
                                nav.navigate(Routes.LOGIN)
                            }
                            .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("添加账号", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSwitcher = false }) { Text("关闭") }
            },
        )
    }

    ModalDrawerSheet {
        Column(Modifier.fillMaxWidth()) {
            // 账号头(点击弹出账号切换;未登录显示登录入口)
            Row(Modifier.fillMaxWidth().clickable {
                if (session != null) showSwitcher = true
                else nav.navigate(Routes.LOGIN)
            }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(session?.avatarHd, session?.screenName ?: "客", size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(session?.screenName ?: "未登录",
                        style = MaterialTheme.typography.titleMedium)
                    session?.let {
                        Text("uid ${it.uid}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (accounts.size > 1) {
                        Text("共 ${accounts.size} 个账号,点击切换",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
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
                    AppState.logout(session.uid, ctx)
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
