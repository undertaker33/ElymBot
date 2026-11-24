package com.elymbot.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PersonaEntity::class,
        ConversationEntity::class,
        TurnEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        CheckpointEntity::class,
        HistorySummaryEntity::class,
        ReplySubmissionEntity::class,
        ReplyDeliveryEntity::class,
        RetiredReplyExecutionEntity::class,
        RetiredReplyReceiptEntity::class,
        ScheduledTaskRunInboxEntity::class,
        PlatformConversationBindingEntity::class,
        PlatformOwnerBindingEntity::class,
        PlatformInboundInboxEntity::class,
        PlatformInboundSegmentEntity::class,
        PlatformTurnRouteEntity::class,
        ModelServiceEntity::class,
        ModelAssignmentEntity::class,
        ModelProbeEntity::class,
        CommandNameEntity::class,
        CredentialEntity::class,
        PersonaTagEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class ElymBotDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun personaTagDao(): PersonaTagDao
    abstract fun conversationDao(): ConversationDao
    abstract fun turnDao(): TurnDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun historySummaryDao(): HistorySummaryDao
    abstract fun replySubmissionDao(): ReplySubmissionDao
    abstract fun replyDeliveryDao(): ReplyDeliveryDao
    abstract fun retiredReplyDao(): RetiredReplyDao
    abstract fun scheduledTaskRunInboxDao(): ScheduledTaskRunInboxDao
    abstract fun platformRouteDao(): PlatformRouteDao
    abstract fun modelConfigurationDao(): ModelConfigurationDao
    abstract fun credentialDao(): CredentialDao
    abstract fun commandNameDao(): CommandNameDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `persona_tags` (`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`name`))",
                )

                val names = linkedSetOf<String>()
                database.query("SELECT `tagsValue` FROM `personas` ORDER BY `sortOrder`, `id`").use { cursor ->
                    while (cursor.moveToNext()) {
                        cursor.getString(0).split('\u001f')
                            .filter(String::isNotEmpty)
                            .forEach { names.add(it) }
                    }
                }

                names.forEachIndexed { index, name ->
                    database.execSQL(
                        "INSERT INTO `persona_tags` (`name`, `sortOrder`) VALUES (?, ?)",
                        arrayOf<Any>(name, index.toLong()),
                    )
                }
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `turns` ADD COLUMN `successfulSilence` INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reply_submissions` (
                        `messageId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `turnSequence` INTEGER NOT NULL,
                        `executionId` TEXT NOT NULL,
                        `attemptId` TEXT NOT NULL,
                        `toolStepOrdinal` INTEGER NOT NULL,
                        `toolCallOrdinal` INTEGER NOT NULL,
                        `messageOrdinal` INTEGER NOT NULL,
                        `toolCallId` TEXT NOT NULL,
                        `suggestedDelayMillis` INTEGER NOT NULL,
                        `historyVisibilityTargetKey` TEXT NOT NULL,
                        `acceptedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`),
                        FOREIGN KEY(`sessionId`, `turnSequence`) REFERENCES `turns`(`sessionId`, `sequence`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reply_submissions_sessionId_executionId_attemptId_turnSequence_toolStepOrdinal_toolCallOrdinal_messageOrdinal` ON `reply_submissions` (`sessionId`, `executionId`, `attemptId`, `turnSequence`, `toolStepOrdinal`, `toolCallOrdinal`, `messageOrdinal`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reply_submissions_sessionId_turnSequence` ON `reply_submissions` (`sessionId`, `turnSequence`)",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reply_deliveries` (
                        `id` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `targetKey` TEXT NOT NULL,
                        `targetKind` TEXT NOT NULL,
                        `channelId` TEXT,
                        `conversationId` TEXT,
                        `accountId` TEXT,
                        `actorId` TEXT,
                        `pluginId` TEXT,
                        `installationInstanceId` TEXT,
                        `status` TEXT NOT NULL,
                        `historyVisibilityGate` INTEGER NOT NULL,
                        `availableAtEpochMillis` INTEGER NOT NULL,
                        `remoteMessageId` TEXT,
                        `failureCode` TEXT,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`messageId`) REFERENCES `reply_submissions`(`messageId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reply_deliveries_messageId_targetKey` ON `reply_deliveries` (`messageId`, `targetKey`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reply_deliveries_status_availableAtEpochMillis` ON `reply_deliveries` (`status`, `availableAtEpochMillis`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reply_deliveries_installationInstanceId_status` ON `reply_deliveries` (`installationInstanceId`, `status`)",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `retired_reply_executions` (
                        `sessionId` TEXT NOT NULL,
                        `executionId` TEXT NOT NULL,
                        `attemptId` TEXT NOT NULL,
                        `turnSequence` INTEGER NOT NULL,
                        `retiredAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`, `executionId`, `attemptId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_retired_reply_executions_sessionId_turnSequence` ON `retired_reply_executions` (`sessionId`, `turnSequence`)",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `retired_reply_receipts` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `turnSequence` INTEGER NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `targetKey` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `accountId` TEXT,
                        `actorId` TEXT,
                        `pluginId` TEXT NOT NULL,
                        `installationInstanceId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `remoteMessageId` TEXT,
                        `retiredAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_retired_reply_receipts_sessionId_messageId_targetKey` ON `retired_reply_receipts` (`sessionId`, `messageId`, `targetKey`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_retired_reply_receipts_sessionId_turnSequence` ON `retired_reply_receipts` (`sessionId`, `turnSequence`)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_task_run_inbox` (
                        `runId` TEXT NOT NULL,
                        `taskId` TEXT NOT NULL,
                        `occurrenceId` TEXT,
                        `scheduleRevision` INTEGER NOT NULL,
                        `plannedAtEpochMillis` INTEGER,
                        `triggeredAtEpochMillis` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `timeZoneId` TEXT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `installationPluginId` TEXT NOT NULL,
                        `installationInstanceId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `sessionId` TEXT,
                        `turnSequence` INTEGER,
                        `failureKind` TEXT,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`runId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scheduled_task_run_inbox_status_triggeredAtEpochMillis_runId` ON `scheduled_task_run_inbox` (`status`, `triggeredAtEpochMillis`, `runId`)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `scheduled_task_run_inbox` ADD COLUMN `receiptPending` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `platform_conversation_bindings` (
                        `pluginId` TEXT NOT NULL,
                        `installationInstanceId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `actorId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `historyVisibility` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`pluginId`, `channelId`, `accountId`, `conversationId`),
                        FOREIGN KEY(`sessionId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_platform_conversation_bindings_sessionId` ON `platform_conversation_bindings` (`sessionId`)",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `platform_owner_bindings` (
                        `pluginId` TEXT NOT NULL,
                        `installationInstanceId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `actorId` TEXT NOT NULL,
                        `ownerBound` INTEGER NOT NULL DEFAULT 1,
                        `autoGrantedPrivatePermission` INTEGER NOT NULL DEFAULT 0,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`pluginId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `platform_inbound_inbox` (
                        `id` TEXT NOT NULL,
                        `pluginId` TEXT NOT NULL,
                        `installationInstanceId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `actorId` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `processing` TEXT NOT NULL,
                        `historyVisibility` TEXT NOT NULL,
                        `policyRevision` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `targetChannelId` TEXT,
                        `targetConversationId` TEXT,
                        `targetAccountId` TEXT,
                        `targetActorId` TEXT,
                        `referenceMessageId` TEXT,
                        `turnSequence` INTEGER,
                        `receivedAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_platform_inbound_inbox_pluginId_channelId_accountId_conversationId_messageId` ON `platform_inbound_inbox` (`pluginId`, `channelId`, `accountId`, `conversationId`, `messageId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_platform_inbound_inbox_sessionId_status_receivedAtEpochMillis` ON `platform_inbound_inbox` (`sessionId`, `status`, `receivedAtEpochMillis`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_platform_inbound_inbox_accountId_conversationId_installationInstanceId_receivedAtEpochMillis` ON `platform_inbound_inbox` (`accountId`, `conversationId`, `installationInstanceId`, `receivedAtEpochMillis`)",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `platform_turn_routes` (
                        `sessionId` TEXT NOT NULL,
                        `turnSequence` INTEGER NOT NULL,
                        `pluginId` TEXT NOT NULL,
                        `installationInstanceId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `actorId` TEXT NOT NULL,
                        `targetChannelId` TEXT NOT NULL,
                        `targetConversationId` TEXT NOT NULL,
                        `targetAccountId` TEXT NOT NULL,
                        `targetActorId` TEXT NOT NULL,
                        `referenceMessageId` TEXT,
                        PRIMARY KEY(`sessionId`, `turnSequence`),
                        FOREIGN KEY(`sessionId`, `turnSequence`) REFERENCES `turns`(`sessionId`, `sequence`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_platform_turn_routes_pluginId_installationInstanceId_channelId_accountId` ON `platform_turn_routes` (`pluginId`, `installationInstanceId`, `channelId`, `accountId`)",
                )
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `platform_inbound_segments` (
                        `inboxId` TEXT NOT NULL,
                        `segmentIndex` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `textValue` TEXT,
                        `resourceId` TEXT,
                        `mediaType` TEXT,
                        `displayName` TEXT,
                        `declaredSizeBytes` INTEGER,
                        `privatePath` TEXT,
                        `byteSize` INTEGER,
                        `projectionStatus` TEXT NOT NULL,
                        PRIMARY KEY(`inboxId`, `segmentIndex`),
                        FOREIGN KEY(`inboxId`) REFERENCES `platform_inbound_inbox`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_platform_inbound_segments_inboxId` ON `platform_inbound_segments` (`inboxId`)",
                )
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE platform_inbound_inbox ADD COLUMN commandReceiptText TEXT",
                )
            }
        }

        fun open(context: Context): ElymBotDatabase = open(context, "elymbot.db")

        internal fun open(context: Context, databaseName: String): ElymBotDatabase =
            Room.databaseBuilder(context.applicationContext, ElymBotDatabase::class.java, databaseName)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .build()
    }
}
