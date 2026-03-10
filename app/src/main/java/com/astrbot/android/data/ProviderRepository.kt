package com.astrbot.android.data

import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ProviderRepository {
    private val _providers = MutableStateFlow(
        listOf(
            ProviderProfile(
                id = "openai-chat",
                name = "OpenAI 对话",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4.1-mini",
                providerType = ProviderType.OPENAI_COMPATIBLE,
                apiKey = "",
                capabilities = setOf(ProviderCapability.CHAT),
            ),
            ProviderProfile(
                id = "deepseek-chat",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat",
                providerType = ProviderType.DEEPSEEK,
                apiKey = "",
                capabilities = setOf(ProviderCapability.CHAT),
            ),
        ),
    )

    val providers: StateFlow<List<ProviderProfile>> = _providers.asStateFlow()

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean = true,
    ): ProviderProfile {
        val resolvedId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val profile = ProviderProfile(
            id = resolvedId,
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            model = model.trim(),
            providerType = providerType,
            apiKey = apiKey.trim(),
            capabilities = capabilities,
            enabled = enabled,
        )
        val exists = _providers.value.any { it.id == resolvedId }
        _providers.value = if (exists) {
            _providers.value.map { item -> if (item.id == resolvedId) profile else item }
        } else {
            _providers.value + profile
        }
        RuntimeLogRepository.append(
            if (exists) {
                "Provider updated: ${profile.name} (${profile.providerType.name}, key=${maskState(profile.apiKey)})"
            } else {
                "Provider added: ${profile.name} (${profile.providerType.name}, key=${maskState(profile.apiKey)})"
            },
        )
        return profile
    }

    fun toggleEnabled(id: String) {
        _providers.value = _providers.value.map { item ->
            if (item.id == id) {
                val updated = item.copy(enabled = !item.enabled)
                RuntimeLogRepository.append("Provider toggled: ${updated.name} enabled=${updated.enabled}")
                updated
            } else {
                item
            }
        }
    }

    fun delete(id: String) {
        val removed = _providers.value.firstOrNull { it.id == id }
        _providers.value = _providers.value.filterNot { it.id == id }
        if (removed != null) {
            RuntimeLogRepository.append("Provider deleted: ${removed.name}")
        }
    }

    private fun maskState(apiKey: String): String {
        return if (apiKey.isBlank()) "empty" else "configured"
    }
}
