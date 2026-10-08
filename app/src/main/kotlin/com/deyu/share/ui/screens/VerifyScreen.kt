package com.deyu.share.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.deyu.share.data.AppState

/** 验证页跨页传递 */
object VerifyNav {
    var url: String = ""
    var altResult: String? = null
    var seedCookie: String? = null   // 打开前种入的 SUB(weibo.cn 域)
}

/**
 * 风控验证页(passport.weibo.cn/verify/...):WebView + 微博 JSBridge 兼容层。
 * 页面完成验证后调用 loginWithALT action → 解析 alt 回传 → 登录页自动 altLogin。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerifyScreen(nav: NavController) {
    val alt = remember { mutableStateOf<String?>(null) }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(if (VerifyNav.url.contains("m.weibo.cn")) "微博" else "安全验证",
                color = MaterialTheme.colorScheme.onBackground)
        })
    }) { pad ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(pad),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = if (VerifyNav.url.contains("m.weibo.cn"))
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
                    else AppState.client.device.ua
                    VerifyNav.seedCookie?.let { sub ->
                        android.webkit.CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setCookie("https://m.weibo.cn", "SUB=$sub")
                            setCookie("https://weibo.cn", "SUB=$sub")
                            flush()
                        }
                    }
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun invoke() {
                            post { evaluateJavascript("javascript:WeiboJSBridge._messageQueue()", null) }
                        }

                        @JavascriptInterface
                        fun processHTML(a: String, b: String) {}

                        @JavascriptInterface
                        fun transferData(json: String) {
                            if (json.contains("loginWithALT") || json.contains("\"alt\"")) {
                                val m = Regex("\"alt\"\\s*:\\s*\"([^\"]+)\"").find(json)
                                m?.groupValues?.get(1)?.let { a -> post { alt.value = a } }
                            }
                        }
                    }, "WeiboJSBridgeDataTransfer")
                    loadUrl(VerifyNav.url)
                    webRef = this
                }
            },
        )
    }

    LaunchedEffect(alt.value) {
        if (alt.value != null) {
            VerifyNav.altResult = alt.value
            nav.popBackStack()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { webRef?.destroy() }
    }
}
