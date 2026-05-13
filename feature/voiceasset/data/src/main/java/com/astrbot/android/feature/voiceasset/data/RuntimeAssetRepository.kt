package com.astrbot.android.feature.voiceasset.data

import android.content.Context
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.audio.AudioAssetSubState
import com.astrbot.android.core.runtime.audio.AudioTtsAssetState
import com.astrbot.android.core.runtime.audio.SherpaOnnxAssetService
import com.astrbot.android.core.runtime.container.CommandRunner
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstallerPort
import com.astrbot.android.core.runtime.container.ContainerRuntimeScript
import com.astrbot.android.core.runtime.container.ContainerRuntimeScripts
import com.astrbot.android.download.DownloadManagerPort
import com.astrbot.android.download.DownloadOwnerType
import com.astrbot.android.download.DownloadRequest
import com.astrbot.android.feature.voiceasset.api.R
import com.astrbot.android.feature.voiceasset.api.RuntimeAssetPort
import com.astrbot.android.feature.voiceasset.api.RuntimeAssetSubState
import com.astrbot.android.feature.voiceasset.api.RuntimeAssetTtsState
import com.astrbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import com.astrbot.android.feature.voiceasset.api.model.RuntimeAssetCatalogItem
import com.astrbot.android.feature.voiceasset.api.model.RuntimeAssetEntryState
import com.astrbot.android.feature.voiceasset.api.model.RuntimeAssetId
import com.astrbot.android.feature.voiceasset.api.model.RuntimeAssetState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RuntimeAssetStateOwner @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val containerRuntimeInstaller: ContainerRuntimeInstallerPort,
    private val commandRunner: CommandRunner,
    private val downloadManager: DownloadManagerPort,
    private val sherpaOnnxAssetService: SherpaOnnxAssetService,
    private val runtimeLogger: RuntimeLogger,
    @Suppress("unused") private val ttsVoiceAssetPort: TtsVoiceAssetPort,
) : RuntimeAssetPort {
    private val _state = MutableStateFlow(RuntimeAssetState(assets = assetCatalog.map(::buildInitialEntry)))

    override val state: StateFlow<RuntimeAssetState> = _state.asStateFlow()

    init {
        sherpaOnnxAssetService.clearDeprecatedTtsAssets(appContext)
        refreshInternal(detailsOverrides = emptyMap())
    }

    fun getAssetOrNull(assetId: String): RuntimeAssetEntryState? {
        val id = RuntimeAssetId.fromValue(assetId) ?: return null
        return _state.value.assets.firstOrNull { it.catalog.id == id }
    }

    override fun refresh(@Suppress("UNUSED_PARAMETER") context: Context) {
        refreshInternal(detailsOverrides = emptyMap())
    }

    override suspend fun downloadAsset(@Suppress("UNUSED_PARAMETER") context: Context, assetId: String) {
        val id = RuntimeAssetId.fromValue(assetId) ?: return
        when (id) {
            RuntimeAssetId.TTS -> downloadTtsAssets(appContext)
            RuntimeAssetId.ON_DEVICE_FRAMEWORK -> downloadOnDeviceFramework(appContext)
            RuntimeAssetId.ON_DEVICE_STT -> downloadOnDeviceStt(appContext)
            RuntimeAssetId.ON_DEVICE_TTS,
            RuntimeAssetId.TTS_VOICE_ASSETS,
            -> Unit
        }
    }

    override suspend fun clearAsset(@Suppress("UNUSED_PARAMETER") context: Context, assetId: String) {
        val id = RuntimeAssetId.fromValue(assetId) ?: return
        when (id) {
            RuntimeAssetId.TTS -> clearTtsAssets(appContext)
            RuntimeAssetId.ON_DEVICE_FRAMEWORK -> clearOnDeviceFramework(appContext)
            RuntimeAssetId.ON_DEVICE_STT -> clearOnDeviceStt(appContext)
            RuntimeAssetId.ON_DEVICE_TTS,
            RuntimeAssetId.TTS_VOICE_ASSETS,
            -> Unit
        }
    }

    override suspend fun downloadOnDeviceTtsModel(
        @Suppress("UNUSED_PARAMETER") context: Context,
        modelId: String,
    ) {
        when (modelId.trim().lowercase()) {
            "kokoro" -> downloadKokoroAssets(appContext)
        }
    }

    override suspend fun clearOnDeviceTtsModel(
        @Suppress("UNUSED_PARAMETER") context: Context,
        modelId: String,
    ) {
        when (modelId.trim().lowercase()) {
            "kokoro" -> clearKokoroAssets(appContext)
        }
    }

    override fun ttsAssetState(@Suppress("UNUSED_PARAMETER") context: Context): RuntimeAssetTtsState {
        return sherpaOnnxAssetService.ttsState(appContext).toRuntimeAssetTtsState()
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
            containerRuntimeInstaller.ensureInstalled()
            runtimeLogger.append("Runtime assets download requested: TTS conversion")
            val result = commandRunner.execute(
                ContainerRuntimeScripts.command(
                    filesDir = context.filesDir,
                    nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                    script = ContainerRuntimeScript.PREPARE_TTS_ASSETS,
                ),
            )
            if (result.exitCode == 0 && ttsMarkerFile(context).exists()) {
                val details = parseDownloadSummary(result.stdout)
                    ?: "TTS conversion assets downloaded."
                refreshInternal(detailsOverrides = mapOf(RuntimeAssetId.TTS to details))
                runtimeLogger.append("Runtime assets download finished: $details")
                updateAsset(RuntimeAssetId.TTS, lastAction = "Downloaded")
            } else {
                val message = result.stderr.ifBlank { result.stdout }.ifBlank { "Unknown error" }
                runtimeLogger.append("Runtime assets download failed: $message")
                updateAsset(
                    RuntimeAssetId.TTS,
                    busy = false,
                    lastAction = "Download failed",
                    details = message,
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            runtimeLogger.append("Runtime assets download exception: $message")
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
            containerRuntimeInstaller.ensureInstalled()
            runtimeLogger.append("Runtime assets clear requested: TTS conversion")
            val result = commandRunner.execute(
                ContainerRuntimeScripts.command(
                    filesDir = context.filesDir,
                    nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                    script = ContainerRuntimeScript.CLEAR_TTS_ASSETS,
                ),
            )
            if (result.exitCode == 0) {
                refreshInternal(detailsOverrides = mapOf(RuntimeAssetId.TTS to "TTS conversion assets removed."))
                runtimeLogger.append("Runtime assets cleared: TTS conversion")
                updateAsset(RuntimeAssetId.TTS, lastAction = "Cleared")
            } else {
                val message = result.stderr.ifBlank { result.stdout }.ifBlank { "Unknown error" }
                runtimeLogger.append("Runtime assets clear failed: $message")
                updateAsset(
                    RuntimeAssetId.TTS,
                    busy = false,
                    lastAction = "Clear failed",
                    details = message,
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            runtimeLogger.append("Runtime assets clear exception: $message")
            updateAsset(
                RuntimeAssetId.TTS,
                busy = false,
                lastAction = "Clear failed",
                details = message,
            )
        }
    }

    private suspend fun downloadOnDeviceFramework(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_FRAMEWORK,
            installed = false,
            busy = true,
            lastAction = "Downloading",
            details = "Activating the bundled Sherpa ONNX Android runtime.",
        )
        try {
            sherpaOnnxAssetService.ensureFrameworkActivated(context)
            refreshInternal(detailsOverrides = emptyMap())
            updateAsset(RuntimeAssetId.ON_DEVICE_FRAMEWORK, lastAction = "Downloaded")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_FRAMEWORK,
                busy = false,
                lastAction = "Download failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun clearOnDeviceFramework(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_FRAMEWORK,
            installed = true,
            busy = true,
            lastAction = "Clearing",
            details = "Removing the Sherpa ONNX activation marker and local model assets.",
        )
        try {
            sherpaOnnxAssetService.clearFramework(context)
            refreshInternal(detailsOverrides = emptyMap())
            updateAsset(RuntimeAssetId.ON_DEVICE_FRAMEWORK, lastAction = "Cleared")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_FRAMEWORK,
                busy = false,
                lastAction = "Clear failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun downloadOnDeviceStt(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_STT,
            installed = false,
            busy = true,
            lastAction = "Downloading",
            details = "Downloading Sherpa ONNX STT assets.",
        )
        try {
            val archiveFile = sherpaOnnxAssetService.sttArchiveFile(context)
            downloadManager.enqueue(
                DownloadRequest(
                    taskKey = STT_DOWNLOAD_TASK_KEY,
                    url = STT_DOWNLOAD_URL,
                    targetFilePath = archiveFile.absolutePath,
                    displayName = "Sherpa STT",
                    ownerType = DownloadOwnerType.RUNTIME_ASSET,
                    ownerId = RuntimeAssetId.ON_DEVICE_STT.value,
                ),
            )
            downloadManager.awaitCompletion(STT_DOWNLOAD_TASK_KEY)
            sherpaOnnxAssetService.installSttAssetsFromArchive(context, archiveFile)
            refreshInternal(detailsOverrides = emptyMap())
            updateAsset(RuntimeAssetId.ON_DEVICE_STT, lastAction = "Downloaded")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_STT,
                busy = false,
                lastAction = "Download failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun clearOnDeviceStt(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_STT,
            installed = true,
            busy = true,
            lastAction = "Clearing",
            details = "Removing Sherpa ONNX STT assets.",
        )
        try {
            sherpaOnnxAssetService.clearSttAssets(context)
            refreshInternal(detailsOverrides = emptyMap())
            updateAsset(RuntimeAssetId.ON_DEVICE_STT, lastAction = "Cleared")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_STT,
                busy = false,
                lastAction = "Clear failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun downloadKokoroAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_TTS,
            busy = true,
            lastAction = "Downloading kokoro",
            details = "Downloading kokoro local TTS assets.",
        )
        try {
            val archiveFile = sherpaOnnxAssetService.kokoroArchiveFile(context)
            downloadManager.enqueue(
                DownloadRequest(
                    taskKey = KOKORO_DOWNLOAD_TASK_KEY,
                    url = KOKORO_DOWNLOAD_URL,
                    targetFilePath = archiveFile.absolutePath,
                    displayName = "Kokoro TTS",
                    ownerType = DownloadOwnerType.RUNTIME_ASSET,
                    ownerId = RuntimeAssetId.ON_DEVICE_TTS.value,
                ),
            )
            downloadManager.awaitCompletion(KOKORO_DOWNLOAD_TASK_KEY)
            sherpaOnnxAssetService.installKokoroAssetsFromArchive(context, archiveFile)
            check(sherpaOnnxAssetService.ttsState(context).kokoro.installed) {
                "Kokoro assets are still missing after download."
            }
            refreshInternal(detailsOverrides = emptyMap())
            updateAsset(RuntimeAssetId.ON_DEVICE_TTS, lastAction = "Downloaded kokoro")
        } catch (error: Exception) {
            runCatching { sherpaOnnxAssetService.clearKokoroAssets(context) }
            updateAsset(
                RuntimeAssetId.ON_DEVICE_TTS,
                busy = false,
                lastAction = "Download failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun clearKokoroAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_TTS,
            busy = true,
            lastAction = "Clearing kokoro",
            details = "Removing kokoro local TTS assets.",
        )
        try {
            sherpaOnnxAssetService.clearKokoroAssets(context)
            refreshInternal(detailsOverrides = emptyMap())
            updateAsset(RuntimeAssetId.ON_DEVICE_TTS, lastAction = "Cleared kokoro")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_TTS,
                busy = false,
                lastAction = "Clear failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun refreshInternal(
        detailsOverrides: Map<RuntimeAssetId, String>,
    ) {
        val previous = _state.value.assets.associateBy { it.catalog.id }
        val refreshed = assetCatalog.map { catalog ->
            val oldEntry = previous[catalog.id]
            val installed = isInstalled(appContext, catalog.id)
            RuntimeAssetEntryState(
                catalog = catalog,
                installed = installed,
                busy = false,
                lastAction = oldEntry?.lastAction.orEmpty(),
                details = detailsOverrides[catalog.id] ?: defaultDetails(appContext, catalog.id, installed),
            )
        }
        _state.value = RuntimeAssetState(assets = refreshed)
    }

    private fun isInstalled(context: Context, assetId: RuntimeAssetId): Boolean = when (assetId) {
        RuntimeAssetId.TTS -> ttsMarkerFile(context).exists()
        RuntimeAssetId.ON_DEVICE_FRAMEWORK -> sherpaOnnxAssetService.frameworkState(context).installed
        RuntimeAssetId.ON_DEVICE_STT -> sherpaOnnxAssetService.sttState(context).installed
        RuntimeAssetId.ON_DEVICE_TTS -> sherpaOnnxAssetService.ttsState(context).kokoro.installed
        RuntimeAssetId.TTS_VOICE_ASSETS,
        -> false
    }

    private fun defaultDetails(
        context: Context?,
        assetId: RuntimeAssetId,
        installed: Boolean,
    ): String = when (assetId) {
        RuntimeAssetId.TTS -> if (installed) {
            "TTS conversion assets are ready."
        } else {
            "TTS conversion assets are not downloaded."
        }
        RuntimeAssetId.ON_DEVICE_FRAMEWORK -> context?.let {
            sherpaOnnxAssetService.frameworkState(it).details
        } ?: "Activate the bundled Sherpa ONNX Android runtime before downloading local STT or TTS model assets."
        RuntimeAssetId.ON_DEVICE_STT -> context?.let {
            sherpaOnnxAssetService.sttState(it).details
        } ?: "Offline Paraformer STT assets are not downloaded."
        RuntimeAssetId.ON_DEVICE_TTS -> context?.let {
            val state = sherpaOnnxAssetService.ttsState(it)
            "Framework: ${state.framework.details} Kokoro: ${state.kokoro.details}"
        } ?: "Framework and kokoro asset status will appear after initialization."
        RuntimeAssetId.TTS_VOICE_ASSETS -> "Cloud TTS voice asset entry is ready. Reference audio import and clone management will be connected in this iteration."
    }

    private fun buildInitialEntry(catalog: RuntimeAssetCatalogItem): RuntimeAssetEntryState {
        return RuntimeAssetEntryState(
            catalog = catalog,
            details = defaultDetails(appContext, catalog.id, installed = false),
        )
    }

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

    companion object {
        private const val STT_DOWNLOAD_TASK_KEY = "asset:stt:paraformer-zh-small-2024-03-09"
        private const val STT_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
        private const val KOKORO_DOWNLOAD_TASK_KEY = "asset:tts:kokoro-int8-multi-lang-v1_1"
        private const val KOKORO_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"

        private val assetCatalog = listOf(
            RuntimeAssetCatalogItem(
                id = RuntimeAssetId.TTS,
                titleRes = R.string.asset_tts_title,
                subtitleRes = R.string.asset_tts_list_subtitle,
                descriptionRes = R.string.asset_tts_desc,
            ),
            RuntimeAssetCatalogItem(
                id = RuntimeAssetId.ON_DEVICE_FRAMEWORK,
                titleRes = R.string.asset_on_device_framework_title,
                subtitleRes = R.string.asset_on_device_framework_list_subtitle,
                descriptionRes = R.string.asset_on_device_framework_desc,
            ),
            RuntimeAssetCatalogItem(
                id = RuntimeAssetId.ON_DEVICE_STT,
                titleRes = R.string.asset_on_device_stt_title,
                subtitleRes = R.string.asset_on_device_stt_list_subtitle,
                descriptionRes = R.string.asset_on_device_stt_desc,
            ),
            RuntimeAssetCatalogItem(
                id = RuntimeAssetId.ON_DEVICE_TTS,
                titleRes = R.string.asset_on_device_tts_title,
                subtitleRes = R.string.asset_on_device_tts_list_subtitle,
                descriptionRes = R.string.asset_on_device_tts_desc,
            ),
            RuntimeAssetCatalogItem(
                id = RuntimeAssetId.TTS_VOICE_ASSETS,
                titleRes = R.string.asset_tts_voice_assets_title,
                subtitleRes = R.string.asset_tts_voice_assets_list_subtitle,
                descriptionRes = R.string.asset_tts_voice_assets_desc,
                actionsEnabled = false,
            ),
        )
    }
}

private fun AudioTtsAssetState.toRuntimeAssetTtsState(): RuntimeAssetTtsState {
    return RuntimeAssetTtsState(
        framework = framework.toRuntimeAssetSubState(),
        kokoro = kokoro.toRuntimeAssetSubState(),
    )
}

private fun AudioAssetSubState.toRuntimeAssetSubState(): RuntimeAssetSubState {
    return RuntimeAssetSubState(
        installed = installed,
        details = details,
    )
}
