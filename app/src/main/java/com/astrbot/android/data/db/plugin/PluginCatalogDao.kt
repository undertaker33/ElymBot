package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class PluginCatalogDao {
    @Query("SELECT * FROM plugin_catalog_sources ORDER BY sourceId ASC")
    abstract suspend fun listSources(): List<PluginCatalogSourceEntity>

    @Query("SELECT * FROM plugin_catalog_sources WHERE sourceId = :sourceId")
    abstract suspend fun getSource(sourceId: String): PluginCatalogSourceEntity?

    @Query("SELECT * FROM plugin_catalog_entries WHERE sourceId = :sourceId ORDER BY sortIndex ASC, pluginId ASC")
    abstract suspend fun listEntries(sourceId: String): List<PluginCatalogEntryEntity>

    @Query("SELECT * FROM plugin_catalog_entries WHERE sourceId = :sourceId AND pluginId = :pluginId")
    abstract suspend fun getEntry(sourceId: String, pluginId: String): PluginCatalogEntryEntity?

    @Query(
        """
        SELECT * FROM plugin_catalog_versions
        WHERE sourceId = :sourceId AND pluginId = :pluginId
        ORDER BY sortIndex ASC, version DESC
        """,
    )
    abstract suspend fun listVersions(sourceId: String, pluginId: String): List<PluginCatalogVersionEntity>

    @Upsert
    abstract suspend fun upsertSources(entities: List<PluginCatalogSourceEntity>)

    @Upsert
    abstract suspend fun upsertEntries(entities: List<PluginCatalogEntryEntity>)

    @Upsert
    abstract suspend fun upsertVersions(entities: List<PluginCatalogVersionEntity>)

    @Query("DELETE FROM plugin_catalog_entries WHERE sourceId = :sourceId")
    abstract suspend fun deleteEntriesBySourceId(sourceId: String)

    @Query("DELETE FROM plugin_catalog_versions WHERE sourceId = :sourceId")
    abstract suspend fun deleteVersionsBySourceId(sourceId: String)

    @Transaction
    open suspend fun replaceCatalog(
        source: PluginCatalogSourceEntity,
        entries: List<PluginCatalogEntryEntity>,
        versions: List<PluginCatalogVersionEntity>,
    ) {
        upsertSources(listOf(source))
        deleteVersionsBySourceId(source.sourceId)
        deleteEntriesBySourceId(source.sourceId)
        if (entries.isNotEmpty()) {
            upsertEntries(entries)
        }
        if (versions.isNotEmpty()) {
            upsertVersions(versions)
        }
    }
}
