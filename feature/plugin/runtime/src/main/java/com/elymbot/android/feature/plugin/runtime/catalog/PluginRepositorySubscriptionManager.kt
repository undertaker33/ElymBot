package com.elymbot.android.feature.plugin.runtime.catalog

import com.elymbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.model.plugin.PluginCatalogSyncState
import com.elymbot.android.model.plugin.PluginInstallIntent
import com.elymbot.android.model.plugin.PluginRepositorySource
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

data class PluginRepositorySubscriptionResult(
    val source: PluginRepositorySource,
    val syncState: PluginCatalogSyncState,
)

class PluginRepositorySubscriptionManager @Inject constructor(
    private val store: PluginCatalogSyncStore,
    private val synchronizer: PluginCatalogSynchronizer,
    private val runtimeLogger: RuntimeLogger,
) {
    private var sourceIdFactory: (String) -> String = ::defaultPluginRepositorySourceId
    private var now: () -> Long = System::currentTimeMillis

    constructor(
        store: PluginCatalogSyncStore,
        synchronizer: PluginCatalogSynchronizer,
        sourceIdFactory: (String) -> String = ::defaultPluginRepositorySourceId,
        now: () -> Long = System::currentTimeMillis,
        runtimeLogger: RuntimeLogger = RuntimeLogger.noop(),
    ) : this(store = store, synchronizer = synchronizer, runtimeLogger = runtimeLogger) {
        this.sourceIdFactory = sourceIdFactory
        this.now = now
    }

    suspend fun subscribeAndSync(rawCatalogUrl: String): PluginRepositorySubscriptionResult {
        runtimeLogger.append("Plugin market subscribe start: rawUrl=$rawCatalogUrl")
        val intent = PluginInstallIntent.repositoryUrl(rawCatalogUrl)
        val catalogUrl = intent.url
        val existing = store.listRepositorySources().firstOrNull { it.catalogUrl == catalogUrl }
        val source = existing ?: PluginRepositorySource(
            sourceId = sourceIdFactory(catalogUrl),
            title = buildRepositoryTitle(catalogUrl),
            catalogUrl = catalogUrl,
            updatedAt = now(),
        )
        runtimeLogger.append(
            "Plugin market subscribe normalized: " +
                "catalogUrl=$catalogUrl " +
                "existing=${existing != null} " +
                "sourceId=${source.sourceId}",
        )
        store.upsertRepositorySource(source)
        val syncState = synchronizer.sync(source.sourceId)
        val refreshed = store.getRepositorySource(source.sourceId) ?: source
        runtimeLogger.append(
            "Plugin market subscribe finished: " +
                "sourceId=${refreshed.sourceId} " +
                "status=${syncState.lastSyncStatus.name} " +
                "plugins=${refreshed.plugins.size}",
        )
        return PluginRepositorySubscriptionResult(
            source = refreshed,
            syncState = syncState,
        )
    }
}

private fun buildRepositoryTitle(catalogUrl: String): String {
    val uri = URI(catalogUrl)
    val host = uri.host?.takeIf { it.isNotBlank() } ?: catalogUrl
    return host.removePrefix("www.")
}

private fun defaultPluginRepositorySourceId(catalogUrl: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(catalogUrl.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(12)
    return "repo-$digest"
}

