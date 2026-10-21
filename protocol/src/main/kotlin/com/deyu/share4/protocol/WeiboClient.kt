package com.deyu.share4.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 微博 m-api 客户端。基址 https://api.weibo.cn/2/,gsid 会话制。
 * 端点与参数布局见 phase0/PROTOCOL.md(全部逆向自 Share 3.9.5)。
 */
class WeiboClient(
    val device: DeviceProfile = DeviceProfile(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        const val API_BASE = "https://api.weibo.cn/2/"
        const val FROM = "10B6095010"        // Share 伪装 appkey(C7292xB)
        const val APPKEY_AID = "7501641714"  // guest/login 的 appkey(C6714qB)
        const val AID_FROM = "1299295010"    // weicoabroad 分支 from(C6714qB.O00000o0)
        const val KEY_HASH = "7d3d1dd67023b838aa54241aea6fe799" // md5(Share 原证书 DER)
        const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0"

        /** 主页关注流(2026-10-21 纠错:不走 cardlist,见 friendsTimeline) */
        const val CID_HOT = "102803_ctg1_8999_-_ctg1_8999_home"
        /** 热榜入口(仅壳,无内容流) */
        const val CID_HOTLIST = "230413"
        /** 旧版首页 containerid(服务端 -200,已废弃,勿用) */
        const val CID_HOME_OLD = "107603"

        /** 发送/验码是否业务成功(errno 0 或缺失均视为成功) */
        fun ApiResult.businessOk(): Boolean = errno == null || errno == 0
    }

    /** cum 签名提供方(app 层注入 patched libwbutil 桥);null=不可用 */
    var cumProvider: (suspend (String) -> String?)? = null

    var aid: String? = null
        set(value) { field = value; onAidChanged?.invoke(value) }
    var guestGsid: String? = null
    var guestUid: String? = null

    /** aid 变化回调(AppState 用它持久化;aid 必须随请求发送,丢失会被 -105) */
    var onAidChanged: ((String?) -> Unit)? = null

    private suspend fun ensureAid() { if (aid == null) guestLogin() }

    var session: LoginSession? = null    // 真实登录会话
        private set


    fun restoreSession(s: LoginSession?) { session = s }

    // ---------- 公共参数(C7292xB.O000000o) ----------
    fun commonParams(authed: Boolean = false): LinkedHashMap<String, String> {
        val p = LinkedHashMap<String, String>()
        p["lang"] = "zh_CN"
        p["networktype"] = "wifi"
        p["c"] = "android"
        p["from"] = FROM
        p["wm"] = "2468_1001"
        p["oldwm"] = if (device.manufacturer.uppercase().contains("XIAOMI")) "20005_0002" else "3333_1001"
        p["umtt"] = device.umtt
        p["v_p"] = "89"
        p["com_ver"] = ""
        p["wb_version"] = "5005"
        p["skin"] = "default"
        p["v_f"] = "2"
        if (authed) {
            session?.let {
                p["gsid"] = it.gsid
                // s = newCalculateS(uid):sha512 反馈索引(2026-10 破解,服务端实测通过)
                p["s"] = WeiboCrypto.sApi(it.uid)
                p["i"] = WeiboCrypto.iValue()
            }
        }
        aid?.let { p["aid"] = it }         // Mz 拦截器对所有请求附加
        return p
    }

    private fun buildUrl(endpoint: String, params: Map<String, String>): String {
        val url = (API_BASE + endpoint).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> url.addQueryParameter(k, v) }
        return url.build().toString()
    }

    private fun baseRequestBuilder(): Request.Builder {
        val b = Request.Builder()
            .header("User-Agent", device.ua)
            // 不手动设 Accept-Encoding:OkHttp 透明 gzip 需由它自己插入该头,手动设会拿到未解压字节
            .header("X-Log-Uid", (session?.uid ?: guestUid) ?: "")
            .header("X-Sessionid", UUID.randomUUID().toString())
        return b
    }

    private suspend inline fun execText(req: Request): String = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    // ---------- aid 注册(实测 200) ----------
    suspend fun guestLogin(): Map<String, String?> = withContext(Dispatchers.IO) {
        val query = commonParams()
        val mfp = "01" + WeiboCrypto.rsaEncryptB64(genMfpJson(), WeiboCrypto.RSA_PUBKEY_MFP)
        val body = FormBody.Builder()
            .add("did", device.stableDid())
            .add("device_name", device.deviceName)
            .add("checktoken", WeiboCrypto.checktoken(device.stableDid()))
            .add("key_hash", KEY_HASH)
            .add("appkey", APPKEY_AID)
            .add("mfp", mfp)
            .add("packagename", "com.hengye.share")
            .build()
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("guest/login", query)).post(body).build()
        ).execute()
        val obj = WeiboJson.parse(resp.body!!.string()) ?: return@withContext emptyMap()
        aid = obj.str("aid")
        guestGsid = obj.str("gsid")
        guestUid = obj.str("uid")
        mapOf("aid" to aid, "gsid" to guestGsid, "uid" to guestUid)
    }

    /** mfp:数字键 1..20 设备指纹 JSON(DO.O000000o) */
    private fun genMfpJson(): String {
        val m = linkedMapOf(
            "1" to "Android " + device.osRelease,
            "2" to device.manufacturer,
            "3" to device.model,
            "7" to "",
            "10" to device.androidId,
            "13" to "zh", "14" to "CN",
            "15" to "56623104000",
            "17" to "", "18" to "02:00:00:00:00:00",
            "19" to "wifi",
            "20" to device.ua,
        )
        return m.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
    }

    // ---------- 登录结果 ----------
    data class ApiResult(
        val httpCode: Int,
        val errno: Int?,
        val errmsg: String?,
        val raw: String,
        val session: LoginSession? = null,
        val sms: SmsChallenge? = null,
        val errurl: String? = null,   // 风控/验证页(8599 等),原包开 WebView 完成后经 JSBridge 拿 alt
    ) {
        val ok get() = session != null
        val identityPassed get() = errno != -105
    }

    /** login_sendcode 返回的验码上下文 */
    data class SmsChallenge(val phone: String, val area: String,
                            val code: String, val number: String, val retcode: String?)

    private fun parseResult(code: Int, text: String, sms: SmsChallenge? = null): ApiResult {
        // 登录响应原始抓包:确认 alt 字段真身(持久化后 token 可无限续期 → 图传即通)
        // protocol 为纯 JVM 模块,println 会以 System.out 形式进 logcat
        if (text.contains("gsid") || text.contains("access_token")) {
            println("Share4Login raw (${text.length}B): ${text.take(1200)}")
        }
        val obj = WeiboJson.parse(text)
        return ApiResult(
            httpCode = code,
            errno = obj?.int("errno") ?: obj?.int("errcode"),
            errmsg = obj?.str("errmsg") ?: obj?.str("msg"),
            raw = text,
            session = WeiboJson.parseLogin(text),
            sms = sms,
            errurl = obj?.str("errurl"),
        )
    }

    // ---------- 账号密码登录(weicoabroad 分支,实测过身份校验) ----------
    suspend fun accountLogin(user: String, password: String): ApiResult = withContext(Dispatchers.IO) {
        val q = LinkedHashMap<String, String>()
        q["c"] = "weicoabroad"
        q["i"] = WeiboCrypto.iValue()
        q["s"] = WeiboCrypto.sOld(user + password)
        q["u"] = user
        q["p"] = WeiboCrypto.rsaEncryptB64(password, WeiboCrypto.RSA_PUBKEY_PWD)
        q["getuser"] = "1"; q["getoauth"] = "1"; q["getcookie"] = "1"
        q["lang"] = "zh_CN_#Hans"
        aid?.let { q["aid"] = it }
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("account/login", q))
                .post("".toRequestBody(null)).build()
        ).execute()
        val r = parseResult(resp.code, resp.body!!.string())
        if (r.session != null) session = r.session
        r
    }

    // ---------- 短信发码(实测协议通过) ----------
    suspend fun smsSendCode(phone: String, area: String = "86"): ApiResult = withContext(Dispatchers.IO) {
        val q = commonParams()
        q["phone"] = phone; q["area"] = area
        q["getuser"] = "1"; q["getoauth"] = "1"; q["getcookie"] = "1"
        q["i"] = WeiboCrypto.iValue()
        val body = FormBody.Builder()
            .add("aid", aid ?: "")
            .add("pwd", "")
            .add("flag", "1")
            .add("phone", phone)
            .build()
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("account/login_sendcode", q)).post(body).build()
        ).execute()
        val text = resp.body!!.string()
        val obj = WeiboJson.parse(text)
        val data = obj?.get("data")?.let { it as? kotlinx.serialization.json.JsonObject }
        val ch = SmsChallenge(
            phone = phone, area = area,
            code = obj?.str("code") ?: data?.str("code") ?: "",
            number = obj?.str("number") ?: data?.str("number") ?: phone,
            retcode = obj?.str("retcode") ?: data?.str("retcode"),
        )
        parseResult(resp.code, text, ch)
    }

    // ---------- 短信验码(WeiboSmsVerifyActivity 参数布局) ----------
    suspend fun smsVerify(ch: SmsChallenge, smscode: String): ApiResult = withContext(Dispatchers.IO) {
        val q = commonParams()
        q["i"] = WeiboCrypto.iValue()
        q["phone"] = ch.phone
        q["number"] = ch.number
        q["code"] = ch.code
        q["smscode"] = smscode
        ch.retcode?.let { q["retcode"] = it }
        val body = FormBody.Builder()
            .add("getuser", "1").add("getoauth", "1").add("getcookie", "1")
            .add("device_name", device.deviceName)
            .build()
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("account/login", q)).post(body).build()
        ).execute()
        val r = parseResult(resp.code, resp.body!!.string())
        if (r.session != null) session = r.session
        r
    }

    // ---------- 开放平台时间线(api.weibo.com,OAuth2,无 cum 体系) ----------
    suspend fun oauthTimeline(count: Int = 20): List<WeiboMblog> = withContext(Dispatchers.IO) {
        ensureFreshToken()
        val url = "https://api.weibo.com/2/statuses/home_timeline.json"
        var text = oauthTimelineRaw(url)
        if (text.contains("expired_token")) {
            // token 过期 → alt 静默重登 → 重试一次
            altRelogin()
            text = oauthTimelineRaw(url)
        }
        val list = TimelineParser.parse(text)
        if (list.isEmpty()) {
            val obj = WeiboJson.parse(text)
            val err = obj?.str("error") ?: obj?.str("error_description") ?: obj?.str("errmsg")
            if (err != null) throw ApiError(obj?.int("error_code") ?: -1, err)
            println("Share4Login timeline raw: " + text.take(400))
        }
        list
    }

    private fun oauthTimelineRaw(url: String): String {
        val resp = http.newCall(
            baseRequestBuilder().url(url)
                .header("Authorization", "OAuth2 " + (session?.oauthToken ?: ""))
                .get().build()
        ).execute()
        return resp.body!!.string()
    }

    /** alt 登录:免密换新 gsid + oauth token(验证页 JSBridge 回执或上次会话) */
    suspend fun altLogin(alt: String, phone: String? = null): ApiResult {
        val q = LinkedHashMap<String, String>()
        q["c"] = "weicoabroad"
        q["i"] = WeiboCrypto.iValue()
        q["alt"] = alt
        q["getuser"] = "1"; q["getoauth"] = "1"; q["getcookie"] = "1"
        q["lang"] = "zh_CN_#Hans"
        aid?.let { q["aid"] = it }
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("account/login", q))
                .post("".toRequestBody(null)).build()
        ).execute()
        val r = parseResult(resp.code, resp.body!!.string())
        if (r.session != null) {
            // 响应可能不带 alt 字段:用本次使用的 alt 兜底保存,续期链(altRelogin)永远有凭据
            session = r.session!!.copy(
                phone = phone ?: session?.phone,
                alt = r.session!!.alt ?: alt,
            )
        }
        return r
    }

    /** alt 静默重登:免密换新 gsid + 新 oauth token(旧包 WeiboWebAuthorizeActivity alt 流) */
    suspend fun altRelogin(): ApiResult? {
        val alt = session?.alt ?: return null
        val q = LinkedHashMap<String, String>()
        q["c"] = "weicoabroad"
        q["i"] = WeiboCrypto.iValue()
        q["alt"] = alt
        q["getuser"] = "1"; q["getoauth"] = "1"; q["getcookie"] = "1"
        q["lang"] = "zh_CN_#Hans"
        aid?.let { q["aid"] = it }
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("account/login", q))
                .post("".toRequestBody(null)).build()
        ).execute()
        val r = parseResult(resp.code, resp.body!!.string())
        if (r.session != null) {
            session = r.session!!.copy(
                phone = session?.phone,
                alt = r.session!!.alt ?: alt,
            )
        }
        return r
    }

    /**
     * account/getoauth 刷新(旧包 C2052Zb weicoabroad 流):
     * s = 反馈索引 sApi(phone, weico PIN, "1299295010")(2026-10 修正:旧 sNew 索引规则错误),
     * 换回全新 gsid + oauth2.0 token。
     */
    suspend fun getoauthRefresh(): ApiResult? {
        val phone = session?.phone ?: return null
        val q = LinkedHashMap<String, String>()
        q["c"] = "weicoabroad"
        q["s"] = WeiboCrypto.sApi(phone, AID_FROM, WeiboCrypto.PIN)
        q["i"] = WeiboCrypto.iValue()
        session?.gsid?.let { q["gsid"] = it }
        q["phone"] = phone
        q["lang"] = "zh_CN"
        q["from"] = AID_FROM
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("account/getoauth", q)).get().build()
        ).execute()
        val r = parseResult(resp.code, resp.body!!.string())
        if (r.session != null) session = r.session!!.copy(phone = phone)
        return r
    }

    private suspend fun ensureFreshToken() {
        val s = session ?: return
        val now = System.currentTimeMillis() / 1000
        val expiry = s.tokenIssuedAt + s.tokenExpires - 60
        if (s.tokenExpires > 0 && now >= expiry) {
            altRelogin()
            if (session?.tokenIssuedAt == s.tokenIssuedAt) {
                // alt 失败(缺失/服务端拒)→ getoauth 通道(需 phone)
                getoauthRefresh()
            }
        }
    }

    // ---------- 时间线(需已登录 gsid) ----------
    suspend fun cardlist(containerId: String, page: Int = 1, count: Int = 25): String {
        ensureAid()
        val q = commonParams(authed = true)
        q["containerid"] = containerId
        q["page"] = page.toString()
        q["count"] = count.toString()
        applyCum("/2/cardlist", q)
        return execText(baseRequestBuilder().url(buildUrl("cardlist", q)).get().build())
    }

    /** 对最终参数集计算 cum(与实际发送 query 一字不差);builder 用 okhttp 同款 URL 编码 */
    private suspend fun applyCum(pathPrefix: String, q: LinkedHashMap<String, String>) {
        val p = cumProvider ?: return
        val url = buildUrl(pathPrefix.substringAfter("/2/"), q)
        val marker = pathPrefix + "?"
        val path = marker + url.substringAfter(marker)
        val cum = p(path) ?: return
        if (cum.isNotEmpty()) q["cum"] = cum
    }

    /** 服务端业务错误(-105 等),UI 层捕获展示 */
    class ApiError(val errno: Int, val errmsg: String) : Exception("$errmsg($errno)")

    suspend fun timeline(containerId: String? = null, page: Int = 1): List<WeiboMblog> {
        val cid = containerId ?: CID_HOT
        val raw = cardlist(cid, page)
        val obj = WeiboJson.parse(raw)
        val errno = obj?.int("errno")
        if (errno != null && errno != 0) {
            // -200:业务层(身份已过)——热榜容器等异常时降级日榜
            if (errno == -200 && cid != CID_HOT) {
                return timeline(CID_HOT, page)
            }
            throw ApiError(errno, obj?.str("errmsg") ?: "未知错误")
        }
        return CardlistParser.parse(raw)
    }

    /**
     * 关注时间线(主页“关注”tab)。cardlist 的 230869{uid}_-_mix 是“我的赞”(C6991tb MY_LIKE,
     * 2026-10-21 纠错),关注流真身是 statuses/friends_timeline(statuses[] + page 分页)。
     */
    suspend fun friendsTimeline(page: Int = 1, count: Int = 25): List<WeiboMblog> =
        withContext(Dispatchers.IO) {
            ensureAid()
            val q = commonParams(authed = true)
            q["count"] = count.toString()
            q["page"] = page.toString()
            applyCum("/2/statuses/friends_timeline", q)
            val raw = execText(baseRequestBuilder()
                .url(buildUrl("statuses/friends_timeline", q)).get().build())
            val obj = WeiboJson.parse(raw)
            val errno = obj?.int("errno")
            if (errno != null && errno != 0) {
                throw ApiError(errno, obj?.str("errmsg") ?: "friendsTimeline 失败")
            }
            TimelineParser.parse(raw)
        }

    // ---------- 图片发博·开放平台两步流(2026-10 端点确认:cn 端内联已关闭) ----------
    // ① POST upload.api.weibo.com/2/mss/upload.json  multipart(pic) + access_token → pic_id
    // ② POST api.weibo.com/2/statuses/upload.json     access_token + status + pic_id
    // 需新鲜 token(300s):ensureFreshToken 依赖 alt(登录响应持久化)/phone 静默续期
    suspend fun uploadPicMss(png: ByteArray): String? = withContext(Dispatchers.IO) {
        ensureFreshToken()
        val token = session?.oauthToken ?: return@withContext null
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("access_token", token)
            .addFormDataPart("pic", "share4.png",
                png.toRequestBody("image/png".toMediaType()))
            .build()
        val req = baseRequestBuilder()
            .url("https://upload.api.weibo.com/2/mss/upload.json")
            .post(form).build()
        val text = http.newCall(req).execute().use { it.body?.string() ?: "" }
        WeiboJson.parse(text)?.str("pic_id") ?: WeiboJson.parse(text)?.str("picId")
    }

    suspend fun statusesUpload(status: String, picId: String): ApiResult = withContext(Dispatchers.IO) {
        ensureFreshToken()
        val token = session?.oauthToken ?: return@withContext ApiResult(0, -1, "未登录", "")
        val form = FormBody.Builder()
            .add("access_token", token)
            .add("status", status)
            .add("pic_id", picId)
            .build()
        val req = baseRequestBuilder()
            .url("https://api.weibo.com/2/statuses/upload.json")
            .post(form).build()
        val text = http.newCall(req).execute().use { it.body?.string() ?: "" }
        val obj = WeiboJson.parse(text)
        ApiResult(200, obj?.int("error_code") ?: obj?.int("errno"),
            obj?.str("error") ?: obj?.str("errmsg"), text)
    }

    /** 端到端:图 → pic_id → 带图发布 */
    suspend fun postWithImage(status: String, png: ByteArray): ApiResult {
        val picId = uploadPicMss(png) ?: return ApiResult(0, -1, "图片上传失败(需重新登录以激活 token)", "")
        return statusesUpload(status, picId)
    }

    // ---------- 个人页(2026-10-20 实测:profile/me + 107603{uid}__-_WEIBO_SECOND_PROFILE_WEIBO) ----------

    data class Profile(
        val screenName: String = "",
        val avatarHd: String? = null,
        val description: String = "",
        val followers: Int = 0,
        val friends: Int = 0,
        val statuses: Int = 0,
        val verified: Boolean = false,
        val verifiedReason: String? = null,
        val cover: String? = null,
        val gender: String? = null,
        val location: String? = null,
    )

    suspend fun profile(uid: String? = null): Profile {
        val q = commonParams(authed = true)
        uid?.let { q["uid"] = it }
        applyCum("/2/profile/me", q)
        val raw = execText(baseRequestBuilder().url(buildUrl("profile/me", q)).get().build())
        val obj = WeiboJson.parse(raw)
        val errno = obj?.int("errno")
        if (errno != null && errno != 0) throw ApiError(errno, obj?.str("errmsg") ?: "profile 失败")
        val mi = obj?.get("mineinfo")?.let { it as? kotlinx.serialization.json.JsonObject } ?: return Profile()
        return Profile(
            screenName = mi.str("screen_name") ?: "",
            avatarHd = mi.str("avatar_hd") ?: mi.str("profile_image_url"),
            description = mi.str("description") ?: "",
            followers = mi.str("followers_count")?.toIntOrNull() ?: 0,
            friends = mi.str("friends_count")?.toIntOrNull() ?: 0,
            statuses = mi.str("statuses_count")?.toIntOrNull() ?: 0,
            verified = mi.str("verified") == "true" || mi.str("verified") == "1",
            verifiedReason = mi.str("verified_reason"),
            cover = mi.str("cover_image_phone") ?: mi.str("cover_image"),
            gender = mi.str("gender"),
            location = mi.str("location"),
        )
    }

    /** 个人微博列表:230413 容器为主(jadx C7292xB 实测校准),首页为空时回退旧 107603 布局 */
    suspend fun userTimeline(uid: String, page: Int = 1): List<WeiboMblog> {
        val list = userTimelineBy(cidProfileWeibos(uid), page)
        if (list.isNotEmpty() || page > 1) return list
        return userTimelineBy("107603${uid}__-_WEIBO_SECOND_PROFILE_WEIBO", page)
    }

    private suspend fun userTimelineBy(cid: String, page: Int): List<WeiboMblog> {
        val q = commonParams(authed = true)
        q["containerid"] = cid
        q["page"] = page.toString()
        q["count"] = "10"
        applyCum("/2/cardlist", q)
        val raw = execText(baseRequestBuilder().url(buildUrl("cardlist", q)).get().build())
        val obj = WeiboJson.parse(raw)
        val errno = obj?.int("errno")
        if (errno != null && errno != 0) throw ApiError(errno, obj?.str("errmsg") ?: "userTimeline 失败")
        return CardlistParser.parse(raw)
    }


    // ---------- 用户主页 / 关注 / 详情 / 评论 / 热搜(2026-10-21 复刻推进) ----------

    /** 主页微博流 containerid(jadx C7292xB.O00000O:230413{uid}_-_WEIBO_SECOND_PROFILE_WEIBO) */
    fun cidProfileWeibos(uid: String): String = "230413${uid}_-_WEIBO_SECOND_PROFILE_WEIBO"

    /** 用户资料(api.weibo.cn/2/users/show,已登录签名链) */
    suspend fun userProfile(uid: String): WeiboUserProfile? = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["uid"] = uid
        applyCum("/2/users/show", q)
        val text = execText(baseRequestBuilder().url(buildUrl("users/show", q)).get().build())
        WeiboJson.parse(text)?.let { o ->
            if (o.str("uid") == null && o.str("id") == null) return@let null
            WeiboUserProfile(
                uid = o.str("uid") ?: o.str("id") ?: uid,
                screenName = o.str("screen_name") ?: "",
                avatarHd = o.str("avatar_hd") ?: o.str("profile_image_url"),
                verified = o.str("verified")?.toBooleanStrictOrNull() ?: false,
                verifiedReason = o.str("verified_reason"),
                description = o.str("description") ?: "",
                followers = o.int("followers_count") ?: 0,
                friends = o.int("friends_count") ?: 0,
                statuses = o.int("statuses_count") ?: 0,
                following = o.str("following")?.toBooleanStrictOrNull()
                    ?: (o.int("follow_me") == 1),
                gender = o.str("gender") ?: "m",
                location = o.str("location"),
            )
        }
    }

    /** 关注 / 取关(Jz:friendships/create|destroy,POST uid) */
    suspend fun friendship(uid: String, create: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["uid"] = uid
        applyCum(if (create) "/2/friendships/create" else "/2/friendships/destroy", q)
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl(if (create) "friendships/create" else "friendships/destroy", q))
                .post("".toRequestBody(null)).build()
        ).execute()
        val obj = WeiboJson.parse(resp.body!!.string())
        (obj?.int("errno") ?: 0) == 0
    }

    /** 微博详情(statuses/show,id=mid;返回体即 mblog 本体,与时间线卡片同构) */
    suspend fun statusDetail(mid: String): WeiboMblog? = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["id"] = mid
        applyCum("/2/statuses/show", q)
        val text = execText(baseRequestBuilder().url(buildUrl("statuses/show", q)).get().build())
        val obj = WeiboJson.parse(text) ?: return@withContext null
        if (obj.str("mid") == null && obj.str("id") == null) null else CardlistParser.parseMblog(obj)
    }

    /** 评论流(comments/show,id=mid,page) */
    suspend fun comments(mid: String, page: Int = 1): List<WeiboComment> = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["id"] = mid; q["page"] = page.toString(); q["count"] = "20"
        applyCum("/2/comments/show", q)
        val text = execText(baseRequestBuilder().url(buildUrl("comments/show", q)).get().build())
        CommentParser.parse(text)
    }

    /** 发评论(comments/create) */
    suspend fun commentCreate(mid: String, comment: String): ApiResult = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["id"] = mid; q["comment"] = comment
        applyCum("/2/comments/create", q)
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("comments/create", q))
                .post("".toRequestBody(null)).build()
        ).execute()
        parseResult(resp.code, resp.body!!.string())
    }

    /**
     * 热搜榜:原 106003 容器 2026 服务端已下线(cardlist -200 无此业务配置),
     * m.weibo.cn 容器接口同步 SPA 化;改走 weibo.com/ajax/side/hotSearch,
     * 凭据为登录响应 crossdomain 的 .weibo.com SUB(会话缺票据时返回空)。
     */
    suspend fun hotSearch(): List<HotWord> = withContext(Dispatchers.IO) {
        val sub = session?.webSubCom ?: return@withContext emptyList()
        val req = Request.Builder()
            .url("https://weibo.com/ajax/side/hotSearch")
            .header("User-Agent", WEB_UA)
            .header("Referer", "https://weibo.com/")
            .header("Cookie", "SUB=$sub")
            .get().build()
        val text = http.newCall(req).execute().use { it.body?.string() ?: "" }
        HotSearchParser.parse(text)
    }

    // ---------- 收藏 / 私信(2026-10-22 复刻推进,规格见 PROTOCOL.md §12) ----------

    /** signedPost 的“errno 为 0/缺失即成功”包装 */
    private suspend fun postOk(endpoint: String, extra: Map<String, String>): Boolean {
        val raw = signedPost(endpoint, extra)
        val obj = WeiboJson.parse(raw) ?: return false
        return (obj.int("errno") ?: 0) == 0
    }

    /** 收藏列表(favorites:元素 {status, favorited_time},status 与时间线卡片同构) */
    suspend fun favorites(page: Int = 1): List<WeiboMblog> = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["page"] = page.toString(); q["count"] = "20"
        applyCum("/2/favorites", q)
        val raw = execText(baseRequestBuilder().url(buildUrl("favorites", q)).get().build())
        val obj = WeiboJson.parse(raw) ?: return@withContext emptyList()
        val errno = obj.int("errno")
        if (errno != null && errno != 0) throw ApiError(errno, obj.str("errmsg") ?: "favorites 失败")
        obj["favorites"]?.jsonArray?.mapNotNull { el ->
            (el as? kotlinx.serialization.json.JsonObject)
                ?.get("status")?.jsonObject?.let { CardlistParser.parseMblog(it) }
        } ?: emptyList()
    }

    suspend fun favoriteCreate(mid: String): Boolean =
        postOk("favorites/create", mapOf("id" to mid))

    suspend fun favoriteDestroy(mid: String): Boolean =
        postOk("favorites/destroy", mapOf("id" to mid))

    /** 私信会话列表(direct_messages/user_list) */
    suspend fun dmUsers(): List<WeiboConversation> = withContext(Dispatchers.IO) {
        ensureAid()
        val q = commonParams(authed = true)
        q["count"] = "20"; q["page"] = "1"
        applyCum("/2/direct_messages/user_list", q)
        val raw = execText(baseRequestBuilder()
            .url(buildUrl("direct_messages/user_list", q)).get().build())
        DmParser.parseUsers(raw)
    }

    /** 私信消息(最新在前;聊天展示请 reversed) */
    suspend fun dmConversation(uid: String, count: Int = 20): List<WeiboDirectMessage> =
        withContext(Dispatchers.IO) {
            ensureAid()
            val q = commonParams(authed = true)
            q["uid"] = uid; q["count"] = count.toString()
            applyCum("/2/direct_messages/conversation", q)
            val raw = execText(baseRequestBuilder()
                .url(buildUrl("direct_messages/conversation", q)).get().build())
            DmParser.parseMessages(raw)
        }

    /** 发私信(direct_messages/create,返回发出的消息;失败返回 null) */
    suspend fun dmSend(uid: String, text: String): WeiboDirectMessage? {
        val raw = signedPost("direct_messages/create",
            mapOf("text" to text, "uid" to uid))
        val obj = WeiboJson.parse(raw) ?: return null
        if ((obj.int("errno") ?: 0) != 0) return null
        return obj.let { DmParser.parseMessage(it) }
    }

    // ---------- 通知(2026-10-20 实测:@我的 + 未读数) ----------
    /** @我的微博(statuses/mentions) */
    suspend fun mentions(page: Int = 1): List<WeiboMblog> {
        val q = commonParams(authed = true)
        q["count"] = "20"; q["page"] = page.toString()
        applyCum("/2/statuses/mentions", q)
        val raw = execText(baseRequestBuilder().url(buildUrl("statuses/mentions", q)).get().build())
        val obj = WeiboJson.parse(raw)
        val errno = obj?.int("errno")
        if (errno != null && errno != 0) throw ApiError(errno, obj?.str("errmsg") ?: "mentions 失败")
        return TimelineParser.parse(raw)
    }

    /** 未读数(remind/unread_count):cmt/dm/mention_status/attitude… */
    suspend fun unreadCount(): Map<String, Int> {
        val q = commonParams(authed = true)
        q["type"] = "cmt,mention,like,dm"
        applyCum("/2/remind/unread_count", q)
        val raw = execText(baseRequestBuilder().url(buildUrl("remind/unread_count", q)).get().build())
        val obj = WeiboJson.parse(raw) ?: return emptyMap()
        return listOf("cmt", "dm", "mention_status", "mention_cmt", "attitude", "follower")
            .mapNotNull { k -> obj.int(k)?.let { k to it } }.toMap()
    }

    // ---------- 搜索(2026-10-20 实测:100303type=1&q=..&t=0 返回真实结果) ----------
    suspend fun search(keyword: String, page: Int = 1): List<WeiboMblog> {
        val q = commonParams(authed = true)
        q["containerid"] = "100303type=1&q=$keyword&t=0"
        q["page"] = page.toString()
        q["count"] = "10"
        applyCum("/2/cardlist", q)
        val raw = execText(baseRequestBuilder().url(buildUrl("cardlist", q)).get().build())
        val obj = WeiboJson.parse(raw)
        val errno = obj?.int("errno")
        if (errno != null && errno != 0) throw ApiError(errno, obj?.str("errmsg") ?: "搜索失败")
        return CardlistParser.parse(raw)
    }

    // ---------- 互动:点赞/评论/转发(2026-10-20 全部实测,规格见 PROTOCOL.md §10) ----------

    /** query 构造 + cum 的 POST 通用流(与实测脚本 test_like/test_comment 一致) */
    private suspend fun signedPost(endpoint: String, extra: Map<String, String>): String =
        with(java.util.LinkedHashMap<String, String>()) {
            putAll(commonParams(authed = true))
            putAll(extra)
            applyCum("/2/$endpoint", this)
            val url = (API_BASE + endpoint).toHttpUrl().newBuilder().apply {
                this@with.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build().toString()
            val req = baseRequestBuilder().url(url).post("".toRequestBody(null)).build()
            withContext(Dispatchers.IO) {
                http.newCall(req).execute().use { it.body?.string() ?: "" }
            }
        }

    /** 点赞 */
    suspend fun like(mid: String): Boolean =
        WeiboJson.parse(signedPost("like/set_like", mapOf("id" to mid)))?.int("errno") == null

    suspend fun cancelLike(mid: String): Boolean =
        WeiboJson.parse(signedPost("like/cancel_like", mapOf("id" to mid)))
            ?.str("result") == "true"

    /** 评论 */
    suspend fun comment(mid: String, text: String): ApiResult {
        val raw = signedPost("comments/create",
            mapOf("id" to mid, "comment" to text, "uicode" to "10000445", "moduleID" to "704"))
        val obj = WeiboJson.parse(raw)
        return ApiResult(httpCode = 200, errno = obj?.int("errno"), errmsg = obj?.str("errmsg"),
            raw = raw)
    }

    /** 转发(返回新博文 mid,空串=失败) */
    suspend fun repost(mid: String, text: String): String {
        val raw = signedPost("statuses/repost",
            mapOf("id" to mid, "status" to text, "isComment" to "0"))
        val obj = WeiboJson.parse(raw) ?: return ""
        return obj.str("mid") ?: obj.str("idstr") ?: ""
    }

    // ---------- 发博(statuses/send multipart,2026-10 端到端实测) ----------
    suspend fun statusesSend(content: String, visible: Int = 0): ApiResult = withContext(Dispatchers.IO) {
        ensureAid()
        // query:公共参数 + cum(实测必须);body:multipart 同样携带公共参数 + content/visible
        val q = commonParams(authed = true)
        applyCum("/2/statuses/send", q)
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
        q.forEach { (k, v) -> form.addFormDataPart(k, v) }
        form.addFormDataPart("content", content)
        form.addFormDataPart("visible", visible.toString())
        val resp = http.newCall(
            baseRequestBuilder().url(buildUrl("statuses/send", q)).post(form.build()).build()
        ).execute()
        parseResult(resp.code, resp.body!!.string())
    }
}
