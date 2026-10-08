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
}
