package com.astrbot.android.feature.settings

import android.content.Context
import android.net.Uri
import com.astrbot.android.core.db.backup.AppBackupConflictPreview
import com.astrbot.android.core.db.backup.AppBackupImportMode
import com.astrbot.android.core.db.backup.AppBackupImportPlan
import com.astrbot.android.core.db.backup.AppBackupImportSource
import com.astrbot.android.core.db.backup.AppBackupItem
import com.astrbot.android.core.db.backup.AppBackupManifest
import com.astrbot.android.core.db.backup.AppBackupModuleConflict
import com.astrbot.android.core.db.backup.AppBackupModuleKind
import com.astrbot.android.core.db.backup.AppBackupModuleSnapshot
import com.astrbot.android.core.db.backup.AppBackupModules
import com.astrbot.android.core.db.backup.AppBackupRestoreResult
import com.astrbot.android.core.db.backup.AppBackupService
import com.astrbot.android.core.db.backup.ConversationBackupItem
import com.astrbot.android.core.db.backup.ConversationBackupService
import com.astrbot.android.core.db.backup.ConversationBackupSettings
import com.astrbot.android.core.db.backup.ConversationImportSource
import com.astrbot.android.core.db.backup.ModuleBackupImportSource
import com.astrbot.android.core.db.backup.ModuleBackupItem
import com.astrbot.android.feature.settings.api.backup.ConversationImportResult
import com.astrbot.android.feature.settings.api.SettingsAppBackupConflictPreview
import com.astrbot.android.feature.settings.api.SettingsAppBackupImportMode
import com.astrbot.android.feature.settings.api.SettingsAppBackupImportPlan
import com.astrbot.android.feature.settings.api.SettingsAppBackupImportSource
import com.astrbot.android.feature.settings.api.SettingsAppBackupItem
import com.astrbot.android.feature.settings.api.SettingsAppBackupManifest
import com.astrbot.android.feature.settings.api.SettingsAppBackupModuleConflict
import com.astrbot.android.feature.settings.api.SettingsAppBackupModuleSnapshot
import com.astrbot.android.feature.settings.api.SettingsAppBackupModules
import com.astrbot.android.feature.settings.api.SettingsAppBackupRestoreResult
import com.astrbot.android.feature.settings.api.SettingsBackupModuleKind
import com.astrbot.android.feature.settings.api.SettingsBackupPort
import com.astrbot.android.feature.settings.api.SettingsConversationBackupItem
import com.astrbot.android.feature.settings.api.SettingsConversationBackupSettings
import com.astrbot.android.feature.settings.api.SettingsConversationImportPreview
import com.astrbot.android.feature.settings.api.SettingsConversationImportResult
import com.astrbot.android.feature.settings.api.SettingsConversationImportSession
import com.astrbot.android.feature.settings.api.SettingsConversationImportSource
import com.astrbot.android.feature.settings.api.SettingsModuleBackupImportSource
import com.astrbot.android.feature.settings.api.SettingsModuleBackupItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AppSettingsBackupPortAdapter @Inject constructor(
    private val appBackupService: AppBackupService,
    private val conversationBackupService: ConversationBackupService,
) : SettingsBackupPort {

    private val fullImportSources = ConcurrentHashMap<String, AppBackupImportSource>()
    private val moduleImportSources = ConcurrentHashMap<String, ModuleBackupImportSource>()
    private val conversationImportSources = ConcurrentHashMap<String, ConversationImportSource>()

    override val fullBackups: StateFlow<List<SettingsAppBackupItem>> =
        MappedStateFlow(appBackupService.backups) { items -> items.map(AppBackupItem::toSettings) }

    override val conversationSettings: StateFlow<SettingsConversationBackupSettings> =
        MappedStateFlow(conversationBackupService.settings, ConversationBackupSettings::toSettings)

    override val conversationBackups: StateFlow<List<SettingsConversationBackupItem>> =
        MappedStateFlow(conversationBackupService.backups) { items -> items.map(ConversationBackupItem::toSettings) }

    override fun moduleBackups(module: SettingsBackupModuleKind): StateFlow<List<SettingsModuleBackupItem>> {
        return MappedStateFlow(appBackupService.backupsForModule(module.toCore())) { items ->
            items.map(ModuleBackupItem::toSettings)
        }
    }

    override suspend fun createFullBackup(): Result<SettingsAppBackupItem> {
        return appBackupService.createBackup().map(AppBackupItem::toSettings)
    }

    override suspend fun deleteFullBackup(backupId: String): Result<Unit> {
        return appBackupService.deleteBackup(backupId)
    }

    override suspend fun exportFullBackup(context: Context, backupId: String, targetUri: Uri): Result<Unit> {
        return appBackupService.exportBackupToUri(context, backupId, targetUri)
    }

    override suspend fun prepareFullImportFromBackup(backupId: String): Result<SettingsAppBackupImportSource> {
        return appBackupService.prepareImportFromBackup(backupId).map(::rememberFullImportSource)
    }

    override suspend fun prepareFullImportFromUri(
        context: Context,
        uri: Uri,
    ): Result<SettingsAppBackupImportSource> {
        return appBackupService.prepareImportFromUri(context, uri).map(::rememberFullImportSource)
    }

    override suspend fun importFullBackup(
        source: SettingsAppBackupImportSource,
        plan: SettingsAppBackupImportPlan,
    ): Result<SettingsAppBackupRestoreResult> {
        val coreSource = fullImportSources[source.id]
            ?: return Result.failure(IllegalArgumentException("Unknown full backup import source"))
        return appBackupService.importBackup(coreSource, plan.toCore()).map(AppBackupRestoreResult::toSettings)
    }

    override suspend fun createModuleBackup(module: SettingsBackupModuleKind): Result<SettingsModuleBackupItem> {
        return appBackupService.createModuleBackup(module.toCore()).map(ModuleBackupItem::toSettings)
    }

    override suspend fun deleteModuleBackup(
        module: SettingsBackupModuleKind,
        backupId: String,
    ): Result<Unit> {
        return appBackupService.deleteModuleBackup(module.toCore(), backupId)
    }

    override suspend fun exportModuleBackup(
        context: Context,
        module: SettingsBackupModuleKind,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> {
        return appBackupService.exportModuleBackupToUri(context, module.toCore(), backupId, targetUri)
    }

    override suspend fun prepareModuleImportFromBackup(
        module: SettingsBackupModuleKind,
        backupId: String,
    ): Result<SettingsModuleBackupImportSource> {
        return appBackupService.prepareModuleImportFromBackup(module.toCore(), backupId)
            .map(::rememberModuleImportSource)
    }

    override suspend fun prepareModuleImportFromUri(
        context: Context,
        module: SettingsBackupModuleKind,
        uri: Uri,
    ): Result<SettingsModuleBackupImportSource> {
        return appBackupService.prepareModuleImportFromUri(context, module.toCore(), uri)
            .map(::rememberModuleImportSource)
    }

    override suspend fun importModuleBackup(
        source: SettingsModuleBackupImportSource,
        mode: SettingsAppBackupImportMode,
    ): Result<Int> {
        val coreSource = moduleImportSources[source.id]
            ?: return Result.failure(IllegalArgumentException("Unknown module backup import source"))
        return appBackupService.importModuleBackup(coreSource, mode.toCore())
    }

    override suspend fun setConversationAutoBackupEnabled(enabled: Boolean) {
        conversationBackupService.setAutoBackupEnabled(enabled)
    }

    override suspend fun setConversationAutoBackupTime(hour: Int, minute: Int) {
        conversationBackupService.setAutoBackupTime(hour, minute)
    }

    override suspend fun createConversationBackup(): Result<SettingsConversationBackupItem> {
        return conversationBackupService.createBackup().map(ConversationBackupItem::toSettings)
    }

    override suspend fun deleteConversationBackup(backupId: String): Result<Unit> {
        return conversationBackupService.deleteBackup(backupId)
    }

    override suspend fun exportConversationBackup(
        context: Context,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> {
        return conversationBackupService.exportBackupToUri(context, backupId, targetUri)
    }

    override suspend fun prepareConversationImportFromBackup(
        backupId: String,
    ): Result<SettingsConversationImportSource> {
        return conversationBackupService.prepareImportFromBackup(backupId).map(::rememberConversationImportSource)
    }

    override suspend fun prepareConversationImportFromUri(
        context: Context,
        uri: Uri,
    ): Result<SettingsConversationImportSource> {
        return conversationBackupService.prepareImportFromUri(context, uri).map(::rememberConversationImportSource)
    }

    override suspend fun importConversationSessions(
        source: SettingsConversationImportSource,
        overwriteDuplicates: Boolean,
    ): Result<SettingsConversationImportResult> {
        val coreSource = conversationImportSources[source.id]
            ?: return Result.failure(IllegalArgumentException("Unknown conversation import source"))
        return conversationBackupService.importSessions(coreSource.sessions, overwriteDuplicates)
            .map(ConversationImportResult::toSettings)
    }

    private fun rememberFullImportSource(source: AppBackupImportSource): SettingsAppBackupImportSource {
        val id = UUID.randomUUID().toString()
        fullImportSources[id] = source
        return source.toSettings(id)
    }

    private fun rememberModuleImportSource(source: ModuleBackupImportSource): SettingsModuleBackupImportSource {
        val id = UUID.randomUUID().toString()
        moduleImportSources[id] = source
        return source.toSettings(id)
    }

    private fun rememberConversationImportSource(source: ConversationImportSource): SettingsConversationImportSource {
        val id = UUID.randomUUID().toString()
        conversationImportSources[id] = source
        return source.toSettings(id)
    }
}

private class MappedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val mapper: (T) -> R,
) : StateFlow<R> {
    override val replayCache: List<R>
        get() = listOf(value)

    override val value: R
        get() = mapper(source.value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        source.collect { value -> collector.emit(mapper(value)) }
    }
}

private fun SettingsBackupModuleKind.toCore(): AppBackupModuleKind = when (this) {
    SettingsBackupModuleKind.BOTS -> AppBackupModuleKind.BOTS
    SettingsBackupModuleKind.PROVIDERS -> AppBackupModuleKind.PROVIDERS
    SettingsBackupModuleKind.PERSONAS -> AppBackupModuleKind.PERSONAS
    SettingsBackupModuleKind.CONFIGS -> AppBackupModuleKind.CONFIGS
    SettingsBackupModuleKind.CONVERSATIONS -> AppBackupModuleKind.CONVERSATIONS
    SettingsBackupModuleKind.QQ_ACCOUNTS -> AppBackupModuleKind.QQ_ACCOUNTS
    SettingsBackupModuleKind.TTS_ASSETS -> AppBackupModuleKind.TTS_ASSETS
}

private fun AppBackupModuleKind.toSettings(): SettingsBackupModuleKind = when (this) {
    AppBackupModuleKind.BOTS -> SettingsBackupModuleKind.BOTS
    AppBackupModuleKind.PROVIDERS -> SettingsBackupModuleKind.PROVIDERS
    AppBackupModuleKind.PERSONAS -> SettingsBackupModuleKind.PERSONAS
    AppBackupModuleKind.CONFIGS -> SettingsBackupModuleKind.CONFIGS
    AppBackupModuleKind.CONVERSATIONS -> SettingsBackupModuleKind.CONVERSATIONS
    AppBackupModuleKind.QQ_ACCOUNTS -> SettingsBackupModuleKind.QQ_ACCOUNTS
    AppBackupModuleKind.TTS_ASSETS -> SettingsBackupModuleKind.TTS_ASSETS
}

private fun SettingsAppBackupImportMode.toCore(): AppBackupImportMode = when (this) {
    SettingsAppBackupImportMode.REPLACE_ALL -> AppBackupImportMode.REPLACE_ALL
    SettingsAppBackupImportMode.MERGE_SKIP_DUPLICATES -> AppBackupImportMode.MERGE_SKIP_DUPLICATES
    SettingsAppBackupImportMode.MERGE_OVERWRITE_DUPLICATES -> AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES
}

private fun SettingsAppBackupImportPlan.toCore(): AppBackupImportPlan = AppBackupImportPlan(
    bots = bots.toCore(),
    providers = providers.toCore(),
    personas = personas.toCore(),
    configs = configs.toCore(),
    conversations = conversations.toCore(),
    qqAccounts = qqAccounts.toCore(),
    ttsAssets = ttsAssets.toCore(),
)

private fun AppBackupItem.toSettings(): SettingsAppBackupItem = SettingsAppBackupItem(
    id = id,
    fileName = fileName,
    createdAt = createdAt,
    trigger = trigger,
    moduleCounts = moduleCounts,
)

private fun ModuleBackupItem.toSettings(): SettingsModuleBackupItem = SettingsModuleBackupItem(
    id = id,
    fileName = fileName,
    createdAt = createdAt,
    trigger = trigger,
    module = module.toSettings(),
    recordCount = recordCount,
    hasFiles = hasFiles,
)

private fun ConversationBackupSettings.toSettings(): SettingsConversationBackupSettings {
    return SettingsConversationBackupSettings(
        autoBackupEnabled = autoBackupEnabled,
        autoBackupHour = autoBackupHour,
        autoBackupMinute = autoBackupMinute,
    )
}

private fun ConversationBackupItem.toSettings(): SettingsConversationBackupItem {
    return SettingsConversationBackupItem(
        id = id,
        fileName = fileName,
        createdAt = createdAt,
        sessionCount = sessionCount,
        messageCount = messageCount,
        trigger = trigger,
    )
}

private fun AppBackupImportSource.toSettings(id: String): SettingsAppBackupImportSource {
    return SettingsAppBackupImportSource(
        id = id,
        label = label,
        manifest = manifest.toSettings(),
        preview = preview.toSettings(),
    )
}

private fun ModuleBackupImportSource.toSettings(id: String): SettingsModuleBackupImportSource {
    return SettingsModuleBackupImportSource(
        id = id,
        label = label,
        module = module.toSettings(),
        manifest = manifest.toSettings(),
        preview = preview.toSettings(),
    )
}

private fun ConversationImportSource.toSettings(id: String): SettingsConversationImportSource {
    return SettingsConversationImportSource(
        id = id,
        label = label,
        preview = SettingsConversationImportPreview(
            duplicateSessions = preview.duplicateSessions.map { session ->
                SettingsConversationImportSession(title = session.title)
            },
            newSessions = preview.newSessions.map { session ->
                SettingsConversationImportSession(title = session.title)
            },
        ),
    )
}

private fun AppBackupManifest.toSettings(): SettingsAppBackupManifest {
    return SettingsAppBackupManifest(modules = modules.toSettings())
}

private fun AppBackupModules.toSettings(): SettingsAppBackupModules {
    return SettingsAppBackupModules(
        bots = bots.toSettings(),
        providers = providers.toSettings(),
        personas = personas.toSettings(),
        configs = configs.toSettings(),
        conversations = conversations.toSettings(),
        qqLogin = qqLogin.toSettings(),
        ttsAssets = ttsAssets.toSettings(),
    )
}

private fun AppBackupModuleSnapshot.toSettings(): SettingsAppBackupModuleSnapshot {
    return SettingsAppBackupModuleSnapshot(count = count)
}

private fun AppBackupConflictPreview.toSettings(): SettingsAppBackupConflictPreview {
    return SettingsAppBackupConflictPreview(
        bots = bots.toSettings(),
        providers = providers.toSettings(),
        personas = personas.toSettings(),
        configs = configs.toSettings(),
        conversations = conversations.toSettings(),
        qqAccounts = qqAccounts.toSettings(),
        ttsAssets = ttsAssets.toSettings(),
    )
}

private fun AppBackupModuleConflict.toSettings(): SettingsAppBackupModuleConflict {
    return SettingsAppBackupModuleConflict(
        duplicateCount = duplicateCount,
        newCount = newCount,
    )
}

private fun AppBackupRestoreResult.toSettings(): SettingsAppBackupRestoreResult {
    return SettingsAppBackupRestoreResult(
        botCount = botCount,
        providerCount = providerCount,
        personaCount = personaCount,
        configCount = configCount,
        conversationCount = conversationCount,
        qqAccountCount = qqAccountCount,
        ttsAssetCount = ttsAssetCount,
    )
}

private fun ConversationImportResult.toSettings(): SettingsConversationImportResult {
    return SettingsConversationImportResult(
        importedCount = importedCount,
        overwrittenCount = overwrittenCount,
        skippedCount = skippedCount,
    )
}
