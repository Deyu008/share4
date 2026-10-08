package com.deyu.share.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                            append("→ cardlist 首页(containerid=${com.deyu.share4.protocol.WeiboClient.CID_HOME})…")
                            val raw = client.cardlist(com.deyu.share4.protocol.WeiboClient.CID_HOME)
                            append(raw.take(300))
                        } catch (e: Exception) { append("✗ ${e.message}") }
                        busy = false }
                }) { Text("cardlist") }
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
