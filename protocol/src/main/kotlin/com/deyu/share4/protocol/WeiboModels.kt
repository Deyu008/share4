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
    val alt: String? = null,          // 长效免密重登凭据(风控验证页 JSBridge 流才返回)
    val sut: String? = null,          // 登录响应 sut 凭据(备用)
    val phone: String? = null,        // 登录手机号(getoauth 刷新签名用)
    val webSub: String? = null,       // 登录响应 cookie.cookie[".weibo.cn"] 的 SUB(WebView 混合通道)
    val webSubCom: String? = null,    // 登录响应 cookie.cookie[".weibo.com"] 的 SUB(weibo.com ajax 通道:热搜榜)
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
            sut = o.str("sut"),
            webSub = o["cookie"]?.jsonObject?.get("cookie")?.jsonObject
                ?.str(".weibo.cn")?.substringAfter("SUB=")?.substringBefore(";"),
            webSubCom = o["cookie"]?.jsonObject?.get("cookie")?.jsonObject
                ?.str(".weibo.com")?.substringAfter("SUB=")?.substringBefore(";"),
            tokenIssuedAt = o.str("oauth2.0", "issued_at")?.toLongOrNull() ?: 0,
            tokenExpires = o.str("oauth2.0", "expires")?.toLongOrNull() ?: 0,
        )
    }
}

/** 视频清晰度档位(page_info.media_info 多档流) */
@Serializable
data class VideoQuality(val label: String, val url: String)


/** 用户资料(users/show) */
@Serializable
data class WeiboUserProfile(
    val uid: String,
    val screenName: String,
    val avatarHd: String? = null,
    val verified: Boolean = false,
    val verifiedReason: String? = null,
    val description: String = "",
    val followers: Int = 0,
    val friends: Int = 0,
    val statuses: Int = 0,
    val following: Boolean = false,
    val gender: String = "m",
    val location: String? = null,
)

/** 评论(comments/show) */
@Serializable
data class WeiboComment(
    val mid: String,
    val text: String,
    val createdAt: String,
    val userName: String,
    val avatar: String? = null,
    val likeCount: Int = 0,
)

/** 热搜词(weibo.com/ajax/side/hotSearch 的 realtime[]) */
@Serializable
data class HotWord(
    val rank: Int,
    val word: String,
    val heat: Int = 0,
    val label: String? = null,       // "新"/"热"/"爆" 等角标
)

object HotSearchParser {
    fun parse(raw: String): List<HotWord> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val arr = root["data"]?.jsonObject?.get("realtime")?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val word = o.str("word") ?: return@mapNotNull null
            HotWord(
                rank = o.int("rank") ?: 0,
                word = word,
                heat = o.str("num")?.toIntOrNull() ?: 0,
                label = o.str("label_name")?.ifBlank { null },
            )
        }
    }
}

/** 微博分组(groups 的 lists[],含系统组) */
@Serializable
data class WeiboGroup(
    val id: String,
    val name: String,
    val memberCount: Int = 0,
    val system: Boolean = false,
)

/** 私信会话(direct_messages/user_list 的 user_list[]) */
@Serializable
data class WeiboConversation(
    val uid: String,
    val name: String,
    val avatar: String? = null,
    val unread: Int = 0,
    val lastText: String = "",
    val lastAt: String = "",
)

/** 私信消息(conversation / create 返回体) */
@Serializable
data class WeiboDirectMessage(
    val id: String,
    val text: String,
    val createdAt: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String? = null,
)

