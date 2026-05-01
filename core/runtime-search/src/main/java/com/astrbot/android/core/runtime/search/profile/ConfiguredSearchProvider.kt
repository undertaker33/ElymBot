package com.astrbot.android.core.runtime.search.profile

import com.astrbot.android.core.runtime.search.SearchProviderResult
import com.astrbot.android.core.runtime.search.UnifiedSearchRequest

interface ConfiguredSearchProvider {
    val providerId: String
    val providerName: String

    fun supports(profile: ConfiguredSearchProfile): Boolean

    suspend fun search(
        profile: ConfiguredSearchProfile,
        request: UnifiedSearchRequest,
    ): SearchProviderResult
}

data class ConfiguredSearchProfile(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val providerType: ConfiguredSearchProviderType,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

enum class ConfiguredSearchProviderType {
    TAVILY_SEARCH,
    BRAVE_SEARCH,
    BOCHA_SEARCH,
    BAIDU_AI_SEARCH,
}
