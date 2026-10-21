package com.deyu.share.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.deyu.share.data.AppState
import com.deyu.share.ui.MediaNav
import com.deyu.share.ui.Routes
import com.deyu.share4.protocol.VideoQuality
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch
import android.widget.Toast

/** 相对时间(刚刚/N分钟前),见 components/Time.kt */

/**
 * 微博卡片的导航回调集合:卡片本身不持有 NavController,
 * 由各页面注入(详情页可覆写 openDetail 为空实现避免自跳)。
 */
class CardNav(
    val openProfile: (uid: String) -> Unit,
    val openDetail: (mid: String) -> Unit,
    val openPics: (pics: List<String>, index: Int) -> Unit,
    val openVideo: (url: String, cover: String?, qualities: List<VideoQuality>) -> Unit,
)

/** 由 NavController 构造标准导航回调(页面级 remember 一次) */
fun cardNavOf(nav: NavController): CardNav = CardNav(
    openProfile = { uid -> if (uid.isNotBlank()) nav.navigate(Routes.profile(uid)) },
    openDetail = { mid -> if (mid.isNotBlank()) nav.navigate(Routes.detail(mid)) },
    openPics = { pics, index -> MediaNav.openPics(pics, index); nav.navigate(Routes.VIEWER) },
    openVideo = { url, cover, qualities ->
        MediaNav.openVideo(url, cover, qualities); nav.navigate(Routes.VIDEO) },
)

/** 卡片内弹窗模式:评论 / 转发 */
private enum class DialogMode { Comment, Repost }

/**
 * 微博卡片:时间线 / 搜索 / 通知 / 个人页 / 详情页共用。
 * 业务交互(评论、转发、点赞)内聚于此;头像与昵称点击进主页,正文点击进详情。
 */
@Composable
fun WeiboCard(m: WeiboMblog, cardNav: CardNav, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<DialogMode?>(null) }
    Row(Modifier.fillMaxWidth().then(modifier).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Avatar(m.avatar, m.userName, onClick = { cardNav.openProfile(m.userId) })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { cardNav.openDetail(m.mid) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.userName, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                        .clickable { cardNav.openProfile(m.userId) })
                if (m.verified) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp),
                        tint = Color(0xFFE8821E))
                }
                Spacer(Modifier.width(6.dp))
                Text("· ${formatRelativeTime(m.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
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
                            Spacer(Modifier.height(6.dp))
                            MediaGrid(m.retweetedPics) { i -> cardNav.openPics(m.retweetedPics, i) }
                        }
                    }
                }
            }
            if (m.pics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MediaGrid(m.pics) { i -> cardNav.openPics(m.pics, i) }
            }
            m.videoUrl?.let { vurl ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { cardNav.openVideo(vurl, m.videoCover, m.videoQualities) },
                    modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        if (m.videoCover?.startsWith("http") == true) {
                            AsyncImage(model = m.videoCover, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Icon(Icons.Filled.PlayArrow, null,
                            Modifier.align(Alignment.Center).size(52.dp),
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
                CardAction(Icons.Outlined.ChatBubbleOutline, m.comments, Modifier.weight(1f)) {
                    dialog = DialogMode.Comment
                }
                CardAction(Icons.Outlined.Repeat, m.reposts, Modifier.weight(1f)) {
                    dialog = DialogMode.Repost
                }
                LikeAction(m, Modifier.weight(1f))
                CardAction(Icons.Outlined.BarChart, 0, Modifier.weight(1f))
                FavoriteAction(m, Modifier.weight(1f))
            }
            dialog?.let { mode ->
                InteractionDialog(
                    title = if (mode == DialogMode.Comment) "评论" else "转发",
                    hint = if (mode == DialogMode.Comment) "说点什么…" else "转发说点什么…",
                    onDismiss = { dialog = null },
                ) { text ->
                    dialog = null
                    scope.launch {
                        val ok = if (mode == DialogMode.Comment)
                            AppState.client.comment(m.mid, text)
                                .let { it.errno == null || it.errno == 0 }
                        else
                            AppState.client.repost(m.mid, text).isNotEmpty()
                        Toast.makeText(ctx, if (ok) "成功" else "失败",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/** 点赞按钮:本地状态翻转 + like/cancel_like(2026-10-20 实测通过的端点) */
@Composable
private fun LikeAction(m: WeiboMblog, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var liked by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(m.likes) }
    Row(modifier.clickable {
        liked = !liked
        count += if (liked) 1 else -1
        scope.launch {
            val ok = if (liked) AppState.client.like(m.mid) else AppState.client.cancelLike(m.mid)
            if (!ok) { liked = !liked; count += if (liked) 1 else -1 }
        }
    }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null,
            Modifier.size(16.dp),
            tint = if (liked) Color(0xFFE2413E)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
        Text(formatCount(count), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 收藏按钮:初始态取 mblog.favorited,本地翻转 + favorites/create|destroy(2026-10-22 实测) */
@Composable
private fun FavoriteAction(m: WeiboMblog, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var fav by remember { mutableStateOf(m.favorited) }
    Row(modifier.clickable {
        fav = !fav
        scope.launch {
            val ok = if (fav) AppState.client.favoriteCreate(m.mid)
            else AppState.client.favoriteDestroy(m.mid)
            if (!ok) fav = !fav
        }
    }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(
            if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
            Modifier.size(16.dp),
            tint = if (fav) Color(0xFFE8A70B)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
        Text(if (fav) "已收藏" else "收藏", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 评论/转发输入弹窗 */
@Composable
private fun InteractionDialog(title: String, hint: String,
                              onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(hint) }, singleLine = false,
                modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onSend(text.trim()) },
                enabled = text.isNotBlank()) { Text("发送") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        })
}

/** 圆形头像,无图时以首字符占位 */
@Composable
fun Avatar(url: String?, fallbackName: String, size: Dp = 44.dp,
           onClick: (() -> Unit)? = null) {
    val base = Modifier.size(size).clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    if (url != null && url.startsWith("http")) {
        AsyncImage(model = url, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = base)
    } else {
        Box(base, contentAlignment = Alignment.Center) {
            Text(fallbackName.take(1), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 图片九宫格简化版(最多 3 列,单图大图) */
@Composable
fun MediaGrid(pics: List<String>, onClick: (Int) -> Unit = {}) {
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

/** 互动条单个动作(图标 + 计数) */
@Composable
fun CardAction(icon: ImageVector, count: Int, modifier: Modifier = Modifier,
               hideZero: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(modifier.then(if (onClick != null)
            Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
        if (!(count == 0 && hideZero)) {
            Text(formatCount(count), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 图片加载前的渐变底色(按 url 稳定取色) */
private fun gradientFor(key: String): Color = when (key.hashCode() % 5) {
    0 -> Color(0xFFDDE7ED); 1 -> Color(0xFFE8E3D8); 2 -> Color(0xFFE3E0EC)
    3 -> Color(0xFFE0E9E0); else -> Color(0xFFEDE4E0)
}

/** 富文本剥壳:br → 换行,其余标签剔除 */
fun stripHtml(s: String) = s.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")

/** 计数人性化:1.2万 / 3.4亿 */
fun formatCount(c: Int): String = when {
    c >= 100000000 -> "%.1f亿".format(c / 100000000f)
    c >= 10000 -> "%.1f万".format(c / 10000f)
    else -> c.toString()
}
