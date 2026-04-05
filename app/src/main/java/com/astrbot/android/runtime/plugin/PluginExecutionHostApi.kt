package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.ExternalPluginWorkspacePolicy
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginConfigStorageJson
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.toStorageBoundary
import org.json.JSONArray
import org.json.JSONObject

data class PluginExecutionHostSnapshot(
    val runtimeKind: ExternalPluginRuntimeKind = ExternalPluginRuntimeKind.JsQuickJs,
    val bridgeMode: String = "compatibility_only",
    val workspaceSnapshot: PluginHostWorkspaceSnapshot = PluginHostWorkspaceSnapshot(),
    val configBoundary: PluginConfigStorageBoundary? = null,
    val configSnapshot: PluginConfigStoreSnapshot = PluginConfigStoreSnapshot(),
)

object PluginExecutionHostApi {
    const val WorkspaceApiKey = "host.workspace_api"
    const val ConfigBoundaryKey = "host.config_boundary"
    const val ConfigSnapshotKey = "host.config_snapshot"
    const val RuntimeKindKey = "host.runtime_kind"
    const val BridgeModeKey = "host.bridge_mode"
    const val ResultMergeModeKey = "host.result_merge_mode"

    fun resolve(pluginId: String): PluginExecutionHostSnapshot {
        val workspaceSnapshot = runCatching {
            val appContext = PluginRepository.requireAppContext()
            ExternalPluginWorkspacePolicy.snapshot(
                storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir),
                pluginId = pluginId,
            )
        }.getOrDefault(PluginHostWorkspaceSnapshot())
        val boundary = runCatching {
            PluginRepository.getInstalledStaticConfigSchema(pluginId)?.toStorageBoundary()
        }.getOrNull()
        val configSnapshot = boundary?.let { resolvedBoundary ->
            runCatching {
                PluginRepository.resolveConfigSnapshot(
                    pluginId = pluginId,
                    boundary = resolvedBoundary,
                )
            }.getOrDefault(PluginConfigStoreSnapshot())
        } ?: PluginConfigStoreSnapshot()
        return PluginExecutionHostSnapshot(
            workspaceSnapshot = workspaceSnapshot,
            configBoundary = boundary,
            configSnapshot = configSnapshot,
        )
    }

    fun inject(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext {
        val configExtras = linkedMapOf<String, String>().apply {
            putAll(context.config.extras)
            put(WorkspaceApiKey, encodeWorkspace(hostSnapshot.workspaceSnapshot))
            hostSnapshot.configBoundary?.let { boundary ->
                put(ConfigBoundaryKey, encodeBoundary(boundary))
            }
            if (
                hostSnapshot.configSnapshot.coreValues.isNotEmpty() ||
                hostSnapshot.configSnapshot.extensionValues.isNotEmpty()
            ) {
                put(ConfigSnapshotKey, encodeConfigSnapshot(hostSnapshot.configSnapshot))
            }
        }
        val triggerExtras = linkedMapOf<String, String>().apply {
            putAll(context.triggerMetadata.extras)
            put(RuntimeKindKey, hostSnapshot.runtimeKind.wireValue)
            put(BridgeModeKey, hostSnapshot.bridgeMode)
            put(ResultMergeModeKey, "ordered_chain_v1")
        }
        return context.copy(
            config = context.config.copy(extras = configExtras),
            triggerMetadata = context.triggerMetadata.copy(extras = triggerExtras),
        )
    }

    private fun encodeWorkspace(snapshot: PluginHostWorkspaceSnapshot): String {
        return JSONObject().apply {
            put("privateRootPath", snapshot.privateRootPath)
            put("importsPath", snapshot.importsPath)
            put("runtimePath", snapshot.runtimePath)
            put("exportsPath", snapshot.exportsPath)
            put("cachePath", snapshot.cachePath)
            put(
                "files",
                JSONArray().apply {
                    snapshot.files.forEach { file ->
                        put(
                            JSONObject().apply {
                                put("relativePath", file.relativePath)
                                put("sizeBytes", file.sizeBytes)
                                put("lastModifiedAtEpochMillis", file.lastModifiedAtEpochMillis)
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    private fun encodeBoundary(boundary: PluginConfigStorageBoundary): String {
        return JSONObject().apply {
            put(
                "coreFieldKeys",
                JSONArray().apply {
                    boundary.coreFieldKeys.sorted().forEach(::put)
                },
            )
            put(
                "extensionFieldKeys",
                JSONArray().apply {
                    boundary.extensionFieldKeys.sorted().forEach(::put)
                },
            )
        }.toString()
    }

    private fun encodeConfigSnapshot(snapshot: PluginConfigStoreSnapshot): String {
        return JSONObject().apply {
            put("coreValues", JSONObject(PluginConfigStorageJson.encodeValues(snapshot.coreValues)))
            put("extensionValues", JSONObject(PluginConfigStorageJson.encodeValues(snapshot.extensionValues)))
        }.toString()
    }
}
