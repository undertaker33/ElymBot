package com.astrbot.android.data

import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyProfileImportTest {
    @Test
    fun `provider legacy json keeps capabilities and probe state`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("id", "provider-1")
                    .put("name", "Vision")
                    .put("baseUrl", "https://example.com/v1")
                    .put("model", "vision-pro")
                    .put("providerType", ProviderType.OPENAI_COMPATIBLE.name)
                    .put("apiKey", "secret")
                    .put("enabled", true)
                    .put("multimodalRuleSupport", FeatureSupportState.SUPPORTED.name)
                    .put("multimodalProbeSupport", FeatureSupportState.UNKNOWN.name)
                    .put("nativeStreamingRuleSupport", FeatureSupportState.UNSUPPORTED.name)
                    .put("nativeStreamingProbeSupport", FeatureSupportState.SUPPORTED.name)
                    .put("sttProbeSupport", FeatureSupportState.UNSUPPORTED.name)
                    .put("ttsProbeSupport", FeatureSupportState.UNKNOWN.name)
                    .put("capabilities", JSONArray().put("CHAT").put("TTS"))
                    .put("ttsVoiceOptions", JSONArray().put("alloy").put("nova")),
            )
            .toString()

        val providers = parseLegacyProviderProfiles(raw)

        assertEquals(1, providers.size)
        val profile = providers.single()
        assertEquals("provider-1", profile.id)
        assertEquals(setOf(ProviderCapability.CHAT, ProviderCapability.TTS), profile.capabilities)
        assertEquals(FeatureSupportState.SUPPORTED, profile.multimodalRuleSupport)
        assertEquals(FeatureSupportState.SUPPORTED, profile.nativeStreamingProbeSupport)
        assertEquals(listOf("alloy", "nova"), profile.ttsVoiceOptions)
    }

    @Test
    fun `config legacy json keeps selected id and list fields`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("id", "cfg-1")
                    .put("name", "Main")
                    .put("defaultChatProviderId", "chat-1")
                    .put("adminUids", JSONArray().put("10001").put("10002"))
                    .put("wakeWords", JSONArray().put("astrbot"))
                    .put("whitelistEntries", JSONArray().put("group:123"))
                    .put("keywordPatterns", JSONArray().put("hello").put("world"))
                    .put("rateLimitStrategy", "stash"),
            )
            .toString()

        val imported = parseLegacyConfigProfiles(raw, "cfg-1")

        assertEquals("cfg-1", imported.selectedProfileId)
        assertEquals(1, imported.profiles.size)
        val profile = imported.profiles.single()
        assertEquals(listOf("10001", "10002"), profile.adminUids)
        assertEquals(listOf("astrbot"), profile.wakeWords)
        assertEquals(listOf("group:123"), profile.whitelistEntries)
        assertEquals(listOf("hello", "world"), profile.keywordPatterns)
        assertEquals("stash", profile.rateLimitStrategy)
    }

    @Test
    fun `persona legacy json keeps enabled tools and defaults`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("id", "persona-1")
                    .put("name", "Helper")
                    .put("tag", "Warm")
                    .put("systemPrompt", "You are helpful.")
                    .put("enabledTools", JSONArray().put("search").put("image"))
                    .put("defaultProviderId", "provider-1")
                    .put("maxContextMessages", 16)
                    .put("enabled", false),
            )
            .toString()

        val personas = parseLegacyPersonaProfiles(raw)

        assertEquals(1, personas.size)
        val profile = personas.single()
        assertEquals(setOf("search", "image"), profile.enabledTools)
        assertEquals("provider-1", profile.defaultProviderId)
        assertEquals(16, profile.maxContextMessages)
        assertTrue(!profile.enabled)
    }
}
