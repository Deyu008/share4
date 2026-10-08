package com.sina.weibo

/**
 * libwbutil 的原生绑定载体(与 so 导出的 Java_com_sina_weibo_WeiboApplication_* 精确匹配)。
 * patched so 的证书守卫已恒真,natives 直接可用。
 */
class WeiboApplication : android.app.Application() {
    companion object {
        @JvmStatic
        external fun getContext(): android.content.Context?
    }

    external fun calculateS(s: String): String?
    external fun newCalculateS(s: String): String?
    external fun getIValue(s: String): String?
    external fun init(s: String)
    external fun getDecryptionString(s: String): String?
    external fun generateCheckToken(s: String, s2: String): String?
    external fun getNetInstance(ctx: android.content.Context, s: String): com.sina.weibo.net.e?
    external fun getNetInstanceFromHotFix(ctx: android.content.Context, s: String, f: java.io.File, s2: String, s3: String, s4: String): com.sina.weibo.net.e?
}
