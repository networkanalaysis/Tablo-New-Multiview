package com.tablotv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TabloSignerTest {
    @Test
    fun `device auth header matches known hash format`() {
        val (authHeader, date) = TabloSigner.makeDeviceAuth(
            "POST",
            "/guide/channels/test/watch",
            "{\"device_id\":\"abc\"}"
        )

        assert(authHeader.startsWith("tablo:"))
        assert(date.isNotBlank())
        assertEquals(3, authHeader.split(":").size)
    }

    @Test
    fun `md5 hmac produces stable output`() {
        val digest = TabloSigner.hmacMd5("secret", "payload")
        assertEquals("4f967408b96fb0e40180074062022698", digest.lowercase())
    }
}
