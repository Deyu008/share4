package com.deyu.share4.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonParamsTest {

    @Test fun `common params key set matches Share`() {
        val c = WeiboClient(DeviceProfile("Xiaomi", "MI 9", "13"))
        val keys = c.commonParams().keys.toList()
        assertEquals(listOf("lang","networktype","c","from","wm","oldwm","umtt",
            "v_p","com_ver","wb_version","skin","v_f"), keys)
    }

    @Test fun `authed params carry gsid and feedback s`() {
        val c = WeiboClient(DeviceProfile())
        assertNull(c.commonParams()["gsid"])
        c.restoreSession(LoginSession(gsid = "_2A_x", uid = "123"))
        c.commonParams(authed = true).let {
            assertEquals("_2A_x", it["gsid"])
            // s = newCalculateS(uid):sha512 反馈索引(2026-10 破解)
            assertEquals(WeiboCrypto.sApi("123"), it["s"])
        }
    }
}
