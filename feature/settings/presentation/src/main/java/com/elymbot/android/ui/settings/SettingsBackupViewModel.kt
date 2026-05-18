package com.elymbot.android.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.elymbot.android.feature.settings.api.SettingsAppBackupImportMode
import com.elymbot.android.feature.settings.api.SettingsAppBackupImportPlan
import com.elymbot.android.feature.settings.api.SettingsAppBackupImportSource
import com.elymbot.android.feature.settings.api.SettingsBackupModuleKind
import com.elymbot.android.feature.settings.api.SettingsBackupPort
import com.elymbot.android.feature.settings.api.SettingsConversationImportSource
import com.elymbot.android.feature.settings.api.SettingsModuleBackupImportSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    private val backupPort: SettingsBackupPort,
) : ViewModel() {
    val fullBackups = backupPort.fullBackups
    val conversationSettings = backupPort.conversationSettings
    val conversationBackups = backupPort.conversationBackups

    fun moduleBackups(module: SettingsBackupModuleKind) = backupPort.moduleBackups(module)

    suspend fun createFullBackup() = backupPort.createFullBackup()
    suspend fun deleteFullBackup(backupId: String) = backupPort.deleteFullBackup(backupId)
    suspend fun exportFullBackup(context: Context, backupId: String, targetUri: Uri) =
        backupPort.exportFullBackup(context, backupId, targetUri)
    suspend fun prepareFullImportFromBackup(backupId: String) = backupPort.prepareFullImportFromBackup(backupId)
    suspend fun prepareFullImportFromUri(context: Context, uri: Uri) = backupPort.prepareFullImportFromUri(context, uri)
    suspend fun importFullBackup(source: SettingsAppBackupImportSource, plan: SettingsAppBackupImportPlan) =
        backupPort.importFullBackup(source, plan)

    suspend fun createModuleBackup(module: SettingsBackupModuleKind) = backupPort.createModuleBackup(module)
    suspend fun deleteModuleBackup(module: SettingsBackupModuleKind, backupId: String) =
        backupPort.deleteModuleBackup(module, backupId)
    suspend fun exportModuleBackup(
        context: Context,
        module: SettingsBackupModuleKind,
        backupId: String,
        targetUri: Uri,
    ) = backupPort.exportModuleBackup(context, module, backupId, targetUri)
    suspend fun prepareModuleImportFromBackup(module: SettingsBackupModuleKind, backupId: String) =
        backupPort.prepareModuleImportFromBackup(module, backupId)
    suspend fun prepareModuleImportFromUri(context: Context, module: SettingsBackupModuleKind, uri: Uri) =
        backupPort.prepareModuleImportFromUri(context, module, uri)
    suspend fun importModuleBackup(source: SettingsModuleBackupImportSource, mode: SettingsAppBackupImportMode) =
        backupPort.importModuleBackup(source, mode)

    suspend fun setConversationAutoBackupEnabled(enabled: Boolean) =
        backupPort.setConversationAutoBackupEnabled(enabled)
    suspend fun setConversationAutoBackupTime(hour: Int, minute: Int) =
        backupPort.setConversationAutoBackupTime(hour, minute)
    suspend fun createConversationBackup() = backupPort.createConversationBackup()
    suspend fun deleteConversationBackup(backupId: String) = backupPort.deleteConversationBackup(backupId)
    suspend fun exportConversationBackup(context: Context, backupId: String, targetUri: Uri) =
        backupPort.exportConversationBackup(context, backupId, targetUri)
    suspend fun prepareConversationImportFromBackup(backupId: String) =
        backupPort.prepareConversationImportFromBackup(backupId)
    suspend fun prepareConversationImportFromUri(context: Context, uri: Uri) =
        backupPort.prepareConversationImportFromUri(context, uri)
    suspend fun importConversationSessions(
        source: SettingsConversationImportSource,
        overwriteDuplicates: Boolean,
    ) = backupPort.importConversationSessions(source, overwriteDuplicates)
}
