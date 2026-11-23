package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.content.Intent
import android.os.Build
import com.elymbot.android.ui.AppUpdateCandidate
import com.elymbot.android.ui.AppUpdateInstallFailure
import com.elymbot.android.ui.AppUpdateInstallResult
import com.elymbot.android.ui.AppUpdateInstallTicket
import com.elymbot.android.ui.AppUpdateState
import com.elymbot.android.ui.AppUpdateUiFailure
import com.elymbot.android.ui.AppUpdateUiStatus
import com.elymbot.android.ui.InstalledPluginSummary
import com.elymbot.android.ui.PluginDistributionFailure
import com.elymbot.android.ui.PluginDistributionStatus
import com.elymbot.android.ui.PluginManagementActionResult
import com.elymbot.android.ui.PluginManagementFailure
import com.elymbot.android.ui.PluginManagementState
import com.elymbot.android.ui.PluginReleaseOption
import com.elymbot.android.ui.PluginReleaseReference
import com.elymbot.android.ui.PluginRuntimeAvailability
import com.elymbot.android.ui.PluginTrustConfigurationFailure
import com.elymbot.android.ui.PluginTrustConfigurationStatus
import com.elymbot.core.contract.ToolDefinition
import com.elymbot.core.contract.ToolExecutionContext
import com.elymbot.core.contract.ToolExecutionResult
import com.elymbot.core.contract.ToolRegistration
import com.elymbot.core.contract.ToolResultStatus
import com.elymbot.core.contract.ToolScope
import com.elymbot.core.contract.ToolSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** App-owned workflows for trusted plugin distribution and user-confirmed APK installation. */
internal class AppHostManagementUiService(
    context: Context,
    private val runtimeManager: PluginRuntimeManager,
    private val configurationLoad: OfficialPluginDistributionConfigurationLoad,
    private val distributionRepository: OfficialPluginDistributionRepository,
    private val appUpdateService: OfficialAppUpdateService,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val stateLock = Any()
    private val updateCheckLock = Mutex()
    private val directoryLock = Mutex()
    private val updateOffers = mutableMapOf<String, AppUpdateOffer>()
    private val verifiedUpdates = mutableMapOf<String, VerifiedAppUpdateApk>()
    private val updateTickets = appContext.getSharedPreferences(UPDATE_TICKETS_PREFERENCES, Context.MODE_PRIVATE)
    private var directorySnapshot: PluginDirectorySnapshot? = null
    private var completedUpdateCheck = false

    private val mutablePluginManagement = MutableStateFlow(initialPluginManagementState())
    val pluginManagement: StateFlow<PluginManagementState> = mutablePluginManagement.asStateFlow()

    private val mutableAppUpdate = MutableStateFlow(AppUpdateState())
    val appUpdate: StateFlow<AppUpdateState> = mutableAppUpdate.asStateFlow()

    val catalogTool: ToolSource = OfficialPluginCatalogToolSource(this)

    init {
        scope.launch { checkForAppUpdate(forceRefresh = false) }
        if (configurationLoad is OfficialPluginDistributionConfigurationLoad.Ready) {
            scope.launch { refreshPluginDirectory() }
        }
    }

    suspend fun restorePlugins() {
        try {
            runtimeManager.restore(distributionRepository::resolveExpectation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // An unavailable optional package must not prevent the base App database from opening.
        } finally {
            publishPluginManagementState()
        }
    }

    suspend fun refreshPluginDirectory() {
        directoryLock.withLock {
            directorySnapshot = distributionRepository.refreshPluginDirectory()
            publishPluginManagementState()
        }
    }

    suspend fun installPlugin(reference: PluginReleaseReference): PluginManagementActionResult {
        if (!runtimeManager.isRestorationComplete) {
            return PluginManagementActionResult.Rejected(PluginManagementFailure.RuntimeUnavailable)
        }
        val currentOption = mutablePluginManagement.value.releases.singleOrNull {
            it.reference == reference
        } ?: return PluginManagementActionResult.Rejected(PluginManagementFailure.ReleaseNotVerified)
        if (!currentOption.compatible) {
            return PluginManagementActionResult.Rejected(PluginManagementFailure.IncompatibleRelease)
        }
        val packageReference = reference.toPackageReference()
        val expectation = distributionRepository.resolveExpectation(packageReference)
            ?: return PluginManagementActionResult.Rejected(PluginManagementFailure.ReleaseNotVerified)
        return try {
            distributionRepository.downloadPlugin(packageReference).use { download ->
                runtimeManager.install(download.packageFile, expectation)
            }
            publishPluginManagementState()
            PluginManagementActionResult.Completed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publishPluginManagementState()
            PluginManagementActionResult.Rejected(PluginManagementFailure.InstallFailed)
        }
    }

    suspend fun enablePlugin(pluginId: String): PluginManagementActionResult {
        val installed = runtimeManager.statusSnapshot().singleOrNull { it.pluginId == pluginId }
            ?: return PluginManagementActionResult.Rejected(PluginManagementFailure.PluginNotInstalled)
        val expectation = distributionRepository.resolveExpectation(
            PluginPackageReference(installed.pluginId, installed.publisherId, installed.version),
        ) ?: return PluginManagementActionResult.Rejected(PluginManagementFailure.ReleaseNotVerified)
        return try {
            runtimeManager.enable(pluginId, expectation)
            publishPluginManagementState()
            PluginManagementActionResult.Completed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publishPluginManagementState()
            PluginManagementActionResult.Rejected(PluginManagementFailure.RuntimeUnavailable)
        }
    }

    suspend fun disablePlugin(pluginId: String): PluginManagementActionResult = try {
        runtimeManager.disable(pluginId)
        publishPluginManagementState()
        PluginManagementActionResult.Completed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        PluginManagementActionResult.Rejected(PluginManagementFailure.PluginNotInstalled)
    }

    suspend fun uninstallPlugin(
        pluginId: String,
        deleteData: Boolean,
    ): PluginManagementActionResult = try {
        val cleaned = runtimeManager.uninstall(pluginId, deleteData)
        publishPluginManagementState()
        if (cleaned) PluginManagementActionResult.Completed
        else PluginManagementActionResult.Rejected(PluginManagementFailure.UninstallCleanupPending)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        PluginManagementActionResult.Rejected(PluginManagementFailure.PluginNotInstalled)
    }

    suspend fun checkForAppUpdate(forceRefresh: Boolean) {
        updateCheckLock.withLock {
            if (completedUpdateCheck && !forceRefresh) return
            mutableAppUpdate.value = AppUpdateState()
            updateOffers.clear()
            val snapshot = appUpdateService.checkForUpdate()
            val candidate = snapshot.availableUpdate?.let { offer ->
                val id = UUID.randomUUID().toString()
                updateOffers[id] = offer
                AppUpdateCandidate(id, offer.versionCode, offer.versionName)
            }
            mutableAppUpdate.value = snapshot.toUiState(candidate)
            completedUpdateCheck = true
        }
    }

    fun canRequestAppUpdateInstall(): Boolean = appUpdateService.hasInstallerPermission(appContext)

    fun appUpdatePermissionSettingsIntent(): Intent = appUpdateService.installerPermissionSettingsIntent(appContext)

    suspend fun prepareAppUpdateInstall(offerId: String): AppUpdateInstallResult {
        if (!canRequestAppUpdateInstall()) return AppUpdateInstallResult.InstallerPermissionRequired
        return updateCheckLock.withLock {
            val offer = updateOffers[offerId]
                ?: return@withLock AppUpdateInstallResult.Rejected(AppUpdateInstallFailure.OfferExpired)
            var verified: VerifiedAppUpdateApk? = null
            try {
                val downloaded = appUpdateService.downloadAndVerify(offer)
                verified = downloaded
                val ticketId = UUID.randomUUID().toString()
                val intent = appUpdateService.installerIntent(appContext, downloaded)
                synchronized(stateLock) { verifiedUpdates[ticketId] = downloaded }
                updateTickets.edit()
                    .putString(ticketId, "${downloaded.versionCode}|${downloaded.packageFile.name}")
                    .commit()
                AppUpdateInstallResult.Ready(
                    AppUpdateInstallTicket(ticketId, intent, downloaded.versionCode, downloaded.versionName),
                )
            } catch (cancelled: CancellationException) {
                verified?.close()
                throw cancelled
            } catch (_: Exception) {
                verified?.close()
                AppUpdateInstallResult.Rejected(AppUpdateInstallFailure.DownloadFailed)
            }
        }
    }

    suspend fun finishAppUpdateInstall(ticketId: String) = withContext(Dispatchers.IO) {
        val verified = synchronized(stateLock) { verifiedUpdates.remove(ticketId) }
        verified?.close() ?: deleteTicketFile(ticketId)
        updateTickets.edit().remove(ticketId).apply()
    }

    private suspend fun publishPluginManagementState() {
        val installed = runtimeManager.statusSnapshot().map { status ->
            InstalledPluginSummary(
                pluginId = status.pluginId,
                publisherId = status.publisherId,
                version = status.version,
                enabled = status.enabled,
                availability = status.availability.toUiAvailability(),
                cleanupPending = status.cleanupPending,
            )
        }
        val restorationComplete = runtimeManager.isRestorationComplete
        synchronized(stateLock) {
            val snapshot = directorySnapshot
            mutablePluginManagement.value = mutablePluginManagement.value.copy(
                restorationComplete = restorationComplete,
                directoryStatus = snapshot?.status?.toUiStatus()
                    ?: initialDirectoryStatus(configurationLoad),
                directoryFailure = snapshot?.failure?.toUiFailure(),
                usingLastGoodDirectory = snapshot?.usingLastGoodDirectory ?: false,
                releases = snapshot?.releases.orEmpty().map { release ->
                    PluginReleaseOption(
                        reference = PluginReleaseReference(release.pluginId, release.publisherId, release.version),
                        compatible = release.compatible,
                    )
                },
                installed = installed,
            )
        }
    }

    private fun initialPluginManagementState(): PluginManagementState {
        val trustStatus: PluginTrustConfigurationStatus
        val trustFailure: PluginTrustConfigurationFailure?
        when (val result = configurationLoad) {
            OfficialPluginDistributionConfigurationLoad.NotConfigured -> {
                trustStatus = PluginTrustConfigurationStatus.NotConfigured
                trustFailure = null
            }
            is OfficialPluginDistributionConfigurationLoad.Ready -> {
                trustStatus = PluginTrustConfigurationStatus.Ready
                trustFailure = null
            }
            is OfficialPluginDistributionConfigurationLoad.Invalid -> {
                trustStatus = PluginTrustConfigurationStatus.Invalid
                trustFailure = result.failure.toUiFailure()
            }
        }
        return PluginManagementState(
            trustConfigurationStatus = trustStatus,
            trustConfigurationFailure = trustFailure,
            restorationComplete = runtimeManager.isRestorationComplete,
            directoryStatus = initialDirectoryStatus(configurationLoad),
        )
    }

    private fun deleteTicketFile(ticketId: String) {
        if (!UUID_PATTERN.matches(ticketId)) return
        val record = updateTickets.getString(ticketId, null) ?: return
        val fileName = record.substringAfter('|', "")
        if (!UPDATE_FILE_PATTERN.matches(fileName)) return
        val cacheRoot = appContext.cacheDir.canonicalFile.toPath()
        val updateRoot = File(appContext.cacheDir, UPDATE_DIRECTORY).canonicalFile
        if (!updateRoot.toPath().startsWith(cacheRoot)) return
        val file = File(updateRoot, fileName).canonicalFile
        if (file.parentFile == updateRoot) file.delete()
    }

    private fun PluginInstanceAvailability.toUiAvailability(): PluginRuntimeAvailability = when (this) {
        PluginInstanceAvailability.RESTORING -> PluginRuntimeAvailability.Restoring
        PluginInstanceAvailability.ACTIVE -> PluginRuntimeAvailability.Active
        PluginInstanceAvailability.UNAVAILABLE -> PluginRuntimeAvailability.Unavailable
        PluginInstanceAvailability.INVALIDATED -> PluginRuntimeAvailability.Disabled
    }

    private fun PluginDirectoryStatus.toUiStatus(): PluginDistributionStatus = when (this) {
        PluginDirectoryStatus.NOT_CONFIGURED -> PluginDistributionStatus.NotConfigured
        PluginDirectoryStatus.READY -> PluginDistributionStatus.Ready
        PluginDirectoryStatus.DIRECTORY_MISSING -> PluginDistributionStatus.DirectoryMissing
        PluginDirectoryStatus.NETWORK_ERROR -> PluginDistributionStatus.NetworkError
        PluginDirectoryStatus.INVALID_DIRECTORY -> PluginDistributionStatus.InvalidDirectory
    }

    private fun PluginDirectoryFailure.toUiFailure(): PluginDistributionFailure = when (this) {
        PluginDirectoryFailure.TRANSFER -> PluginDistributionFailure.Transfer
        PluginDirectoryFailure.INVALID_METADATA -> PluginDistributionFailure.InvalidMetadata
        PluginDirectoryFailure.PRIVATE_STORAGE -> PluginDistributionFailure.PrivateStorage
        PluginDirectoryFailure.INTERNAL -> PluginDistributionFailure.Internal
    }

    private fun OfficialPluginDistributionConfigurationLoad.Failure.toUiFailure(): PluginTrustConfigurationFailure = when (this) {
        OfficialPluginDistributionConfigurationLoad.Failure.EMPTY_KEY -> PluginTrustConfigurationFailure.EmptyKey
        OfficialPluginDistributionConfigurationLoad.Failure.KEY_TOO_LARGE -> PluginTrustConfigurationFailure.KeyTooLarge
        OfficialPluginDistributionConfigurationLoad.Failure.INVALID_RSA_KEY -> PluginTrustConfigurationFailure.InvalidRsaKey
        OfficialPluginDistributionConfigurationLoad.Failure.UNSUPPORTED_ABI -> PluginTrustConfigurationFailure.UnsupportedAbi
        OfficialPluginDistributionConfigurationLoad.Failure.READ_FAILED -> PluginTrustConfigurationFailure.ReadFailed
    }

    private fun AppUpdateSnapshot.toUiState(candidate: AppUpdateCandidate?): AppUpdateState = AppUpdateState(
        status = when (status) {
            AppUpdateStatus.UP_TO_DATE -> AppUpdateUiStatus.UpToDate
            AppUpdateStatus.UPDATE_AVAILABLE -> AppUpdateUiStatus.UpdateAvailable
            AppUpdateStatus.METADATA_MISSING -> AppUpdateUiStatus.MetadataMissing
            AppUpdateStatus.NETWORK_ERROR -> AppUpdateUiStatus.NetworkError
            AppUpdateStatus.INVALID_RELEASE -> AppUpdateUiStatus.InvalidRelease
            AppUpdateStatus.INCOMPATIBLE_PACKAGE -> AppUpdateUiStatus.IncompatiblePackage
            AppUpdateStatus.INCOMPATIBLE_INSTALLATION -> AppUpdateUiStatus.IncompatibleInstallation
        },
        installedVersionCode = installedVersionCode,
        installedVersionName = installedVersionName,
        availableUpdate = candidate,
        failure = failure?.let {
            when (it) {
                AppUpdateFailure.NETWORK -> AppUpdateUiFailure.Network
                AppUpdateFailure.RELEASE_METADATA -> AppUpdateUiFailure.ReleaseMetadata
                AppUpdateFailure.RELEASE_ASSET_DIGEST -> AppUpdateUiFailure.ReleaseAssetDigest
                AppUpdateFailure.INSTALLED_PACKAGE -> AppUpdateUiFailure.InstalledPackage
            }
        },
    )

    private fun PluginReleaseReference.toPackageReference() =
        PluginPackageReference(pluginId = pluginId, publisherId = publisherId, version = version)

    private fun initialDirectoryStatus(
        configuration: OfficialPluginDistributionConfigurationLoad,
    ): PluginDistributionStatus = when (configuration) {
        OfficialPluginDistributionConfigurationLoad.NotConfigured -> PluginDistributionStatus.NotConfigured
        is OfficialPluginDistributionConfigurationLoad.Invalid -> PluginDistributionStatus.NotConfigured
        is OfficialPluginDistributionConfigurationLoad.Ready -> PluginDistributionStatus.Loading
    }

    private companion object {
        const val UPDATE_TICKETS_PREFERENCES = "official-app-update-tickets"
        const val UPDATE_DIRECTORY = "app-updates"
        val UUID_PATTERN = Regex("[0-9a-fA-F-]{36}")
        val UPDATE_FILE_PATTERN = Regex("elymbot-update-[A-Za-z0-9_-]+\\.apk")
    }
}

private class OfficialPluginCatalogToolSource(
    private val host: AppHostManagementUiService,
) : ToolSource {
    override val definition = ToolDefinition(
        id = "official_plugin_catalog",
        name = "official_plugin_catalog",
        description = "Read the verified official plugin catalog and host compatibility. This tool is read-only and cannot install or change plugins.",
        parametersJsonSchema = "{}",
        scopes = setOf(ToolScope.MAIN_SESSION),
    )

    override suspend fun execute(
        call: com.elymbot.core.contract.ModelToolCall,
        context: ToolExecutionContext,
        onProgress: suspend (com.elymbot.core.contract.ToolProgress) -> Unit,
    ): ToolExecutionResult {
        if (context.scope != ToolScope.MAIN_SESSION) {
            return ToolExecutionResult(call.callId, ToolResultStatus.REJECTED, "Plugin recommendations are only available in the App main session.")
        }
        return try {
            host.refreshPluginDirectory()
            val state = host.pluginManagement.value
            val content = buildString {
                append("directoryStatus=").append(state.directoryStatus.name)
                append("; trust=").append(state.trustConfigurationStatus.name)
                append("; releases=")
                append(state.releases.filter(PluginReleaseOption::compatible).joinToString { release ->
                    "${release.reference.pluginId}@${release.reference.version}"
                }.ifEmpty { "none" })
            }
            val status = if (state.trustConfigurationStatus == PluginTrustConfigurationStatus.Ready) {
                ToolResultStatus.SUCCESS
            } else {
                ToolResultStatus.REJECTED
            }
            ToolExecutionResult(call.callId, status, content)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ToolExecutionResult(call.callId, ToolResultStatus.FAILED, "The verified plugin directory is unavailable.")
        }
    }
}
