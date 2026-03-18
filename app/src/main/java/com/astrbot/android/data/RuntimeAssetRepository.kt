package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.R
import com.astrbot.android.model.RuntimeAssetCatalogItem
import com.astrbot.android.model.RuntimeAssetEntryState
import com.astrbot.android.model.RuntimeAssetId
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.runtime.BridgeCommandRunner
import com.astrbot.android.runtime.ContainerRuntimeInstaller
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeAssetRepository {
    private val assetCatalog = listOf(
        RuntimeAssetCatalogItem(
            id = RuntimeAssetId.TTS,
            titleRes = R.string.asset_tts_title,
            subtitleRes = R.string.asset_tts_list_subtitle,
            descriptionRes = R.string.asset_tts_desc,
        ),
    )

    private val _state = MutableStateFlow(RuntimeAssetState(assets = assetCatalog.map(::buildInitialEntry)))
    val state: StateFlow<RuntimeAssetState> = _state.asStateFlow()

    fun initialize(context: Context) {
        refresh(context.applicationContext)
    }

    fun getAssetOrNull(assetId: String): RuntimeAssetEntryState? {
        val id = RuntimeAssetId.fromValue(assetId) ?: return null
        return _state.value.assets.firstOrNull { it.catalog.id == id }
    }

    suspend fun downloadAsset(context: Context, assetId: String) {
        val id = RuntimeAssetId.fromValue(assetId) ?: return
        when (id) {
            RuntimeAssetId.TTS -> downloadTtsAssets(context.applicationContext)
        }
    }

    suspend fun clearAsset(context: Context, assetId: String) {
        val id = RuntimeAssetId.fromValue(assetId) ?: return
        when (id) {
            RuntimeAssetId.TTS -> clearTtsAssets(context.applicationContext)
        }
    }

    fun refresh(context: Context) {
        refresh(context.applicationContext, detailsOverrides = emptyMap())
    }

    private suspend fun downloadTtsAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.TTS,
            installed = false,
            busy = true,
            lastAction = "Downloading",
            details = "Preparing TTS conversion assets. The first download can take several minutes.",
        )
        try {
            ContainerRuntimeInstaller.ensureInstalled(context)
            val scriptFile = File(context.filesDir, "runtime/scripts/prepare_tts_assets.sh")
            val command = buildString {
                append("/system/bin/sh ")
                append(scriptFile.absolutePath.shellQuote())
                append(' ')
                append(context.filesDir.absolutePath.shellQuote())
                append(' ')
                append(context.applicationInfo.nativeLibraryDir.shellQuote())
            }
            RuntimeLogRepository.append("Runtime assets download requested: TTS conversion")
            val result = BridgeCommandRunner.execute(command)
            if (result.exitCode == 0 && ttsMarkerFile(context).exists()) {
                val details = parseDownloadSummary(result.stdout)
                    ?: "TTS conversion assets downloaded."
                refresh(context, detailsOverrides = mapOf(RuntimeAssetId.TTS to details))
                RuntimeLogRepository.append("Runtime assets download finished: $details")
                updateAsset(RuntimeAssetId.TTS, lastAction = "Downloaded")
            } else {
                val message = result.stderr.ifBlank { result.stdout }.ifBlank { "Unknown error" }
                RuntimeLogRepository.append("Runtime assets download failed: $message")
                updateAsset(
                    RuntimeAssetId.TTS,
                    busy = false,
                    lastAction = "Download failed",
                    details = message,
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            RuntimeLogRepository.append("Runtime assets download exception: $message")
            updateAsset(
                RuntimeAssetId.TTS,
                busy = false,
                lastAction = "Download failed",
                details = message,
            )
        }
    }

    private suspend fun clearTtsAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.TTS,
            installed = true,
            busy = true,
            lastAction = "Clearing",
            details = "Removing downloaded TTS conversion assets.",
        )
        try {
            ContainerRuntimeInstaller.ensureInstalled(context)
            val scriptFile = File(context.filesDir, "runtime/scripts/clear_tts_assets.sh")
            val command = buildString {
                append("/system/bin/sh ")
                append(scriptFile.absolutePath.shellQuote())
                append(' ')
                append(context.filesDir.absolutePath.shellQuote())
                append(' ')
                append(context.applicationInfo.nativeLibraryDir.shellQuote())
            }
            RuntimeLogRepository.append("Runtime assets clear requested: TTS conversion")
            val result = BridgeCommandRunner.execute(command)
            if (result.exitCode == 0) {
                refresh(context, detailsOverrides = mapOf(RuntimeAssetId.TTS to "TTS conversion assets removed."))
                RuntimeLogRepository.append("Runtime assets cleared: TTS conversion")
                updateAsset(RuntimeAssetId.TTS, lastAction = "Cleared")
            } else {
                val message = result.stderr.ifBlank { result.stdout }.ifBlank { "Unknown error" }
                RuntimeLogRepository.append("Runtime assets clear failed: $message")
                updateAsset(
                    RuntimeAssetId.TTS,
                    busy = false,
                    lastAction = "Clear failed",
                    details = message,
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            RuntimeLogRepository.append("Runtime assets clear exception: $message")
            updateAsset(
                RuntimeAssetId.TTS,
                busy = false,
                lastAction = "Clear failed",
                details = message,
            )
        }
    }

    private fun refresh(
        context: Context,
        detailsOverrides: Map<RuntimeAssetId, String>,
    ) {
        val previous = _state.value.assets.associateBy { it.catalog.id }
        val refreshed = assetCatalog.map { catalog ->
            val oldEntry = previous[catalog.id]
            val installed = isInstalled(context, catalog.id)
            RuntimeAssetEntryState(
                catalog = catalog,
                installed = installed,
                busy = false,
                lastAction = oldEntry?.lastAction.orEmpty(),
                details = detailsOverrides[catalog.id] ?: defaultDetails(catalog.id, installed),
            )
        }
        _state.value = RuntimeAssetState(assets = refreshed)
    }

    private fun isInstalled(context: Context, assetId: RuntimeAssetId): Boolean = when (assetId) {
        RuntimeAssetId.TTS -> ttsMarkerFile(context).exists()
    }

    private fun defaultDetails(assetId: RuntimeAssetId, installed: Boolean): String = when (assetId) {
        RuntimeAssetId.TTS -> if (installed) {
            "TTS conversion assets are ready."
        } else {
            "TTS conversion assets are not downloaded."
        }
    }

    private fun buildInitialEntry(catalog: RuntimeAssetCatalogItem): RuntimeAssetEntryState = RuntimeAssetEntryState(
        catalog = catalog,
        details = defaultDetails(catalog.id, installed = false),
    )

    private fun updateAsset(
        assetId: RuntimeAssetId,
        installed: Boolean? = null,
        busy: Boolean? = null,
        lastAction: String? = null,
        details: String? = null,
    ) {
        _state.value = RuntimeAssetState(
            assets = _state.value.assets.map { entry ->
                if (entry.catalog.id != assetId) {
                    entry
                } else {
                    entry.copy(
                        installed = installed ?: entry.installed,
                        busy = busy ?: entry.busy,
                        lastAction = lastAction ?: entry.lastAction,
                        details = details ?: entry.details,
                    )
                }
            },
        )
    }

    private fun ttsMarkerFile(context: Context): File {
        return File(context.filesDir, "runtime/rootfs/ubuntu/root/.astrbot-tts-assets-ready")
    }

    private fun String.shellQuote(): String {
        return "'" + replace("'", "'\"'\"'") + "'"
    }

    private fun parseDownloadSummary(stdout: String): String? {
        val summary = stdout.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("ASTRBOT_TTS_ASSET_SUMMARY:") }
            ?.removePrefix("ASTRBOT_TTS_ASSET_SUMMARY:")
            ?.trim()
            .orEmpty()
        if (summary.isBlank()) return null
        return "TTS conversion assets downloaded. $summary"
    }
}
