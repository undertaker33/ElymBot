package com.astrbot.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyStructuredStateImportTest {
    @Test
    fun `legacy bot bindings parser supports string and object values`() {
        val raw = JSONObject()
            .put("bot-a", "config-a")
            .put(
                "bot-b",
                JSONObject()
                    .put("configProfileId", "config-b")
                    .put("persistConversationLocally", true)
                    .put("boundQqUins", JSONArray().put("123").put("456")),
            )
            .toString()

        val bindings = parseLegacyBotBindings(raw)

        assertEquals("config-a", bindings.getValue("bot-a").configProfileId)
        assertFalse(bindings.getValue("bot-a").persistConversationLocally)
        assertEquals("config-b", bindings.getValue("bot-b").configProfileId)
        assertEquals(listOf("123", "456"), bindings.getValue("bot-b").boundQqUins)
    }

    @Test
    fun `legacy qq account parser ignores blank uins`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("uin", "10001")
                    .put("nickName", "Alice")
                    .put("avatarUrl", "https://example.com/a.png"),
            )
            .put(
                JSONObject()
                    .put("uin", " ")
                    .put("nickName", "Ignored"),
            )
            .toString()

        val accounts = parseLegacySavedQqAccounts(raw)

        assertEquals(1, accounts.size)
        assertEquals("10001", accounts.single().uin)
        assertEquals("Alice", accounts.single().nickName)
    }
}