object DmParser {
    fun parseUsers(raw: String): List<WeiboConversation> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val arr = root["user_list"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val u = o["user"]?.jsonObject ?: return@mapNotNull null
            val last = o["direct_message"]?.jsonObject
            WeiboConversation(
                uid = u.str("idstr") ?: u.str("id") ?: "",
                name = u.str("name") ?: u.str("screen_name") ?: "",
                avatar = u.str("profile_image_url"),
                unread = o.int("unread_count") ?: 0,
                lastText = last?.str("text") ?: "",
                lastAt = last?.str("created_at") ?: "",
            )
        }
    }

    /** conversation 返回最新在前,解析后按调用方需要自行排序 */
    fun parseMessages(raw: String): List<WeiboDirectMessage> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val arr = root["direct_messages"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el -> parseMessage(el.jsonObject) }
    }

    fun parseMessage(o: JsonObject): WeiboDirectMessage? {
        val id = o.str("idstr") ?: o.str("id") ?: return null
        return WeiboDirectMessage(
            id = id,
            text = o.str("text") ?: "",
            createdAt = o.str("created_at") ?: "",
            senderId = o.str("sender_id") ?: "",
            senderName = o.str("sender_screen_name") ?: "",
            senderAvatar = (o["sender"] as? JsonObject)?.str("profile_image_url"),
        )
    }
}

object CommentParser {
    fun parse(raw: String): List<WeiboComment> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val arr = root["data"]?.jsonArray ?: root["comments"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val c = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val u = c["user"]?.jsonObject
            WeiboComment(
                mid = c.str("mid") ?: c.str("id") ?: "",
                text = c.str("text") ?: "",
                createdAt = c.str("created_at") ?: "",
                userName = u?.str("screen_name") ?: "",
                avatar = u?.str("avatar_hd") ?: u?.str("profile_image_url"),
                likeCount = c.int("like_count") ?: 0,
            )
        }
    }
}

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
    val favorited: Boolean = false,
    /** 当前账号是否已赞(部分接口给 attitudes_status/liked,缺省视为未赞) */
    val liked: Boolean = false,
    /**
     * 长文完整正文(mblog.longText.longTextContent)。
     * 2026 服务端已不再下发该字段(原版 Share 的 mblog 模型里有),
     * 完整正文改走 m.weibo.cn/statuses/extend 实时拉取,见 WeiboClient.longText。
     */
    val longText: String? = null,
) {
    /** 展示用正文:长文优先取完整版 */
    val plainText: String get() = (longText?.takeIf { it.isNotBlank() } ?: text)
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
}

object TimelineParser {
    /**
     * 解析 statuses[](friends_timeline / mentions)。
     * 服务端会往关注流里插广告/推荐位(2026-10-21 实测:30 条混 5 条,
     * 标记为 mblogtype≠0 / ad_state≠0 / 携带 promotion 字段),剔除。
     */
    fun parse(raw: String): List<WeiboMblog> {
        val root = WeiboJson.parse(raw) ?: return emptyList()
        val arr = root["statuses"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val injected = (o.int("mblogtype") ?: 0) != 0 ||
                (o.int("ad_state") ?: 0) != 0 || o.containsKey("promotion")
            if (injected) null else CardlistParser.parseMblog(o)
        }
    }
}

object CardlistParser {
    /** media_info 多档流 → 清晰度列表(高到低;m3u8 兜底“自动”)。
     *  2026 热门流实测字段:mp4_720p_mp4 / stream_url(_hd) / h265_* / mp4_*_url 与旧键组并存 */
    private val QUALITY_KEYS = listOf(
        "mp4_1080p_mp4" to "1080P",
        "mp4_720p_mp4" to "720P",
        "h265_mp4_hd" to "高清·H265",
        "mp4_hd_mp4" to "高清",
        "mp4_hd_url" to "高清",
        "mp4_sd_mp4" to "标清",
        "mp4_sd_url" to "标清",
        "h265_mp4_ld" to "流畅·H265",
        "mp4_ld_mp4" to "流畅",
        "stream_url_hd" to "自动高清",
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
            favorited = m.str("favorited")?.toBooleanStrictOrNull() ?: false,
            liked = (m.int("attitudes_status") ?: 0) == 1 ||
                (m.str("liked")?.toBooleanStrictOrNull() ?: false),
            longText = m["longText"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?.str("longTextContent"),
        )
    }
}
