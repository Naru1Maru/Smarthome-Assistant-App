package com.local.smarthomeassistant.net

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun normalizeBaseUrl_addsSchemeAndTrims() {
        assertEquals("http://192.168.0.10:8099", normalizeBaseUrlForTest(" 192.168.0.10:8099 "))
        assertEquals("http://192.168.0.10:8099", normalizeBaseUrlForTest("http://192.168.0.10:8099/"))
        assertEquals("https://x.y", normalizeBaseUrlForTest("https://x.y/"))
    }
}
