package com.astrbot.android.core.runtime.search.local

import com.astrbot.android.core.runtime.search.local.engine.SearchEngineAdapter
import javax.inject.Inject

class SearchEngineRegistry @Inject constructor(
    private val engines: List<@JvmSuppressWildcards SearchEngineAdapter>,
) {
    private val byId: Map<String, SearchEngineAdapter> = engines.associateBy { it.id }

    fun get(engineId: String): SearchEngineAdapter? = byId[engineId]

    fun ordered(policy: LocalSearchPolicy): List<SearchEngineAdapter> {
        return policy.engineOrder.mapNotNull(::get)
    }
}
