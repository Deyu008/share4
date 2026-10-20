package com.deyu.share.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 微博时间戳 → 原版风格的相对时间(刚刚 / N分钟前 / N小时前 / 昨天 / M月d日 / yyyy-M-d)。
 * 输入格式为微博 API 的 "Tue Oct 20 20:23:20 +0800 2026";解析失败时原样返回前 20 字符。
 */
private val parser = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)

fun formatRelativeTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val t = try { parser.parse(raw.trim())?.time ?: return raw.take(20) } catch (e: Exception) {
        return raw.take(20)
    }
    val diff = (System.currentTimeMillis() - t) / 1000L
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 172800 -> "昨天"
        diff < 2592000 -> {
            val d = Date(t)
            "${SimpleDateFormat("M月d日", Locale.CHINA).format(d)}"
        }
        else -> {
            val d = Date(t)
            SimpleDateFormat("yyyy-M-d", Locale.CHINA).format(d)
        }
    }
}
