package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus

@Entity(tableName = "plugin_catalog_sources")
data class PluginCatalogSourceEntity(
    @PrimaryKey val sourceId: String,
    val title: String,
    val catalogUrl: String,
    val updatedAt: Long,
    val lastSyncAtEpochMillis: Long?,
    val lastSyncStatus: String = PluginCatalogSyncStatus.NEVER_SYNCED.name,
    val lastSyncErrorSummary: String = "",
)
