package com.astrbot.android.di.hilt

import com.astrbot.android.core.backup.BackupParticipant
import com.astrbot.android.core.backup.BackupParticipantCoverage
import com.astrbot.android.core.backup.BackupParticipantRegistry
import com.astrbot.android.core.backup.BackupParticipantRestoreResult
import com.astrbot.android.core.backup.BackupParticipantSnapshot
import com.astrbot.android.core.db.backup.AppBackupDataPort
import com.astrbot.android.core.db.backup.ConversationBackupDataPort
import com.astrbot.android.di.HiltAppBackupDataPort
import com.astrbot.android.di.HiltConversationBackupDataPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object BackupModule {
    @Provides
    @Singleton
    fun provideAppBackupDataPort(
        dataPort: HiltAppBackupDataPort,
    ): AppBackupDataPort = dataPort

    @Provides
    @Singleton
    fun provideConversationBackupDataPort(
        dataPort: HiltConversationBackupDataPort,
    ): ConversationBackupDataPort = dataPort

    @Provides
    @Singleton
    fun provideBackupParticipantRegistry(
        participants: Set<@JvmSuppressWildcards BackupParticipant>,
    ): BackupParticipantRegistry = BackupParticipantRegistry(participants)

    @Provides
    @IntoSet
    fun provideBotBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "bots",
        displayName = "Bot profiles",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun provideProviderBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "providers",
        displayName = "Provider profiles",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun providePersonaBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "personas",
        displayName = "Persona profiles",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun provideConfigBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "configs",
        displayName = "Config profiles",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun provideConversationBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "conversations",
        displayName = "Conversations",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun provideQqBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "qq-login",
        displayName = "QQ login",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun provideTtsBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "tts-assets",
        displayName = "TTS assets",
        coverage = BackupParticipantCoverage.SUPPORTED,
    )

    @Provides
    @IntoSet
    fun providePluginBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "plugins",
        displayName = "Plugin data",
        coverage = BackupParticipantCoverage.PLANNED,
    )

    @Provides
    @IntoSet
    fun provideResourceBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "resources",
        displayName = "Resource Center",
        coverage = BackupParticipantCoverage.PLANNED,
    )

    @Provides
    @IntoSet
    fun provideCronBackupParticipant(): BackupParticipant = metadataParticipant(
        key = "cron",
        displayName = "Cron jobs",
        coverage = BackupParticipantCoverage.PLANNED,
    )

    private fun metadataParticipant(
        key: String,
        displayName: String,
        coverage: BackupParticipantCoverage,
    ): BackupParticipant {
        return object : BackupParticipant {
            override val key: String = key
            override val displayName: String = displayName
            override val coverage: BackupParticipantCoverage = coverage

            override suspend fun snapshot(): BackupParticipantSnapshot {
                return BackupParticipantSnapshot(key = key)
            }

            override suspend fun restore(snapshot: BackupParticipantSnapshot): BackupParticipantRestoreResult {
                return BackupParticipantRestoreResult(key = key, restoredCount = snapshot.recordCount)
            }
        }
    }
}
