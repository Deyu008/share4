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
        // 旧版本升级迁移:只有单会话字段时并入多账号列表
        if (AccountStore.list(ctx).isEmpty()) {
            SessionStore.loadSession(ctx)?.let { AccountStore.add(ctx, it) }
        }
        // cum 桥:纯 Kotlin 实现(零 IV SHA-256,2026-10 破解)——与签名无关,任何证书可用
        client.cumProvider = { path ->
            val c = com.deyu.share4.protocol.WeiboCrypto.cum(path)
            android.util.Log.d("Share4Cum", "cum=$c path=$path")
            c
        }
    }

    val session: LoginSession? get() = client.session

    /** 已登录账号列表(高级版功能,上限 10) */
    fun accounts(ctx: Context): List<LoginSession> = AccountStore.list(ctx)

    fun login(s: LoginSession, ctx: Context) {
        client.restoreSession(s)
        SessionStore.saveSession(ctx, s)
        AccountStore.add(ctx, s)
    }

    /** 切换账号(抽屉账号切换;未登录态切换无效果) */
    fun switchAccount(uid: String, ctx: Context): Boolean {
        val target = AccountStore.switch(ctx, uid) ?: return false
        client.restoreSession(target)
        return true
    }

    /** 退出指定账号(当前账号退出时自动落到列表中下一个,无则登出态) */
    fun logout(uid: String?, ctx: Context) {
        if (uid == null || uid == AccountStore.currentUid(ctx)) {
            client.restoreSession(null)
            SessionStore.saveSession(ctx, null)
            AccountStore.remove(ctx, uid ?: AccountStore.currentUid(ctx).orEmpty())
        } else {
            AccountStore.remove(ctx, uid)
        }
    }

    fun logout(ctx: Context) {
        client.restoreSession(null)
        SessionStore.saveSession(ctx, null)
    }
}
