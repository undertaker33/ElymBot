package com.astrbot.android.runtime.plugin.catalog

import com.astrbot.android.data.plugin.catalog.PluginCatalogJson
import com.astrbot.android.data.plugin.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginRepositorySource

class PluginCatalogSynchronizer(
    private val store: PluginCatalogSyncStore,
    private val fetcher: PluginCatalogFetcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val decode: (String) -> PluginRepositorySource = PluginCatalogJson::decodeRepositorySource,
) {
    suspend fun sync(sourceId: String): PluginCatalogSyncState {
        val subscribedSource = store.getRepositorySource(sourceId)
            ?: error("Plugin repository source not found for sourceId=$sourceId")

        val attemptAt = now()
        return runCatching {
            val rawJson = fetcher.fetch(subscribedSource.catalogUrl)
            val parsedSource = decode(rawJson)
            val normalized = parsedSource.normalizeForSubscription(
                sourceId = subscribedSource.sourceId,
                catalogUrl = subscribedSource.catalogUrl,
                lastSyncAtEpochMillis = attemptAt,
            )
            if (normalized.plugins.isEmpty()) {
                val emptySource = normalized.copy(lastSyncStatus = PluginCatalogSyncStatus.EMPTY)
                store.upsertRepositorySource(emptySource)
                emptySource.toSyncState()
            } else {
                val successSource = normalized.copy(lastSyncStatus = PluginCatalogSyncStatus.SUCCESS)
                store.replaceRepositoryCatalog(successSource)
                successSource.toSyncState()
            }
        }.getOrElse { failure ->
            val failedSource = subscribedSource.copy(
                lastSyncAtEpochMillis = attemptAt,
                lastSyncStatus = PluginCatalogSyncStatus.FAILED,
                lastSyncErrorSummary = failure.toErrorSummary(),
            )
            store.upsertRepositorySource(failedSource)
            failedSource.toSyncState()
        }
    }
}

private fun PluginRepositorySource.normalizeForSubscription(
    sourceId: String,
    catalogUrl: String,
    lastSyncAtEpochMillis: Long,
): PluginRepositorySource {
    return copy(
        sourceId = sourceId,
        catalogUrl = catalogUrl,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        lastSyncStatus = PluginCatalogSyncStatus.NEVER_SYNCED,
        lastSyncErrorSummary = "",
        plugins = plugins.map { entry ->
            entry.copy(
                versions = entry.versions.map { version ->
                    version.normalizeAgainstCatalog(catalogUrl)
                },
            )
        },
    )
}

private fun PluginCatalogVersion.normalizeAgainstCatalog(catalogUrl: String): PluginCatalogVersion {
    return copy(packageUrl = resolvePackageUrl(catalogUrl))
}

private fun PluginRepositorySource.toSyncState(): PluginCatalogSyncState {
    return PluginCatalogSyncState(
        sourceId = sourceId,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        lastSyncStatus = lastSyncStatus,
        lastSyncErrorSummary = lastSyncErrorSummary,
    )
}

private fun Throwable.toErrorSummary(): String {
    val message = message?.trim().orEmpty()
    return when {
        message.isNotBlank() -> message.take(MAX_ERROR_SUMMARY_LENGTH)
        else -> javaClass.simpleName.take(MAX_ERROR_SUMMARY_LENGTH)
    }
}

private const val MAX_ERROR_SUMMARY_LENGTH = 240
