package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "plugin_catalog_versions",
    primaryKeys = ["sourceId", "pluginId", "version"],
    indices = [
        Index(value = ["sourceId", "pluginId", "sortIndex"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PluginCatalogEntryEntity::class,
            parentColumns = ["sourceId", "pluginId"],
            childColumns = ["sourceId", "pluginId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PluginCatalogVersionEntity(
    val sourceId: String,
    val pluginId: String,
    val version: String,
    val packageUrl: String,
    val publishedAt: Long,
    val protocolVersion: Int,
    val minHostVersion: String,
    val maxHostVersion: String,
    val permissionsJson: String,
    val changelog: String,
    val sortIndex: Int,
)
