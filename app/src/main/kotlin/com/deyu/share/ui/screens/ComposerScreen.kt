package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import kotlinx.coroutines.launch

/** X 式发博:全屏编辑、顶部极简 chrome、右下角计数、发布成功后关闭 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val logged = AppState.session != null
    val limit = 2000

    fun close() { nav.popBackStack() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) {
                    Icon(Icons.Filled.Close, "关闭",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = error != null,
                    enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                    Text(error.orEmpty().take(22),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 8.dp))
                }
                Button(
                    enabled = text.isNotBlank() && !busy && logged,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            if (!logged) { error = "未登录,发布需要先登录"; busy = false; return@launch }
                            val r = AppState.client.statusesSend(text.trim())
                            busy = false
                            if (r.errno == null || r.errno == 0) close()
                            else error = "${r.errmsg ?: "发送失败"}(${r.errno})"
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = Color.White, trackColor = Color.Transparent)
                    else Text("发布", fontWeight = FontWeight.SemiBold)
                }
            }

            BasicTextField(
                value = text, onValueChange = { if (it.length <= limit) text = it },
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                textStyle = TextStyle(fontSize = 19.sp, lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Column {
                            Text("有什么新鲜事?", fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            inner()
                        }
                    } else inner()
                },
            )
        }
    }

    // 底部字符计数(右下角,随内容浮现)
    Box(Modifier.fillMaxSize()) {
        Text(
            "${text.length} / $limit",
            style = MaterialTheme.typography.labelSmall,
            color = if (text.length > limit - 200) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
                .navigationBarsPadding(),
        )
    }
}
