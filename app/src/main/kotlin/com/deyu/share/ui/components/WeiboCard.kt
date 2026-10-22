package com.deyu.share.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

/** 正文超过这个长度才折叠(约 6 行),避免短微博也被塞一个「全文」 */
private const val COLLAPSE_AT = 200

/**
 * 微博卡片:时间线 / 搜索 / 通知 / 个人页 / 详情页共用。
 * 业务交互(评论、转发、点赞、收藏)内聚于此;头像与昵称点击进主页,正文点击进详情。
 */
@Composable
fun WeiboCard(m: WeiboMblog, cardNav: CardNav, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<DialogMode?>(null) }
    var expanded by remember { mutableStateOf(false) }
    // 服务端只给截断文本的长微博:点「查看全文」再拉完整正文
    var fullText by remember { mutableStateOf<String?>(null) }
    var loadingLong by remember { mutableStateOf(false) }
    // 正文来源优先级:已拉取的完整版 > mblog 自带 longText > 时间线里的 text
    val rawText = fullText ?: m.longText ?: m.text
    val bodyText = stripHtml(rawText)
    val longText = bodyText.length > COLLAPSE_AT
    val truncatedByServer = m.isLongText && m.longText.isNullOrBlank() && fullText == null

    Row(Modifier.fillMaxWidth().then(modifier).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Avatar(m.avatar, m.userName, onClick = { cardNav.openProfile(m.userId) })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            // 昵称行单独可点进主页;正文区整体可点进详情
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
                Text(formatRelativeTime(m.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(3.dp))

            Column(Modifier.fillMaxWidth().clickable { cardNav.openDetail(m.mid) }
                .animateContentSize()) {
                Text(bodyText, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (longText && !expanded) 6 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis)
                if (longText) {
                    Text(if (expanded) "收起" else "展开全文",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                            .clickable { expanded = !expanded })
                } else if (truncatedByServer) {
                    // 服务端只给了截断摘要:点「查看全文」再拉完整正文
                    Text(if (loadingLong) "正在加载全文…" else "查看全文",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp).clickable {
                            if (loadingLong) return@clickable
                            loadingLong = true
                            scope.launch {
                                val t = runCatching { AppState.client.longText(m.mid, m.id) }
                                    .getOrNull()
                                if (t != null) { fullText = t; expanded = true }
                                loadingLong = false
                            }
                        })
                }
                m.retweetedUser?.let { ru ->
                    Spacer(Modifier.height(7.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(m.retweetedAvatar, ru, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("@$ru", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            m.retweetedText?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(stripHtml(it), fontSize = 13.sp, lineHeight = 19.sp,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            if (m.retweetedPics.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                MediaGrid(m.retweetedPics) { i ->
                                    cardNav.openPics(m.retweetedPics, i)
                                }
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
                    VideoCard(cover = m.videoCover, qualities = m.videoQualities) {
                        cardNav.openVideo(vurl, m.videoCover, m.videoQualities)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ActionRow(m, onComment = { dialog = DialogMode.Comment },
                onRepost = { dialog = DialogMode.Repost })
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

/** 互动条:评论 / 转发 / 赞 / 收藏 四等分 + 右侧更多(复制/链接),仿原版分布 */
@Composable
private fun ActionRow(m: WeiboMblog, onComment: () -> Unit, onRepost: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val link = "https://m.weibo.cn/detail/${m.mid}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CardAction(Icons.Outlined.ChatBubbleOutline, m.comments, Modifier.weight(1f), onClick = onComment)
        CardAction(Icons.Outlined.Repeat, m.reposts, Modifier.weight(1f), onClick = onRepost)
        LikeAction(m, Modifier.weight(1f))
        FavoriteAction(m, Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreHoriz, "更多", Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("复制正文") }, onClick = {
                    menu = false
                    clipboard.setText(AnnotatedString(m.plainText))
                    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                })
                DropdownMenuItem(text = { Text("复制链接") }, onClick = {
                    menu = false
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                })
                DropdownMenuItem(text = { Text("浏览器打开") }, onClick = {
                    menu = false
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                })
            }
        }
    }
}

/** 卡片之间的分隔线:左侧留出头像列宽,与原版列表一致 */
@Composable
fun FeedDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        modifier = modifier.padding(start = 68.dp))
}

/** 点赞按钮:本地状态翻转 + like/cancel_like(2026-10-20 实测通过的端点) */
@Composable
private fun LikeAction(m: WeiboMblog, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var liked by remember { mutableStateOf(m.liked) }
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
            color = if (liked) Color(0xFFE2413E)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (fav) {
            Text("已收藏", style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE8A70B))
        }
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
            Text(fallbackName.take(1), fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 图片网格(仿微博九宫格):单图大图,2/4 张两列,其余三列,最多 9 张。
 * 超出 9 张时第 9 格覆盖「+N」。
 */
@Composable
fun MediaGrid(pics: List<String>, onClick: (Int) -> Unit = {}) {
    val shown = pics.take(9)
    if (shown.isEmpty()) return
    val cols = when (shown.size) {
        1 -> 1
        2, 4 -> 2
        else -> 3
    }
    val single = shown.size == 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.chunked(cols).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (single) Modifier.fillMaxWidth(0.78f) else Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIdx, p ->
                    val idx = rowIdx * cols + colIdx
                    val cell = if (single) Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                               else Modifier.weight(1f).aspectRatio(1f)
                    Box(cell.clip(RoundedCornerShape(if (single) 12.dp else 6.dp))
                        .background(gradientFor(p))
                        .clickable { onClick(idx) }) {
                        if (p.startsWith("http")) {
                            AsyncImage(model = p, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("[图片${idx + 1}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (idx == 8 && pics.size > 9) {
                            Box(Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center) {
                                Text("+${pics.size - 9}", color = Color.White,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // 末行不足列数时补空位,避免最后一张被拉宽
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 视频封面:16:9 卡片 + 居中播放键;封面缺失时用渐变兜底而不是一片白 */
@Composable
private fun VideoCard(cover: String?, qualities: List<VideoQuality>, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(12.dp))
        .background(gradientFor(cover ?: "video"))
        .clickable(onClick = onClick)) {
        if (cover != null && cover.startsWith("http")) {
            AsyncImage(model = cover, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // 底部压暗,保证角标可读
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
        Box(Modifier.align(Alignment.Center).size(54.dp)
            .background(Color.Black.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "播放", Modifier.size(32.dp), tint = Color.White)
        }
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("视频", color = Color.White, fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
            if (qualities.size > 1) {
                Spacer(Modifier.width(6.dp))
                Text("${qualities.size} 档清晰度", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

/** 互动条单个动作(图标 + 计数),图标左对齐以保持五列视觉节奏 */
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

/** 富文本剥壳:br → 换行,标签剔除,常见实体还原 */
fun stripHtml(s: String): String = s
    .replace(Regex("<br\\s*/?>"), "\n")
    .replace(Regex("</?p[^>]*>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }

/** 计数人性化:1.2万 / 3.4亿 */
fun formatCount(c: Int): String = when {
    c >= 100000000 -> "%.1f亿".format(c / 100000000f)
    c >= 10000 -> "%.1f万".format(c / 10000f)
    else -> c.toString()
}
