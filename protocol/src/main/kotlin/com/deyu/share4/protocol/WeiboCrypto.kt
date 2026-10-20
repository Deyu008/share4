package com.deyu.share4.protocol

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * 微博 m-api 签名算法族。全部逆向自 Share 3.9.5(com.hengye.share):
 * - s 旧版  libwbutil/wbgjb aa3 + WeiboSecurityUtils.aa3
 * - s 新版  toSecurityValue(libwbgjb generateS "new version" 分支)
 * - i      libwbutil getIValue
 * - p      libSecShell secP(RSA/ECB/PKCS1,117 字节分块)
 * 规格书:E:\Code\Share\phase0\PROTOCOL.md
 */
object WeiboCrypto {

    /** weico 协议族常量(libwbgjb WeiboPin 反编译 + 国际版公开分析交叉验证) */
    const val PIN = "CypCHG2kSlRkdvr2RG1QF8b2lCWXl7k7"

    /**
     * libwbutil 真 PIN(2026-10 破解):
     * PIN = DES-ECB-Decrypt(常量BLOB, key = md5hex(证书DER[0:首个0x00]).lower().takeLast(8))
     * 真 key 来自活进程内 g_pStandardSigs = c2d319c4...704e6c1b → "704e6c1b"
     * 明文 32 字符 + PKCS5(0x08×8) —— 任何签名包均可直接用此 PIN 算 s
     */
    const val PIN_WBUTIL = "5l0WXnhiY4pJ794KIJ7Rw5F45VXg9sjo"

    /** Share 登录密码 RSA 公钥(libSecShell secP 提取) */
    const val RSA_PUBKEY_PWD =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC46y69c1rmEk6btBLCPgxJkCxdDcAH9k7kBLff" +
        "gG1KWqUErjdv+aMkEZmBaprEW846YEwBn60gyBih3KU518fL3F+sv2b6xEeOxgjWO+NPgSWmT3q1" +
        "up95HmmLHlgVwqTKqRUHd8+Tr43D5h+J8T69etX0YNdT5ACvm+Ar0HdarwIDAQAB"

    /** aid 注册 mfp 加密公钥(DO.O00000oO) */
    const val RSA_PUBKEY_MFP =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHHM0Fi2Z6+QYKXqFUX2Cy6AaWq3cPi+GSn9oe" +
        "AwQbPZR75JB7Netm0HtBVVbtPhzT7UO2p1JhFUKWqrqoYuAjkgMVPmA0sFrQohns5EE44Y86XQop" +
        "D4ZO+dE5KjUZFE6vrPO3rWW3np2BqlgKpjnYZri6TJApmIpGcQg9/G/3zQIDAQAB"

    private val HEX = "0123456789abcdef".toCharArray()

    fun md5Hex(src: String): String = digestHex("MD5", src.toByteArray(Charsets.UTF_8))
    fun sha512Hex(src: String): String = digestHex("SHA-512", src.toByteArray(Charsets.UTF_8))

