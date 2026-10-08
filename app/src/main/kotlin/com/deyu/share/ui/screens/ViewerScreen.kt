package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * 全屏图片查看器:左右翻页、双指缩放、双击放大、单击隐藏 chrome、页码指示。
 * 放大状态下禁用 pager 滚动(手势让位给 pan)。
 */
@Composable
fun ViewerScreen(nav: NavController) {
    val pics = MediaNav.pics
    val pagerState = rememberPagerState(initialPage = MediaNav.startIndex) { pics.size }
    var chrome by remember { mutableStateOf(true) }
    val zoomed = remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed.value,   // 放大时手势归 pan
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableAsyncImage(
                url = pics.getOrElse(page) { "" },
                onTap = { chrome = !chrome },
                onZoomChanged = { zoomed.value = it },
            )
        }

        // chrome:关闭 + 页码
        AnimatedVisibility(visible = chrome && pics.size > 0,
            enter = fadeIn(tween(180)), exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopStart)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.Close, "关闭", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                    Text("${pagerState.currentPage + 1} / ${pics.size}",
                        color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun ZoomableAsyncImage(url: String, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val animScale = remember { Animatable(1f) }
    val animTx = remember { Animatable(0f) }
    val animTy = remember { Animatable(0f) }

    fun sync() { scale = animScale.value; tx = animTx.value; ty = animTy.value }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(url) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val old = scale
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) {
                        // 以 centroid 为锚缩放 + 平移
                        val sx = scale / old
                        tx = tx * sx + (pan.x + (1 - sx) * centroid.x)
                        ty = ty * sx + (pan.y + (1 - sx) * centroid.y)
                        // 平移边界(±(scale-1)*size/2)
                        val maxX = (size.width * (scale - 1)) / 2f
                        val maxY = (size.height * (scale - 1)) / 2f
                        tx = tx.coerceIn(-maxX, maxX)
                        ty = ty.coerceIn(-maxY, maxY)
                    } else {
                        tx = 0f; ty = 0f
                    }
                    onZoomChanged(scale > 1f)
                }
            }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scope.launch {
                                animScale.snapTo(scale); animTx.snapTo(tx); animTy.snapTo(ty)
                                animScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                                animTx.animateTo(0f, tween(200))
                                animTy.animateTo(0f, tween(200))
                                sync(); onZoomChanged(false)
                            }
                        } else {
                            scope.launch {
                                animScale.snapTo(1f); animTx.snapTo(0f); animTy.snapTo(0f)
                                animScale.animateTo(2.6f, spring(dampingRatio = 0.7f, stiffness = 380f))
                                sync(); onZoomChanged(true)
                            }
                        }
                    },
                )
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = tx; translationY = ty
                },
        )
    }
}
