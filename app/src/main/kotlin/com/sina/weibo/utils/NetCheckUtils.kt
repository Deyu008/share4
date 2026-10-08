package com.sina.weibo.utils

import android.content.Context

/**
 * cum 签名桥:复用 patched libwbutil.so(证书守卫已恒真)的原生导出。
 * JNI 符号 Java_com_sina_weibo_utils_NetCheckUtils_getParam 与此类全名精确匹配,
 * 方法名必须为 getParam 且为静态 —— 零 NDK 绑定。
 */
object NetCheckUtils {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("wbutil")
            loaded = true
        }
    }

    /** path 形如 "/2/cardlist?...全参数"(与请求实际发送的 query 串一字不差) */
    fun cum(path: String): String? = try {
        ensureLoaded()
        getParam(null, path)
    } catch (e: Throwable) {
        android.util.Log.w("Share4Cum", "cum fail: ${e.message}")
        null
    }

    @JvmStatic
    external fun getParam(ctx: Context?, path: String): String?
}
