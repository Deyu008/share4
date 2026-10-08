package com.deyu.share.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.deyu.share4.protocol.VideoQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 现代播放器:自定义 Compose 控制层
 * - 点按:隐藏/显示 chrome;双击左/中/右:-10s / 播放暂停 / +10s
 * - 长按:3x 倍速(松开恢复,徽标提示)
 * - 清晰度:底部选择器,切换保持进度
 */
@Composable
fun VideoScreen(nav: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val qualities = MediaNav.qualities.ifEmpty {
        MediaNav.videoUrl.takeIf { it.isNotEmpty() }?.let { listOf(VideoQuality("自动", it)) } ?: emptyList()
    }
    var qi by remember { mutableIntStateOf(0) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(qualities.firstOrNull()?.url ?: ""))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---------- 播放状态轮询 ----------
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var chrome by remember { mutableStateOf(true) }
    var speedHold by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<Int?>(null) }   // -10 / +10 提示
    var showQuality by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            buffering = player.playbackState == ExoPlayer.STATE_BUFFERING
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 1L
            buffered = player.bufferedPosition
            delay(400)
        }
    }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(700); seekHint = null } }
    LaunchedEffect(chrome, isPlaying) { if (chrome && isPlaying) { delay(3500); chrome = false } }

    fun togglePlay() {
        player.playWhenReady = !player.playWhenReady
        chrome = true
    }
    fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
        seekHint = (deltaMs / 1000).toInt()
        chrome = true
    }
    fun switchQuality(index: Int) {
        if (index == qi) { showQuality = false; return }
        val pos = player.currentPosition
        val wasPlaying = player.playWhenReady
        qi = index
        player.setMediaItem(MediaItem.fromUri(qualities[index].url), pos)
        player.prepare()
        player.playWhenReady = wasPlaying
        showQuality = false
        chrome = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ---------- 视频层 ----------
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false      // 控制层全自定义
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ---------- 手势层 ----------
        val scope = rememberCoroutineScope()
        var holdJob by remember { mutableStateOf<Job?>(null) }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { chrome = !chrome },
                    onDoubleTap = { offset ->
                        when {
                            offset.x < size.width * 0.33f -> seekBy(-10_000)
                            offset.x > size.width * 0.67f -> seekBy(10_000)
                            else -> togglePlay()
                        }
                    },
                    onLongPress = {
                        speedHold = true
                        player.playbackParameters = PlaybackParameters(3f)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (speedHold) {
                            speedHold = false
                            player.playbackParameters = PlaybackParameters(1f)
                        }
                        holdJob?.cancel()
                    },
                )
            }
        )

        // ---------- 缓冲 ----------
        if (buffering && !speedHold) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
            }
        }

        // ---------- 3x 倍速徽标 ----------
        AnimatedVisibility(visible = speedHold, enter = fadeIn(tween(120)), exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)) {
            Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(10.dp)) {
                Text("3.0x 倍速播放中", color = Color(0xFFFF9E2C), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }

        // ---------- 双击快进提示 ----------
        AnimatedVisibility(visible = seekHint != null, enter = fadeIn(tween(100)), exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape) {
                Text(if ((seekHint ?: 0) > 0) "+${seekHint}s" else "${seekHint}s",
                    color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }

        // ---------- 中心播放/暂停(带缩放动画) ----------
        AnimatedVisibility(
            visible = chrome && !buffering && !speedHold,
            enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.7f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(onClick = { togglePlay() }, shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.size(66.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, Modifier.size(38.dp), tint = Color.White)
                }
            }
        }

        // ---------- 顶部 chrome ----------
        AnimatedVisibility(visible = chrome, enter = fadeIn(tween(180)), exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopStart)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.Close, "关闭", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                // 清晰度选择入口
                Surface(onClick = { showQuality = true },
                    color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, null, Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(qualities.getOrNull(qi)?.label ?: "自动",
                            color = Color.White, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    activity?.requestedOrientation =
                        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }) {
                    Icon(Icons.Filled.Fullscreen, "全屏", tint = Color.White)
                }
            }
        }

        // ---------- 底部 chrome:进度条 ----------
        AnimatedVisibility(visible = chrome, enter = fadeIn(tween(180)), exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomStart)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp)) {
                Slider(
                    value = (position.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { player.seekTo((it * duration).toLong()); chrome = true },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFE8821E),
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                )
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text("${fmt(position)} / ${fmt(duration)}", color = Color.White, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // ---------- 清晰度选择器 ----------
        if (showQuality) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { showQuality = false } })
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding().padding(vertical = 10.dp)) {
                    Text("清晰度", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    qualities.forEachIndexed { index, q ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { switchQuality(index) }
                                .padding(horizontal = 18.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(q.label, fontSize = 15.sp,
                                color = if (index == qi) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            if (index == qi) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
