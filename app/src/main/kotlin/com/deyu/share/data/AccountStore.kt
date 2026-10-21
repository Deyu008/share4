package com.deyu.share.data

import android.content.Context
import com.deyu.share4.protocol.LoginSession
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 多账号仓储(高级版解锁功能,上限 10 个,对齐原版 AccountManageActivity)。
 * 会话列表按 uid 去重;"当前账号"沿用 SessionStore 的单会话字段,保持启动路径兼容。
 */
object AccountStore {
    private const val FILE = "accounts"
    private const val KEY_LIST = "session_list"
    private const val KEY_CURRENT = "current_uid"
    const val MAX_ACCOUNTS = 10
    private val json = Json { ignoreUnknownKeys = true }

    fun list(ctx: Context): List<LoginSession> = runCatching {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null)?.let {
                json.decodeFromString(ListSerializer(LoginSession.serializer()), it)
            }
    }.getOrNull().orEmpty()

    fun currentUid(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_CURRENT, null)

    fun add(ctx: Context, s: LoginSession) {
        val rest = list(ctx).filter { it.uid != s.uid }
        // 原版规则:满 10 个需先退出一个
        if (rest.size >= MAX_ACCOUNTS) return
        persist(ctx, rest + s, s.uid)
    }

    fun switch(ctx: Context, uid: String): LoginSession? {
        val target = list(ctx).firstOrNull { it.uid == uid } ?: return null
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_CURRENT, uid).apply()
        // 保持 SessionStore 的"当前会话"字段同步(启动恢复路径用它)
        SessionStore.saveSession(ctx, target)
        return target
    }

    fun remove(ctx: Context, uid: String) {
        val rest = list(ctx).filter { it.uid != uid }
        val cur = currentUid(ctx)
        persist(ctx, rest, if (cur == uid) rest.firstOrNull()?.uid else cur)
        if (cur == uid) SessionStore.saveSession(ctx, rest.firstOrNull())
    }

    private fun persist(ctx: Context, accounts: List<LoginSession>, current: String?) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, json.encodeToString(accounts))
            .putString(KEY_CURRENT, current)
            .apply()
    }
}
