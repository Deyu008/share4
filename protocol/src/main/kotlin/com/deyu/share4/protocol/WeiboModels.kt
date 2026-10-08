package com.deyu.share4.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** account/login 成功响应(字段名对齐旧包解析:gsid/userinfo/crossdomainurls/oauth) */
@Serializable
data class LoginSession(
    val gsid: String,
    val uid: String,
    val screenName: String? = null,
    val avatarHd: String? = null,
    val oauthToken: String? = null,
    val alt: String? = null,          // 长效免密重登凭据(WeiboWebAuthorizeActivity alt 流)
    val phone: String? = null,        // 登录手机号(getoauth 刷新签名用)
    val webSub: String? = null,       // 登录响应 cookie.cookie[".weibo.cn"] 的 SUB(WebView 混合通道)
    val tokenIssuedAt: Long = 0,      // oauth2.0 token 签发时间(秒)
    val tokenExpires: Long = 0,       // 有效期(秒,登录响应 expires)
)

/** 顶层扩展:JSON 便捷取值(供 WeiboClient / CardlistParser 共用) */
fun JsonObject.str(vararg keys: String): String? {
    var node: kotlinx.serialization.json.JsonElement? = this
    for (k in keys) {
        node = (node as? JsonObject)?.get(k) ?: return null
    }
    return node?.jsonPrimitive?.contentOrNull
}

fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

/** guest/login 或 login_sendcode 的通用 JSON 读取 */
object WeiboJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): JsonObject? =
        try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { null }

    fun parseLogin(text: String): LoginSession? {
        val o = parse(text) ?: return null
        val gsid = o.str("gsid") ?: o.str("userinfo", "gsid") ?: return null
        return LoginSession(
            gsid = gsid,
            uid = o.str("uid") ?: o.str("userinfo", "uid") ?: o.str("userinfo", "id") ?: "",
            screenName = o.str("screen_name") ?: o.str("userinfo", "screen_name"),
            avatarHd = o.str("userinfo", "avatar_hd") ?: o.str("userinfo", "profile_image_url"),
            oauthToken = o.str("oauth2.0", "access_token") ?: o.str("oauth", "access_token"),
            alt = o.str("alt"),
            webSub = o["cookie"]?.jsonObject?.get("cookie")?.jsonObject
                ?.str(".weibo.cn")?.substringAfter("SUB=")?.substringBefore(";"),
            tokenIssuedAt = o.str("oauth2.0", "issued_at")?.toLongOrNull() ?: 0,
            tokenExpires = o.str("oauth2.0", "expires")?.toLongOrNull() ?: 0,
        )
    }
}

/** 视频清晰度档位(page_info.media_info 多档流) */
@Serializable
data class VideoQuality(val label: String, val url: String)

/** cardlist → 扁平微博卡片(字段对齐 cardlist mblog JSON) */
@Serializable
data class WeiboMblog(
    val mid: String,
    val id: String? = null,
    val createdAt: String = "",
    val text: String = "",
    val source: String = "",
    val userName: String = "",
    val userId: String = "",
    val avatar: String? = null,
    val verified: Boolean = false,
    val verifiedType: Int = -1,
    val pics: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoCover: String? = null,
    val videoQualities: List<VideoQuality> = emptyList(),
    val reposts: Int = 0,
    val comments: Int = 0,
    val likes: Int = 0,
    val retweetedText: String? = null,
    val retweetedUser: String? = null,
    val retweetedAvatar: String? = null,
    val retweetedPics: List<String> = emptyList(),
    val isLongText: Boolean = false,
) {
    val plainText: String get() = text
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
}

object TimelineParser {
    /** 解析 api.weibo.com/2/statuses/home_timeline.json 的 statuses[] */
    fun parse(raw: String): List<WeiboMblog> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val arr = root["statuses"]?.jsonArray ?: return emptyList()
        return arr.map { CardlistParser.parseMblog(it.jsonObject) }
    }
}

object CardlistParser {
    /** media_info 多档流 → 清晰度列表(高到低;m3u8 兜底"自动") */
    private val QUALITY_KEYS = listOf(
        "mp4_1080p_mp4" to "1080P",
        "mp4_720p_mp4" to "720P",
        "mp4_hd_mp4" to "高清",
        "mp4_sd_mp4" to "标清",
        "mp4_ld_mp4" to "流畅",
        "stream_url" to "自动",
    )

    fun videoQualitiesOf(pageInfo: JsonObject): List<VideoQuality> {
        val mi = pageInfo["media_info"]?.jsonObject ?: return emptyList()
        return QUALITY_KEYS.mapNotNull { (k, label) ->
            mi.str(k)?.let { VideoQuality(label, it) }
        }.distinctBy { it.url }
    }
    /** 解析 cardlist 的 cards[](card_type 9=mblog,11=转发外壳;mblog 直接嵌在 card 里也兼容) */
    fun parse(raw: String): List<WeiboMblog> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val cards = root["cards"]?.jsonArray ?: return emptyList()
        val out = ArrayList<WeiboMblog>()
        for (c in cards) {
            val co = c.jsonObject
            val m = co["mblog"]?.jsonObject ?: continue
            out.add(parseMblog(m))
        }
        return out
    }

    fun parseMblog(m: JsonObject): WeiboMblog {
        fun pics(node: JsonObject?): List<String> =
            node?.get("pics")?.jsonArray
                ?.mapNotNull { it.jsonObject["large"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
                ?: emptyList()
        val rt = m["retweeted_status"]?.jsonObject
        val user = m["user"]?.jsonObject
        val vt = user?.get("verified_type")?.jsonPrimitive?.intOrNull ?: -1
        return WeiboMblog(
            mid = m.str("mid") ?: m.str("id") ?: "",
            id = m.str("id") ?: m.str("mid"),
            createdAt = m.str("created_at") ?: "",
            text = m.str("text") ?: "",
            source = (m.str("source") ?: "").replace(Regex("</?a[^>]*>"), ""),
            userName = user?.str("screen_name") ?: "",
            userId = user?.str("id") ?: "",
            avatar = user?.str("avatar_hd") ?: user?.str("profile_image_url"),
            verified = (user?.str("verified")?.toBooleanStrictOrNull() ?: false),
            verifiedType = vt,
            pics = pics(m),
            videoUrl = m["page_info"]?.jsonObject?.let { mi -> videoQualitiesOf(mi).firstOrNull()?.url },
            videoCover = m["page_info"]?.jsonObject?.str("page_pic", "url"),
            videoQualities = m["page_info"]?.jsonObject?.let { videoQualitiesOf(it) } ?: emptyList(),
            reposts = m.int("reposts_count") ?: 0,
            comments = m.int("comments_count") ?: 0,
            likes = m.int("attitudes_count") ?: 0,
            retweetedText = rt?.str("text"),
            retweetedUser = rt?.get("user")?.jsonObject?.str("screen_name"),
            retweetedAvatar = rt?.get("user")?.jsonObject?.str("profile_image_url"),
            retweetedPics = pics(rt),
            isLongText = m.str("isLongText")?.toBooleanStrictOrNull() ?: false,
        )
    }
}
