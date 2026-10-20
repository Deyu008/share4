package com.deyu.share.data

import android.content.Context
import com.deyu.share4.protocol.WeiboClient
import com.deyu.share4.protocol.LoginSession

/** 单例共享:协议客户端 + 当前会话 */
object AppState {
    lateinit var client: WeiboClient
        private set

    fun init(ctx: Context) {
        if (::client.isInitialized) return
        val device = SessionStore.loadDeviceProfile(ctx)
        client = WeiboClient(device)
        client.restoreSession(SessionStore.loadSession(ctx))
        client.aid = SessionStore.loadAid(ctx)
        client.onAidChanged = { SessionStore.saveAid(ctx, it) }
        // cum 桥:纯 Kotlin 实现(零 IV SHA-256,2026-10 破解)——与签名无关,任何证书可用
        client.cumProvider = { path ->
            val c = com.deyu.share4.protocol.WeiboCrypto.cum(path)
            android.util.Log.d("Share4Cum", "cum=$c path=$path")
            c
        }
    }

    val session: LoginSession? get() = client.session

    fun login(s: LoginSession, ctx: Context) {
        client.restoreSession(s)
        SessionStore.saveSession(ctx, s)
    }

    fun logout(ctx: Context) {
        client.restoreSession(null)
        SessionStore.saveSession(ctx, null)
    }
}
