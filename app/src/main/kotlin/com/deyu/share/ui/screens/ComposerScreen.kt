package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.deyu.share.data.AppState
import com.deyu.share.data.DraftStore
import kotlinx.coroutines.launch

/** 发博编辑器:全屏编辑、配图(开放平台两步流)、关闭时可选存草稿、可从草稿恢复 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(nav: NavController, draftId: Long = 0L) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var askDraft by remember { mutableStateOf(false) }
    var draftLoaded by remember { mutableStateOf(false) }
    val logged = AppState.session != null
    val limit = 2000

    // 从草稿恢复(draftId 非空时载入一次;发送或放弃后草稿删除)
    LaunchedEffect(draftId) {
        if (draftId != 0L && !draftLoaded) {
            DraftStore.list(ctx).firstOrNull { it.id == draftId }?.let { d -> text = d.text }
            draftLoaded = true
        }
    }

    // 图片选择器
    val pickPic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? ->
        uri?.let {
            pickedUri = it
            picked = runCatching {
                ctx.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
            }.getOrNull()
        }
    }

    fun close() { nav.popBackStack() }

    // 内容非空时关闭 → 先问是否存草稿
    fun requestClose() {
        if (text.isNotBlank() || picked != null) askDraft = true else close()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // 顶栏:关闭 / 标题 / 发布
            Row(Modifier.fillMaxWidth().height(52.dp)
                .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { requestClose() }) {
                    Icon(Icons.Filled.Close, "关闭",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(if (draftId != 0L) "编辑草稿" else "发微博",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp))
                AnimatedVisibility(visible = error != null,
                    enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                    Text(error.orEmpty().take(18),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 8.dp))
                }
                Button(
                    enabled = (text.isNotBlank() || picked != null) && !busy && logged,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            if (!logged) {
                                error = "未登录,发布需要先登录"; busy = false; return@launch
                            }
                            val r = if (picked != null) {
                                // 带图:开放平台两步流(mss/upload → pic_id → statuses/upload)
                                AppState.client.postWithImage(
                                    text.trim().ifBlank { "分享图片" }, picked!!)
                            } else {
                                AppState.client.statusesSend(text.trim())
                            }
                            busy = false
                            if (r.errno == null || r.errno == 0) {
                                if (draftId != 0L) DraftStore.remove(ctx, draftId)
                                close()
                            } else {
                                error = "${r.errmsg ?: "发送失败"}(${r.errno})"
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = Color.White, trackColor = Color.Transparent)
                    } else {
                        Text("发布", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = text, onValueChange = { if (it.length <= limit) text = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    textStyle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Box {
                                Text("有什么新鲜事想分享给大家?",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.5f))
                                inner()
                            }
                        } else inner()
                    },
                )

                // 配图预览 + 工具条
                if (pickedUri != null) {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        AsyncImage(model = pickedUri, contentDescription = "已选图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(Modifier.size(22.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                            .clickable { picked = null; pickedUri = null }
                            .align(Alignment.TopEnd)) {
                            Icon(Icons.Filled.Close, "移除图片", Modifier.size(14.dp)
                                .align(Alignment.Center), tint = Color.White)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickPic.launch("image/*") }) {
                        Icon(Icons.Filled.Image, "添加图片",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    if (picked != null) {
                        Text("${picked!!.size / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${text.length} / $limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (text.length > limit - 200)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 底部操作区避开导航栏
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    // 存草稿确认弹窗
    if (askDraft) {
        AlertDialog(
            onDismissRequest = { askDraft = false },
            title = { Text("存入草稿箱?") },
            text = { Text("当前内容尚未发布") },
            confirmButton = {
                TextButton(onClick = {
                    DraftStore.save(ctx, text)
                    close()
                }) { Text("存草稿") }
            },
            dismissButton = {
                TextButton(onClick = { close() }) { Text("不保存") }
            },
        )
    }
}
