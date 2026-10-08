package com.deyu.share4.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对拍金标准:phase0/golden.json(Python 参考实现 weibo_proto.py / login_probes.py 生成,
 * 其中 s 旧版与 i 算法已通过对真实服务端的行为验证)。
 */
class WeiboCryptoTest {

    @Test fun pin() = assertEquals("CypCHG2kSlRkdvr2RG1QF8b2lCWXl7k7", WeiboCrypto.PIN)

    @Test fun iValue_15zeros() = assertEquals("f0cd624", WeiboCrypto.iValue("000000000000000"))

    @Test fun iValue_serial() = assertEquals("b3eba63", WeiboCrypto.iValue("serial12345"))

    @Test fun sOld_login() =
        assertEquals("b990bd1b", WeiboCrypto.sOld("13800138000" + "Passw0rd!测试"))

    @Test fun sOld_uid() = assertEquals("33608dfd", WeiboCrypto.sOld("2059907146295"))

    @Test fun sNew_shareFrom() =
        assertEquals("c1fcccfe", WeiboCrypto.sNew("2059907146295", WeiboCrypto.PIN, "10B6095010"))

    @Test fun checktoken() =
        assertEquals("d61bed0a1627ad7192b3607d679a79e3", WeiboCrypto.checktoken("443d5ca3ad2ae53a"))

    @Test fun rsaPwd_length() {
        // 9 字节中文密码 → 单块 RSA(128B) → Base64 172 字符(Python 参考一致)
        assertEquals(172, WeiboCrypto.rsaEncryptB64("Passw0rd!测试", WeiboCrypto.RSA_PUBKEY_PWD).length)
    }

    @Test fun rsaPwd_deterministicBlockCount() {
        val longPwd = "x".repeat(300)  // 300 ASCII = 3 块
        val enc = WeiboCrypto.rsaEncryptB64(longPwd, WeiboCrypto.RSA_PUBKEY_PWD)
        assertEquals(3 * 128 / 3 * 4, enc.length)  // 3 块 → 384B → 512 base64 chars
        assertTrue(enc.length == 512)
    }

    @Test fun ua_format() {
        val d = DeviceProfile("Xiaomi", "MI 9", "13")
        assertEquals("Xiaomi-MI 9__weibo__11.6.0__android__android13", d.ua)
        assertEquals("Xiaomi-MI 9", d.deviceName)
    }
}
