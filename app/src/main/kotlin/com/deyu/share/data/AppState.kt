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
        // cum 桥:patched libwbutil(证书守卫恒真)
        client.cumProvider = { path ->
            val c = com.sina.weibo.utils.NetCheckUtils.cum(path)
            android.util.Log.d("Share4Cum", "cum=" + c + " path=" + path)
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