    private fun digestHex(algo: String, bytes: ByteArray): String {
        val d = MessageDigest.getInstance(algo).digest(bytes)
        val out = CharArray(d.size * 2)
        for (i in d.indices) {
            val v = d[i].toInt() and 0xff
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0f]
        }
        return String(out)
    }

    /**
     * s 旧版(服务端当前在用,实测通过):
     * md5hex(src + PIN) 取 0-based 位 [1,5,2,10,17,9,25,27]
     */
    fun sOld(src: String, pin: String = PIN): String {
        val h = md5Hex(src + pin)
        val idx = intArrayOf(1, 5, 2, 10, 17, 9, 25, 27)
        return buildString { idx.forEach { append(h[it]) } }
    }

    /**
     * s 新版(toSecurityValue,实测当前被服务端拒绝,保留备用):
     * h1 = sha512(pin + src + from);h2 = sha512(from)
     * idx 从 0 按 hex 值累加,每轮取 h1[idx],共 8 字符
     */
    fun sNew(src: String, pin: String, from: String): String {
        val h1 = sha512Hex(pin + src + from)
        val h2 = sha512Hex(from)
        val sb = StringBuilder()
        var i = 0
        for (k in 0 until 8) {
            i += HEX.indexOf(h2[k])
            sb.append(h1[i])
        }
        return sb.toString()
    }

    /**
     * i(IValue,7 字符,libwbutil getIValue 逐指令还原):
     * i = md5(serial)[-6:] + md5(last6 + last6[:4])[-1]
     */
    fun iValue(serial: String = "000000000000000"): String {
        val m1 = md5Hex(serial)
        val last6 = m1.takeLast(6)
        val m2 = md5Hex(last6 + last6.take(4))
        return last6 + m2.takeLast(1)
    }

    /** RSA/ECB/PKCS1 分块加密(117 字节/块),Base64(NO_WRAP)。对应 secP / DO.O000000o */
    fun rsaEncryptB64(plain: String, pubkeyB64: String): String {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubkeyB64)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val data = plain.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream()
        var off = 0
        while (off < data.size) {
            val len = minOf(117, data.size - off)
            out.write(cipher.doFinal(data, off, len))
            off += len
        }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    /** checktoken = md5hex("/" + did + "/obiew")(DO.O00000oO) */
    fun checktoken(did: String): String = md5Hex("/$did/obiew")

    // ============ 2026-10 破解:libwbutil 的 s(反馈索引) 与 cum(零 IV SHA-256) ============

    private val HEXVAL = "0123456789abcdef".withIndex().associate { it.value to it.index }

    /**
     * s 真值(Java 入口 newCalculateS,Unicorn 原生执行逐指令验证):
     * h1 = sha512(PIN_WBUTIL + src + from); h2 = sha512(from)
     * i 从 0 反馈累加:i += hexval(h2[i]) —— 取 h1[i],共 8 字符
     * (jadx 里 toSecurityValue 的 charAt(i) 不是 charAt(k),此前全部复刻错在此)
     */
    fun sApi(src: String, from: String = "10B6095010", pin: String = PIN_WBUTIL): String {
        val h1 = sha512Hex(pin + src + from)
        val h2 = sha512Hex(from)
        val sb = StringBuilder()
        var i = 0
        repeat(8) {
            i += HEXVAL[h2[i]]!!
            sb.append(h1[i])
        }
        return sb.toString()
    }

    /**
     * cum(wb_mapi_getparam,24/24 与 .so 指令级模拟一致):
     * 该库 sha256 从不调 starts → IV 全零(魔改),DES 标准 ECB:
     *   h1 = SHA256'(path); off = 8 - (h1[0]&7)
     *   key = path[off,off+8)(不足补 (8-len)); blk = path[-4:]+path[:4]
     *   cum = UPPERHEX(SHA256'(h1||DES(key,blk)))[off, off+8)
     */
    fun cum(path: String): String? {
        if (path.contains("&cum=")) {
            // C 逻辑:strstr("&cum=") 后第 13 字节(8 位值结束处)为结尾或 '&' → 视为已签名
            val i = path.indexOf("&cum=")
            val nxt = path.getOrNull(i + 13)
            if (nxt == null || nxt == '&') return null
        }
        val b = path.toByteArray(Charsets.ISO_8859_1)
        val h1 = sha256ZeroIv(b)
        val start = h1[0].toInt() and 7
        val off = 8 - start
        if (b.size <= off) return null
        var key = b.copyOfRange(off, minOf(off + 8, b.size))
        if (key.size < 8) {
            val pad = ByteArray(8) { (8 - key.size).toByte() }
            key = key + pad
        }
        val blk = b.copyOfRange(b.size - 4, b.size) + b.copyOfRange(0, 4)
        val des = javax.crypto.Cipher.getInstance("DES/ECB/NoPadding")
            .apply { init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "DES")) }
        val enc = des.doFinal(blk)
        val h2 = sha256ZeroIv(h1 + enc)
        return h2.toHexUpper().substring(off, off + 8)
    }

    /** SHA-256 with custom IV(libwbutil 的 init 只清零、从不 starts → IV 全零) */
    private val K256 = longArrayOf(
        0x428a2f98L, 0x71374491L, 0xb5c0fbcfL, 0xe9b5dba5L, 0x3956c25bL, 0x59f111f1L, 0x923f82a4L, 0xab1c5ed5L,
        0xd807aa98L, 0x12835b01L, 0x243185beL, 0x550c7dc3L, 0x72be5d74L, 0x80deb1feL, 0x9bdc06a7L, 0xc19bf174L,
        0xe49b69c1L, 0xefbe4786L, 0x0fc19dc6L, 0x240ca1ccL, 0x2de92c6fL, 0x4a7484aaL, 0x5cb0a9dcL, 0x76f988daL,
        0x983e5152L, 0xa831c66dL, 0xb00327c8L, 0xbf597fc7L, 0xc6e00bf3L, 0xd5a79147L, 0x06ca6351L, 0x14292967L,
        0x27b70a85L, 0x2e1b2138L, 0x4d2c6dfcL, 0x53380d13L, 0x650a7354L, 0x766a0abbL, 0x81c2c92eL, 0x92722c85L,
        0xa2bfe8a1L, 0xa81a664bL, 0xc24b8b70L, 0xc76c51a3L, 0xd192e819L, 0xd6990624L, 0xf40e3585L, 0x106aa070L,
        0x19a4c116L, 0x1e376c08L, 0x2748774cL, 0x34b0bcb5L, 0x391c0cb3L, 0x4ed8aa4aL, 0x5b9cca4fL, 0x682e6ff3L,
        0x748f82eeL, 0x78a5636fL, 0x84c87814L, 0x8cc70208L, 0x90befffaL, 0xa4506cebL, 0xbef9a3f7L, 0xc67178f2L
    ).map { it.toInt() }.toIntArray()

    private fun sha256ZeroIv(data: ByteArray, iv: IntArray = IntArray(8)): ByteArray {
        var h = iv.copyOf()
        val ml = data.size.toLong() * 8
        val padLen = ((55 - data.size) % 64 + 64) % 64
        val pad = ByteArray(data.size + 1 + padLen + 8)
        data.copyInto(pad)
        pad[data.size] = 0x80.toByte()
        for (i in 0 until 8) pad[pad.size - 8 + i] = (ml ushr ((7 - i) * 8)).toByte()
        val k = K256
        var off = 0
        while (off < pad.size) {
            val w = IntArray(64)
            for (j in 0 until 16) {
                w[j] = ((pad[off + j * 4].toInt() and 0xFF) shl 24) or
                    ((pad[off + j * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((pad[off + j * 4 + 2].toInt() and 0xFF) shl 8) or
                    (pad[off + j * 4 + 3].toInt() and 0xFF)
            }
            for (j in 16 until 64) {
                val s0 = w[j - 15].rotr(7) xor w[j - 15].rotr(18) xor (w[j - 15] ushr 3)
                val s1 = w[j - 2].rotr(17) xor w[j - 2].rotr(19) xor (w[j - 2] ushr 10)
                w[j] = w[j - 16] + s0 + w[j - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (j in 0 until 64) {
                val S1 = e.rotr(6) xor e.rotr(11) xor e.rotr(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + S1 + ch + k[j] + w[j]
                val S0 = a.rotr(2) xor a.rotr(13) xor a.rotr(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = S0 + maj
                hh = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            off += 64
        }
        return ByteArray(32) { i -> (h[i / 4] ushr (24 - (i % 4) * 8)).toByte() }
    }

    private fun Int.rotr(n: Int): Int = (this ushr n) or (this shl (32 - n))

    private fun ByteArray.toHexUpper(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789ABCDEF"[v ushr 4]).append("0123456789ABCDEF"[v and 0xF])
        }
        return sb.toString()
    }

}
