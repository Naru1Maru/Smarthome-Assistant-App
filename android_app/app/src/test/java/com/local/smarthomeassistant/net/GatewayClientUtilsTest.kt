package com.local.smarthomeassistant.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GatewayClientUtilsTest {

    @Test
    fun normalizeBaseUrl_addsHttpSchemeWhenMissing() {
        assertEquals("http://example", normalizeBaseUrl("example"))
        assertEquals("https://secure", normalizeBaseUrl("https://secure"))
        assertEquals("http://host", normalizeBaseUrl(" host/"))
    }

    @Test
    fun normalizeApiKey_stripsHeaderAndWhitespace() {
        val raw = "X-API-Key:   abc.def _ghi\n"
        val normalized = normalizeApiKey(raw)
        assertEquals("abc.def_ghi", normalized)
        assertFalse(normalized.contains(" "))
    }

    @Test
    fun parseGatewayOk_extractsClarificationAndContext() {
        val json = JSONObject().apply {
            put("status", "NEEDS_CLARIFICATION")
            put("say_text", "Скажите ещё раз")
            put("clarification", JSONObject().apply {
                put("needed", true)
                put("question", "Какую комнату?")
                put("options", listOf("кухня", "гостиная"))
            })
            put("validated_command", JSONObject().apply {
                put("normalized", JSONObject().apply {
                    put("context_updates", JSONObject().apply {
                        put("last_area_name", "кухня")
                    })
                })
            })
        }.toString()

        val ok = parseGatewayOk(json)
        assertEquals("NEEDS_CLARIFICATION", ok.status)
        assertEquals("Скажите ещё раз", ok.sayText)
        assertEquals("кухня", ok.contextUpdatesLastAreaName)
        assertTrue(ok.clarification?.options?.contains("кухня") == true)
    }
}
