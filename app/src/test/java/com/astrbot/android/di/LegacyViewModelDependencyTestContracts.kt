package com.astrbot.android.di

import com.astrbot.android.feature.plugin.data.PluginCatalogVersionGateResult
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.qq.data.NapCatLoginService
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface QQLoginViewModelDependencies {
    val loginState: StateFlow<NapCatLoginState>
    suspend fun refresh(manual: Boolean = false)
    suspend fun refreshQrCode()
    suspend fun quickLoginSavedAccount(uin: String? = null)
    suspend fun saveQuickLoginAccount(uin: String)
    suspend fun logoutCurrentAccount()
    suspend fun passwordLogin(uin: String, password: String)
    suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String)
    suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?)
    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult
    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult
    fun log(message: String)
}

interface PluginViewModelDependencies {
    val records: StateFlow<List<PluginInstallRecord>>
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>
    val governanceReadModels: Flow<Map<String, PluginGovernanceReadModel>>
    val logBus: PluginRuntimeLogBus
    suspend fun handleInstallIntent(intent: PluginInstallIntent, onDownloadProgress: (PluginDownloadProgress) -> Unit = {}): PluginInstallIntentResult
    suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult
    suspend fun ensureOfficialMarketCatalogSubscribed(): PluginCatalogSyncState
    suspend fun refreshMarketCatalog(): List<PluginCatalogSyncState>
    fun getHostVersion(): String
    fun evaluateCatalogVersion(version: PluginCatalogVersion): PluginCatalogVersionGateResult
    fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability?
    fun getPluginGovernance(pluginId: String): PluginGovernanceReadModel?
    fun getPluginGovernanceSilently(pluginId: String): PluginGovernanceReadModel?
    suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord
    fun getPluginStaticConfigSchema(pluginId: String): PluginStaticConfigSchema?
    fun resolvePluginSettingsSchemaPath(pluginId: String): String?
    fun resolvePluginConfigSnapshot(pluginId: String, boundary: PluginConfigStorageBoundary): PluginConfigStoreSnapshot
    fun savePluginCoreConfig(pluginId: String, boundary: PluginConfigStorageBoundary, coreValues: Map<String, PluginStaticConfigValue>): PluginConfigStoreSnapshot
    fun savePluginExtensionConfig(pluginId: String, boundary: PluginConfigStorageBoundary, extensionValues: Map<String, PluginStaticConfigValue>): PluginConfigStoreSnapshot
    fun resolvePluginWorkspaceSnapshot(pluginId: String): PluginHostWorkspaceSnapshot
    suspend fun importPluginWorkspaceFile(pluginId: String, uri: String): PluginHostWorkspaceSnapshot
    fun deletePluginWorkspaceFile(pluginId: String, relativePath: String): PluginHostWorkspaceSnapshot
    fun clearPluginFailureState(pluginId: String): PluginInstallRecord
    fun recoverPluginFailureState(pluginId: String): PluginInstallRecord
    fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord
    fun uninstallPlugin(pluginId: String, policy: PluginUninstallPolicy): com.astrbot.android.feature.plugin.data.PluginUninstallResult
}
