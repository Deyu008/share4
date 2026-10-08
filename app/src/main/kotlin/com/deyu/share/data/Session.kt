package com.deyu.share.data

import android.content.Context
import com.deyu.share4.protocol.DeviceProfile
import com.deyu.share4.protocol.LoginSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 会话与设备指纹持久化:gsid 恢复免重复登录;
 * androidId 固定保存避免设备身份漂移触发风控。
 */
object SessionStore {
    private const val FILE = "session"
    private const val KEY_SESSION = "login_session_json"
    private const val KEY_ANDROID_ID = "device_android_id"
    private const val KEY_AID = "guest_aid"
    private val json = Json { ignoreUnknownKeys = true }

    fun loadSession(ctx: Context): LoginSession? = runCatching {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null)?.let { json.decodeFromString<LoginSession>(it) }
    }.getOrNull()

    fun saveSession(ctx: Context, s: LoginSession?) {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (s == null) sp.edit().remove(KEY_SESSION).apply()
        else sp.edit().putString(KEY_SESSION, json.encodeToString(s)).apply()
    }

    fun loadAid(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_AID, null)

    fun saveAid(ctx: Context, aid: String?) {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (aid.isNullOrEmpty()) sp.edit().remove(KEY_AID).apply()
        else sp.edit().putString(KEY_AID, aid).apply()
    }

    /** 设备指纹:首次生成后固定 */
    fun loadDeviceProfile(ctx: Context): DeviceProfile {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val saved = sp.getString(KEY_ANDROID_ID, null)
        val androidId = if (saved != null) saved else {
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            sp.edit().putString(KEY_ANDROID_ID, id).apply()
            id
        }
        val man = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL.ifBlank { "MI 9" }
        return DeviceProfile(
            manufacturer = if (man.isBlank()) "Xiaomi" else man,
            model = model,
            osRelease = android.os.Build.VERSION.RELEASE.ifBlank { "13" },
            androidId = androidId,
        )
    }
}
