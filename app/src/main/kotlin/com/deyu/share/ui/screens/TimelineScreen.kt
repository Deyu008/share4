package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
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
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    val logged = AppState.session != null
    val haze = remember { HazeState() }
    var page by remember { mutableIntStateOf(0) }   // 0=首页 1=搜索 2=通知 3=我
    var webViewMode by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true; banner = null
            if (!logged) {
                items = mockTimeline
                banner = "未登录 · 演示数据"
            } else {
                try {
                    val cid = if (tab == 0) WeiboClient.CID_HOME else WeiboClient.CID_HOT
                    android.util.Log.d("Share4Tl", "load tab=$tab cid=$cid cum=${AppState.client.cumProvider != null}")
                    val list = AppState.client.timeline(cid, page = 1)
                    android.util.Log.d("Share4Tl", "native ok: ${list.size} items")
                    items = list
                    webViewMode = false
                    banner = null
                    if (list.isEmpty()) banner = "时间线为空"
                } catch (e: Exception) {
                    android.util.Log.d("Share4Tl", "native FAIL: ${e.message}")
                    banner = "原生通道失败(${e.message?.take(50)}) · 切换网页模式"
                    webViewMode = true
                }
            }
            loading = false
        }
    }
    LaunchedEffect(tab, logged) { load() }

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
                    1 -> SimplePage("搜索", "热搜与全文搜索,即将到来")
                    2 -> SimplePage("通知", "@我的、评论、私信聚合在这里,即将到来")
                    3 -> ProfilePage(nav)
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
                        MblogItem(mblog, nav, Modifier.animateItem())
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 76.dp))
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center) {
                            Text(
                                if (logged) "— 已经到底啦 —" else "— 登录后查看真实微博 —",
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

@Composable
private fun MblogItem(m: WeiboMblog, nav: NavController, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().then(modifier).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Avatar(m.avatar, m.userName)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.userName, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (m.verified) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp),
                        tint = Color(0xFFE8821E))
                }
                Spacer(Modifier.width(6.dp))
                Text("· ${m.createdAt.take(20)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(m.plainText, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            m.retweetedUser?.let { ru ->
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("@$ru", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary)
                        m.retweetedText?.let {
                            Text(stripHtml(it), fontSize = 13.sp, lineHeight = 18.sp,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        if (m.retweetedPics.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp)); MediaGrid(m.retweetedPics) { i ->
                            MediaNav.openPics(m.retweetedPics, i); nav.navigate(Routes.VIEWER) }
                        }
                    }
                }
            }
            if (m.pics.isNotEmpty()) { Spacer(Modifier.height(8.dp)); MediaGrid(m.pics) { i ->
                MediaNav.openPics(m.pics, i); nav.navigate(Routes.VIEWER) } }
            m.videoUrl?.let { vurl ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { MediaNav.openVideo(vurl, m.videoCover, m.videoQualities); nav.navigate(Routes.VIDEO) },
                    modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        if (m.videoCover?.startsWith("http") == true) {
                            AsyncImage(model = m.videoCover, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Icon(Icons.Filled.PlayArrow, null, Modifier.align(Alignment.Center).size(52.dp),
                            tint = Color.White.copy(alpha = 0.95f))
                        Box(Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f))) {
                            Text("视频 · 点击播放", color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Action(Icons.Outlined.ChatBubbleOutline, m.comments, Modifier.weight(1f))
                Action(Icons.Outlined.Repeat, m.reposts, Modifier.weight(1f))
                Action(Icons.Outlined.FavoriteBorder, m.likes, Modifier.weight(1f))
                Action(Icons.Outlined.BarChart, 0, Modifier.weight(1f))
                Action(Icons.Outlined.BookmarkBorder, 0, Modifier.weight(1f), hideZero = true)
            }
        }
    }
}

private fun stripHtml(s: String) = s.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")

@Composable
private fun Avatar(url: String?, fallbackName: String) {
    if (url != null && url.startsWith("http")) {
        AsyncImage(model = url, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Box(Modifier.size(44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Text(fallbackName.take(1), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaGrid(pics: List<String>, onClick: (Int) -> Unit = {}) {
    val h = if (pics.size == 1) 200.dp else 92.dp
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        pics.take(3).forEachIndexed { idx, p ->
            Surface(shape = RoundedCornerShape(14.dp),
                color = gradientFor(p),
                onClick = { onClick(idx) },
                modifier = Modifier.weight(1f).height(h)) {
                if (p.startsWith("http")) {
                    AsyncImage(model = p, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("[图片${idx + 1}]", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun gradientFor(key: String): Color = when (key.hashCode() % 5) {
    0 -> Color(0xFFDDE7ED); 1 -> Color(0xFFE8E3D8); 2 -> Color(0xFFE3E0EC)
    3 -> Color(0xFFE0E9E0); else -> Color(0xFFEDE4E0)
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int,
                   modifier: Modifier = Modifier, hideZero: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
        if (!(count == 0 && hideZero)) {
            Text(formatCount(count), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatCount(c: Int): String = when {
    c >= 100000000 -> "%.1f亿".format(c / 100000000f)
    c >= 10000 -> "%.1f万".format(c / 10000f)
    else -> c.toString()
}

@Composable
private fun SimplePage(title: String, hint: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(140.dp))
        Icon(Icons.Outlined.Construction, null, Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ProfilePage(nav: NavController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val session = AppState.session
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text("我", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        if (session == null) {
            Text("未登录", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                nav.navigate(com.deyu.share.ui.Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }) { Text("去登录") }
        } else {
            Text(session.screenName?.ifBlank { "微博用户 ${session.uid}" } ?: ("微博用户 " + session.uid),
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("uid ${session.uid}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = {
                AppState.logout(ctx)
                nav.navigate(com.deyu.share.ui.Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }) { Text("退出登录") }
        }
    }
}
