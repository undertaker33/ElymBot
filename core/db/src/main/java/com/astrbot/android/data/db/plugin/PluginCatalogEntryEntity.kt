package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "plugin_catalog_entries",
    primaryKeys = ["sourceId", "pluginId"],
    indices = [
        Index(value = ["sourceId", "sortIndex"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PluginCatalogSourceEntity::class,
            parentColumns = ["sourceId"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PluginCatalogEntryEntity(
    val sourceId: String,
    val pluginId: String,
    val title: String,
    val author: String,
    val description: String,
    val entrySummary: String,
    val scenariosJson: String,
    val sortIndex: Int,
)
