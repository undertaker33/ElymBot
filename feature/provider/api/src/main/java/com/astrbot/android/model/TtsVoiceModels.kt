package com.astrbot.android.model

import com.astrbot.android.feature.provider.domain.model.ProviderType

data class ClonedVoiceBinding(
    val id: String,
    val providerId: String,
    val providerType: ProviderType,
    val model: String,
    val voiceId: String,
    val displayName: String,
    val createdAt: Long,
    val lastVerifiedAt: Long = 0L,
    val status: String = "ready",
)

data class TtsVoiceReferenceClip(
    val id: String,
    val localPath: String,
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TtsVoiceReferenceAsset(
    val id: String,
    val name: String,
    val source: String = "",
    val localPath: String = "",
    val remoteUrl: String = "",
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val clips: List<TtsVoiceReferenceClip> = emptyList(),
    val providerBindings: List<ClonedVoiceBinding> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
