package com.astrbot.android.data.db

import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRiskLevel
import org.json.JSONArray
import org.json.JSONObject

data class PluginCatalogWriteModel(
    val source: PluginCatalogSourceEntity,
    val entries: List<PluginCatalogEntryEntity>,
    val versions: List<PluginCatalogVersionEntity>,
)

fun PluginRepositorySource.toWriteModel(): PluginCatalogWriteModel {
    val entryEntities = plugins.mapIndexed { index, entry ->
        entry.toEntity(sourceId = sourceId, sortIndex = index)
    }
    val versionEntities = plugins.flatMap { entry ->
        entry.versions.mapIndexed { index, version ->
            version.toEntity(sourceId = sourceId, pluginId = entry.pluginId, sortIndex = index)
        }
    }
    return PluginCatalogWriteModel(
        source = PluginCatalogSourceEntity(
            sourceId = sourceId,
            title = title,
            catalogUrl = catalogUrl,
            updatedAt = updatedAt,
            lastSyncAtEpochMillis = lastSyncAtEpochMillis,
            lastSyncStatus = lastSyncStatus.name,
            lastSyncErrorSummary = lastSyncErrorSummary,
        ),
        entries = entryEntities,
        versions = versionEntities,
    )
}

fun PluginCatalogSourceEntity.toModel(entries: List<PluginCatalogEntry>): PluginRepositorySource {
    return PluginRepositorySource(
        sourceId = sourceId,
        title = title,
        catalogUrl = catalogUrl,
        updatedAt = updatedAt,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        lastSyncStatus = lastSyncStatus.toSyncStatus(),
        lastSyncErrorSummary = lastSyncErrorSummary,
        plugins = entries,
    )
}

fun PluginCatalogSourceEntity.toSyncState(): PluginCatalogSyncState {
    return PluginCatalogSyncState(
        sourceId = sourceId,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        lastSyncStatus = lastSyncStatus.toSyncStatus(),
        lastSyncErrorSummary = lastSyncErrorSummary,
    )
}

fun PluginRepositorySource.toEntryRecord(entry: PluginCatalogEntry): PluginCatalogEntryRecord {
    return PluginCatalogEntryRecord(
        sourceId = sourceId,
        sourceTitle = title,
        catalogUrl = catalogUrl,
        entry = entry,
    )
}

fun PluginCatalogEntryEntity.toModel(versions: List<PluginCatalogVersion>): PluginCatalogEntry {
    return PluginCatalogEntry(
        pluginId = pluginId,
        title = title,
        author = author,
        description = description,
        entrySummary = entrySummary,
        scenarios = scenariosJson.decodeStringList(),
        versions = versions,
    )
}

fun PluginCatalogVersionEntity.toModel(): PluginCatalogVersion {
    return PluginCatalogVersion(
        version = version,
        packageUrl = packageUrl,
        publishedAt = publishedAt,
        protocolVersion = protocolVersion,
        minHostVersion = minHostVersion,
        maxHostVersion = maxHostVersion,
        permissions = permissionsJson.decodePermissions(),
        changelog = changelog,
    )
}

private fun PluginCatalogEntry.toEntity(sourceId: String, sortIndex: Int): PluginCatalogEntryEntity {
    return PluginCatalogEntryEntity(
        sourceId = sourceId,
        pluginId = pluginId,
        title = title,
        author = author,
        description = description,
        entrySummary = entrySummary,
        scenariosJson = scenarios.encodeAsJsonArray(),
        sortIndex = sortIndex,
    )
}

private fun PluginCatalogVersion.toEntity(
    sourceId: String,
    pluginId: String,
    sortIndex: Int,
): PluginCatalogVersionEntity {
    return PluginCatalogVersionEntity(
        sourceId = sourceId,
        pluginId = pluginId,
        version = version,
        packageUrl = packageUrl,
        publishedAt = publishedAt,
        protocolVersion = protocolVersion,
        minHostVersion = minHostVersion,
        maxHostVersion = maxHostVersion,
        permissionsJson = permissions.encodePermissionsAsJson(),
        changelog = changelog,
        sortIndex = sortIndex,
    )
}

private fun List<String>.encodeAsJsonArray(): String {
    return JSONArray().apply {
        forEach(::put)
    }.toString()
}

private fun String.decodeStringList(): List<String> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return buildList {
        for (index in 0 until array.length()) {
            add(array.optString(index))
        }
    }
}

private fun List<PluginPermissionDeclaration>.encodePermissionsAsJson(): String {
    return JSONArray().apply {
        forEach { permission ->
            put(
                JSONObject().apply {
                    put("permissionId", permission.permissionId)
                    put("title", permission.title)
                    put("description", permission.description)
                    put("riskLevel", permission.riskLevel.name)
                    put("required", permission.required)
                },
            )
        }
    }.toString()
}

private fun String.decodePermissions(): List<PluginPermissionDeclaration> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index)
                ?: throw IllegalArgumentException("permissions[$index] must be an object")
            add(
                PluginPermissionDeclaration(
                    permissionId = json.optString("permissionId"),
                    title = json.optString("title"),
                    description = json.optString("description"),
                    riskLevel = json.optString("riskLevel")
                        .takeIf { it.isNotBlank() }
                        ?.let { PluginRiskLevel.valueOf(it) }
                        ?: PluginRiskLevel.MEDIUM,
                    required = json.optBoolean("required", true),
                ),
            )
        }
    }
}

private fun String.toSyncStatus(): PluginCatalogSyncStatus {
    return runCatching { PluginCatalogSyncStatus.valueOf(this) }
        .getOrDefault(PluginCatalogSyncStatus.NEVER_SYNCED)
}
