package com.sina.weibo.data.sp

/** libwbutil JNI_OnLoad 签名校验所需占位 */
class EncryptSharedPreferences {
    companion object {
        @JvmStatic
        external fun loadSpFile(ctx: android.content.Context, s: String): ByteArray?
        @JvmStatic
        external fun saveSpFile(ctx: android.content.Context, s: String, b: ByteArray)
    }
}
