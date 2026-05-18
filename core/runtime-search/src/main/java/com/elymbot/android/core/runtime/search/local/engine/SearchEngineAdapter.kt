package com.elymbot.android.core.runtime.search.local.engine

import com.elymbot.android.core.runtime.search.local.EngineSearchRequest
import com.elymbot.android.core.runtime.search.local.EngineSearchResult
import com.elymbot.android.core.runtime.search.local.SearchEngineCapability

interface SearchEngineAdapter {
    val id: String
    val displayName: String
    val capabilities: Set<SearchEngineCapability>

    suspend fun search(request: EngineSearchRequest): EngineSearchResult
}
