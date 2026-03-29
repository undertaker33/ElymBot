package com.astrbot.android.data

import com.astrbot.android.model.ProviderType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyRuntimeStateImportTest {
    @Test
    fun `legacy bridge prefs parser keeps manual commands and endpoint`() {
        val imported = parseLegacyNapCatBridgeConfig(
            values = mapOf(
                "runtime_mode" to "manual",
                "endpoint" to "ws://10.0.2.2:7000/ws",
                "health_url" to "http://10.0.2.2:6099",
                "auto_start" to true,
                "start_command" to "start.sh",
                "stop_command" to "stop.sh",
                "status_command" to "status.sh",
                "command_preview" to "Start runtime",
            ),
        )

        assertEquals("manual", imported.runtimeMode)
        assertEquals("ws://10.0.2.2:7000/ws", imported.endpoint)
        assertEquals("http://10.0.2.2:6099", imported.healthUrl)
        assertTrue(imported.autoStart)
        assertEquals("start.sh", imported.startCommand)
        assertEquals("Start runtime", imported.commandPreview)
    }

    @Test
    fun `legacy tts assets parser keeps clips and bindings`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("id", "asset-1")
                    .put("name", "Narrator")
                    .put("source", "imported")
                    .put("localPath", "C:/tmp/ref.wav")
                    .put("durationMs", 1200L)
                    .put("sampleRateHz", 44100)
                    .put("createdAt", 100L)
                    .put(
                        "clips",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "clip-1")
                                .put("localPath", "C:/tmp/ref.wav")
                                .put("durationMs", 1200L)
                                .put("sampleRateHz", 44100)
                                .put("createdAt", 100L),
                        ),
                    )
                    .put(
                        "providerBindings",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "provider-1:model-1:voice-1")
                                .put("providerId", "provider-1")
                                .put("providerType", ProviderType.OPENAI_TTS.name)
                                .put("model", "model-1")
                                .put("voiceId", "voice-1")
                                .put("displayName", "Voice One")
                                .put("createdAt", 101L)
                                .put("lastVerifiedAt", 102L)
                                .put("status", "ready"),
                        ),
                    ),
            )
            .toString()

        val assets = parseLegacyTtsVoiceAssets(raw)

        assertEquals(1, assets.size)
        val asset = assets.single()
        assertEquals("asset-1", asset.id)
        assertEquals(1, asset.clips.size)
        assertEquals("clip-1", asset.clips.single().id)
        assertEquals(1, asset.providerBindings.size)
        assertEquals("provider-1", asset.providerBindings.single().providerId)
    }
}
