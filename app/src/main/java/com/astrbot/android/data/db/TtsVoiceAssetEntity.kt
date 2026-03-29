package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.TtsVoiceReferenceClip
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "tts_voice_assets")
data class TtsVoiceAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String,
    val localPath: String,
    val remoteUrl: String,
    val durationMs: Long,
    val sampleRateHz: Int,
    val clipsJson: String,
    val providerBindingsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

fun TtsVoiceAssetEntity.toModel(): TtsVoiceReferenceAsset {
    return TtsVoiceReferenceAsset(
        id = id,
        name = name,
        source = source,
        localPath = localPath,
        remoteUrl = remoteUrl,
        durationMs = durationMs,
        sampleRateHz = sampleRateHz,
        clips = parseClipsJson(clipsJson),
        providerBindings = parseProviderBindingsJson(providerBindingsJson),
        createdAt = createdAt,
    )
}

fun TtsVoiceReferenceAsset.toEntity(): TtsVoiceAssetEntity {
    return TtsVoiceAssetEntity(
        id = id,
        name = name,
        source = source,
        localPath = localPath,
        remoteUrl = remoteUrl,
        durationMs = durationMs,
        sampleRateHz = sampleRateHz,
        clipsJson = JSONArray().apply {
            clips.forEach { clip ->
                put(
                    JSONObject()
                        .put("id", clip.id)
                        .put("localPath", clip.localPath)
                        .put("durationMs", clip.durationMs)
                        .put("sampleRateHz", clip.sampleRateHz)
                        .put("createdAt", clip.createdAt),
                )
            }
        }.toString(),
        providerBindingsJson = JSONArray().apply {
            providerBindings.forEach { binding ->
                put(
                    JSONObject()
                        .put("id", binding.id)
                        .put("providerId", binding.providerId)
                        .put("providerType", binding.providerType.name)
                        .put("model", binding.model)
                        .put("voiceId", binding.voiceId)
                        .put("displayName", binding.displayName)
                        .put("createdAt", binding.createdAt)
                        .put("lastVerifiedAt", binding.lastVerifiedAt)
                        .put("status", binding.status),
                )
            }
        }.toString(),
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
    )
}

private fun parseClipsJson(raw: String): List<TtsVoiceReferenceClip> {
    if (raw.isBlank()) return emptyList()
    val array = JSONArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            val clip = array.optJSONObject(index) ?: continue
            add(
                TtsVoiceReferenceClip(
                    id = clip.optString("id"),
                    localPath = clip.optString("localPath"),
                    durationMs = clip.optLong("durationMs"),
                    sampleRateHz = clip.optInt("sampleRateHz"),
                    createdAt = clip.optLong("createdAt"),
                ),
            )
        }
    }
}

private fun parseProviderBindingsJson(raw: String): List<ClonedVoiceBinding> {
    if (raw.isBlank()) return emptyList()
    val array = JSONArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            val binding = array.optJSONObject(index) ?: continue
            add(
                ClonedVoiceBinding(
                    id = binding.optString("id"),
                    providerId = binding.optString("providerId"),
                    providerType = runCatching {
                        ProviderType.valueOf(binding.optString("providerType"))
                    }.getOrDefault(ProviderType.OPENAI_TTS),
                    model = binding.optString("model"),
                    voiceId = binding.optString("voiceId"),
                    displayName = binding.optString("displayName"),
                    createdAt = binding.optLong("createdAt"),
                    lastVerifiedAt = binding.optLong("lastVerifiedAt"),
                    status = binding.optString("status").ifBlank { "ready" },
                ),
            )
        }
    }
}
