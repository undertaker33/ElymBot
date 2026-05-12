package com.astrbot.android.feature.settings.api

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface SettingsBackupPort {
    val fullBackups: StateFlow<List<SettingsAppBackupItem>>
    val conversationSettings: StateFlow<SettingsConversationBackupSettings>
    val conversationBackups: StateFlow<List<SettingsConversationBackupItem>>

    fun moduleBackups(module: SettingsBackupModuleKind): StateFlow<List<SettingsModuleBackupItem>>

    suspend fun createFullBackup(): Result<SettingsAppBackupItem>
    suspend fun deleteFullBackup(backupId: String): Result<Unit>
    suspend fun exportFullBackup(context: Context, backupId: String, targetUri: Uri): Result<Unit>
    suspend fun prepareFullImportFromBackup(backupId: String): Result<SettingsAppBackupImportSource>
    suspend fun prepareFullImportFromUri(context: Context, uri: Uri): Result<SettingsAppBackupImportSource>
    suspend fun importFullBackup(
        source: SettingsAppBackupImportSource,
        plan: SettingsAppBackupImportPlan,
    ): Result<SettingsAppBackupRestoreResult>

    suspend fun createModuleBackup(module: SettingsBackupModuleKind): Result<SettingsModuleBackupItem>
    suspend fun deleteModuleBackup(module: SettingsBackupModuleKind, backupId: String): Result<Unit>
    suspend fun exportModuleBackup(
        context: Context,
        module: SettingsBackupModuleKind,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit>
    suspend fun prepareModuleImportFromBackup(
        module: SettingsBackupModuleKind,
        backupId: String,
    ): Result<SettingsModuleBackupImportSource>
    suspend fun prepareModuleImportFromUri(
        context: Context,
        module: SettingsBackupModuleKind,
        uri: Uri,
    ): Result<SettingsModuleBackupImportSource>
    suspend fun importModuleBackup(
        source: SettingsModuleBackupImportSource,
        mode: SettingsAppBackupImportMode,
    ): Result<Int>

    suspend fun setConversationAutoBackupEnabled(enabled: Boolean)
    suspend fun setConversationAutoBackupTime(hour: Int, minute: Int)
    suspend fun createConversationBackup(): Result<SettingsConversationBackupItem>
    suspend fun deleteConversationBackup(backupId: String): Result<Unit>
    suspend fun exportConversationBackup(context: Context, backupId: String, targetUri: Uri): Result<Unit>
    suspend fun prepareConversationImportFromBackup(backupId: String): Result<SettingsConversationImportSource>
    suspend fun prepareConversationImportFromUri(context: Context, uri: Uri): Result<SettingsConversationImportSource>
    suspend fun importConversationSessions(
        source: SettingsConversationImportSource,
        overwriteDuplicates: Boolean,
    ): Result<SettingsConversationImportResult>
}

enum class SettingsBackupModuleKind {
    BOTS,
    PROVIDERS,
    PERSONAS,
    CONFIGS,
    CONVERSATIONS,
    QQ_ACCOUNTS,
    TTS_ASSETS,
}

enum class SettingsAppBackupImportMode {
    REPLACE_ALL,
    MERGE_SKIP_DUPLICATES,
    MERGE_OVERWRITE_DUPLICATES,
}

data class SettingsAppBackupImportPlan(
    val bots: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
    val providers: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
    val personas: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
    val configs: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
    val conversations: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
    val qqAccounts: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
    val ttsAssets: SettingsAppBackupImportMode = SettingsAppBackupImportMode.REPLACE_ALL,
)

data class SettingsAppBackupItem(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val trigger: String,
    val moduleCounts: Map<String, Int>,
)

data class SettingsModuleBackupItem(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val trigger: String,
    val module: SettingsBackupModuleKind,
    val recordCount: Int,
    val hasFiles: Boolean,
)

data class SettingsAppBackupModuleSnapshot(
    val count: Int = 0,
)

data class SettingsAppBackupModules(
    val bots: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
    val providers: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
    val personas: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
    val configs: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
    val conversations: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
    val qqLogin: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
    val ttsAssets: SettingsAppBackupModuleSnapshot = SettingsAppBackupModuleSnapshot(),
)

data class SettingsAppBackupManifest(
    val modules: SettingsAppBackupModules = SettingsAppBackupModules(),
)

data class SettingsAppBackupModuleConflict(
    val duplicateCount: Int = 0,
    val newCount: Int = 0,
)

data class SettingsAppBackupConflictPreview(
    val bots: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
    val providers: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
    val personas: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
    val configs: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
    val conversations: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
    val qqAccounts: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
    val ttsAssets: SettingsAppBackupModuleConflict = SettingsAppBackupModuleConflict(),
)

data class SettingsAppBackupImportSource(
    val id: String,
    val label: String,
    val manifest: SettingsAppBackupManifest,
    val preview: SettingsAppBackupConflictPreview,
)

data class SettingsModuleBackupImportSource(
    val id: String,
    val label: String,
    val module: SettingsBackupModuleKind,
    val manifest: SettingsAppBackupManifest,
    val preview: SettingsAppBackupModuleConflict,
)

data class SettingsAppBackupRestoreResult(
    val botCount: Int,
    val providerCount: Int,
    val personaCount: Int,
    val configCount: Int,
    val conversationCount: Int,
    val qqAccountCount: Int,
    val ttsAssetCount: Int,
)

data class SettingsConversationBackupSettings(
    val autoBackupEnabled: Boolean = false,
    val autoBackupHour: Int = 3,
    val autoBackupMinute: Int = 0,
)

data class SettingsConversationBackupItem(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val sessionCount: Int,
    val messageCount: Int,
    val trigger: String,
)

data class SettingsConversationImportSession(
    val title: String,
)

data class SettingsConversationImportPreview(
    val duplicateSessions: List<SettingsConversationImportSession> = emptyList(),
    val newSessions: List<SettingsConversationImportSession> = emptyList(),
)

data class SettingsConversationImportSource(
    val id: String,
    val label: String,
    val preview: SettingsConversationImportPreview,
)

data class SettingsConversationImportResult(
    val importedCount: Int,
    val overwrittenCount: Int,
    val skippedCount: Int,
)
