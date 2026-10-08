package com.deyu.share4.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        /** 首页关注时间线 containerid(m.weibo.cn 同款;端到端时验证) */
        const val CID_HOME = "107603"
        /** 热门时间线 */
        const val CID_HOT = "102803_ctg1_-9999_-_hotweibo"

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
                // 原包已登录五件套:gsid + s=calc(uid)(脱壳=s=uid) + i + aid + cum
                p["s"] = it.uid
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
     * s = sNew(phone, PIN, "1299295010")(sha512 取位),换回全新 gsid + oauth2.0 token。
     */
    suspend fun getoauthRefresh(): ApiResult? {
        val phone = session?.phone ?: return null
        val q = LinkedHashMap<String, String>()
        q["c"] = "weicoabroad"
        q["s"] = WeiboCrypto.sNew(phone, WeiboCrypto.PIN, AID_FROM)
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

    suspend fun timeline(containerId: String = CID_HOME, page: Int = 1): List<WeiboMblog> {
        val raw = cardlist(containerId, page)
        val obj = WeiboJson.parse(raw)
        val errno = obj?.int("errno")
        if (errno != null && errno != 0) {
            throw ApiError(errno, obj?.str("errmsg") ?: "未知错误")
        }
        return CardlistParser.parse(raw)
    }

    // ---------- 发博(statuses/send multipart,参数布局见 PROTOCOL.md §7) ----------
    suspend fun statusesSend(content: String, visible: Int = 0): ApiResult = withContext(Dispatchers.IO) {
        ensureAid()
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        commonParams(authed = true).forEach { (k, v) -> form.addFormDataPart(k, v) }
        form.addFormDataPart("content", content)
        form.addFormDataPart("visible", visible.toString())
        val resp = http.newCall(
            baseRequestBuilder().url(API_BASE + "statuses/send").post(form.build()).build()
        ).execute()
        parseResult(resp.code, resp.body!!.string())
    }
}
