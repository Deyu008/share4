package com.deyu.share4.protocol

import java.security.MessageDigest
import java.util.UUID

/**
 * 设备画像。忠实复刻 Share 发送的设备身份参数;
 * did/android_id 首次生成后由调用方持久化,避免每次变化触发风控。
 */
data class DeviceProfile(
    val manufacturer: String = "Xiaomi",
    val model: String = "MI 9",
    val osRelease: String = "13",
    val androidId: String = UUID.randomUUID().toString().replace("-", "").take(16),
    val did: String = "",
) {
    /** 16 位 hex did(与 DeviceId.getDeviceIdCustom 同形态;由 androidId 派生保持稳定) */
    fun stableDid(): String {
        if (did.isNotEmpty()) return did
        return md5HexOf("share4-" + androidId).take(16)
    }

    private fun md5HexOf(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    /** C6879sB.O000000o:UA 头(空格保留) */
    val ua: String
        get() = "$manufacturer-$model" + "__weibo__11.6.0__android__android" + osRelease

    /** umtt 参数 = 完整 UA 串 */
    val umtt: String get() = ua

    /** RO.O00000Oo():device_name */
    val deviceName: String get() = "$manufacturer-$model"
}
