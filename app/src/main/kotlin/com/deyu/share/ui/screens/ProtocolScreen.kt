package com.deyu.share.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share4.protocol.WeiboCrypto
import kotlinx.coroutines.launch

/** 协议验证页:真机网络环境下实测协议栈,端到端调试工作台 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val client = AppState.client
    var log by remember {
        mutableStateOf(buildString {
            append("设备: ua=${client.device.ua}\n")
            append("did=${client.device.stableDid()}  androidId=${client.device.androidId}\n")
            append("i=${WeiboCrypto.iValue()}  PIN=${WeiboCrypto.PIN.take(8)}…\n")
            append("会话: ${if (client.session != null) "已登录 uid=${client.session?.uid} gsid=${client.session?.gsid?.take(18)}…" else "未登录(游客)"}\n")
            append("签名模式: s=uid(复刻脱壳包)\n")
        })
    }
    var busy by remember { mutableStateOf(false) }
    var midInput by remember { mutableStateOf("") }
    fun append(s: String) { log = s + "\n" + log }

    Scaffold(topBar = { TopAppBar(title = { Text("协议验证") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true; scope.launch {
                        try { val r = client.guestLogin()
                            append("✓ guest/login aid=${r["aid"]?.take(24)}… uid=${r["uid"]}")
                        } catch (e: Exception) { append("✗ ${e.message}") }
                        busy = false }
                }) { Text("guest/login") }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true; scope.launch {
                        try {
                            append("→ account/login 探针…")
                            val r = client.accountLogin("spike_probe_0001", "WrongProbe!123")
                            append("${if (r.errno == 104) "✓" else "?"} errno=${r.errno} ${r.errmsg}")
                        } catch (e: Exception) { append("✗ ${e.message}") }
                        busy = false }
                }) { Text("登录探针") }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true; scope.launch {
                        try {
                            append("→ 关注流(friends_timeline)…")
                            val list = client.friendsTimeline()
                            append("✓ ${list.size} 条,首条作者:${list.firstOrNull()?.userName ?: "无"}")
                        } catch (e: Exception) { append("✗ ${e.message}") }
                        busy = false }
                }) { Text("关注流") }
            }
            Spacer(Modifier.height(8.dp))
            // 长文探测:statuses/extend 拉完整正文
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = midInput, onValueChange = { midInput = it },
                    label = { Text("mid", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton(enabled = !busy && midInput.isNotBlank(), onClick = {
                    busy = true; scope.launch {
                        try {
                            append("→ statuses/extend ${midInput.trim()}…")
                            val t = client.longText(midInput.trim())
                            append(if (t != null) "✓ 长文 ${t.length} 字\n头:${t.take(50)}\n尾:${t.takeLast(60)}"
                                   else "✗ 无长文返回")
                        } catch (e: Exception) { append("✗ ${e.message}") }
                        busy = false }
                }) { Text("长文") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = log, onValueChange = {},
                modifier = Modifier.fillMaxWidth().weight(1f),
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall
                    .copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
