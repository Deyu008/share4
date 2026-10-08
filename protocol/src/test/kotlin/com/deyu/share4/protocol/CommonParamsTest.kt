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

    @Test fun `gsid presence adds s`() {
        val c = WeiboClient(DeviceProfile())
        assertNull(c.commonParams()["gsid"])
        c.commonParams("_2A_x").let {
            assertEquals("_2A_x", it["gsid"])
            assertEquals("_2A_x", it["s"])
        }
    }
}
