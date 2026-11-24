package com.elymbot.android.data

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A host-owned mapping from one normalized external route to one read-only App conversation. */
@Entity(
    tableName = "platform_conversation_bindings",
    primaryKeys = ["pluginId", "channelId", "accountId", "conversationId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"], unique = true)],
)
data class PlatformConversationBindingEntity(
    val pluginId: String,
    val installationInstanceId: String,
    val channelId: String,
    val accountId: String,
    val conversationId: String,
    val actorId: String,
    val sessionId: String,
    val historyVisibility: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "platform_owner_bindings")
data class PlatformOwnerBindingEntity(
    @PrimaryKey val pluginId: String,
    val installationInstanceId: String,
    val channelId: String,
    val accountId: String,
    val conversationId: String,
    val actorId: String,
    @ColumnInfo(defaultValue = "1") val ownerBound: Boolean = true,
    @ColumnInfo(defaultValue = "0") val autoGrantedPrivatePermission: Boolean = false,
    val updatedAtEpochMillis: Long,
)

/** Stable inbound idempotency record and durable queue for accepted platform messages. */
@Entity(
    tableName = "platform_inbound_inbox",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pluginId", "channelId", "accountId", "conversationId", "messageId"], unique = true),
        Index(value = ["sessionId", "status", "receivedAtEpochMillis"]),
        Index(value = ["accountId", "conversationId", "installationInstanceId", "receivedAtEpochMillis"]),
    ],
)
data class PlatformInboundInboxEntity(
    @PrimaryKey val id: String,
    val pluginId: String,
    val installationInstanceId: String,
    val channelId: String,
    val accountId: String,
    val conversationId: String,
    val actorId: String,
    val messageId: String,
    val sessionId: String,
    val processing: String,
    val historyVisibility: String,
    val policyRevision: Long,
    val status: String,
    val text: String,
    val commandReceiptText: String? = null,
    val targetChannelId: String?,
    val targetConversationId: String?,
    val targetAccountId: String?,
    val targetActorId: String?,
    val referenceMessageId: String?,
    val turnSequence: Long?,
    val receivedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Ordered host-owned projection of the normalized message, including unsupported media metadata. */
@Entity(
    tableName = "platform_inbound_segments",
    primaryKeys = ["inboxId", "segmentIndex"],
    foreignKeys = [
        ForeignKey(
            entity = PlatformInboundInboxEntity::class,
            parentColumns = ["id"],
            childColumns = ["inboxId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["inboxId"])],
)
data class PlatformInboundSegmentEntity(
    val inboxId: String,
    val segmentIndex: Int,
    val kind: String,
    val textValue: String?,
    val resourceId: String?,
    val mediaType: String?,
    val displayName: String?,
    val declaredSizeBytes: Long?,
    val privatePath: String?,
    val byteSize: Long?,
    val projectionStatus: String,
)

/** Source and original reply route for a platform-created Agent turn. */
@Entity(
    tableName = "platform_turn_routes",
    primaryKeys = ["sessionId", "turnSequence"],
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["sessionId", "sequence"],
            childColumns = ["sessionId", "turnSequence"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pluginId", "installationInstanceId", "channelId", "accountId"])],
)
data class PlatformTurnRouteEntity(
    val sessionId: String,
    val turnSequence: Long,
    val pluginId: String,
    val installationInstanceId: String,
    val channelId: String,
    val accountId: String,
    val conversationId: String,
    val actorId: String,
    val targetChannelId: String,
    val targetConversationId: String,
    val targetAccountId: String,
    val targetActorId: String,
    val referenceMessageId: String?,
)

internal object PlatformCommandReceiptStatus {
    const val PENDING = "COMMAND_REPLY_PENDING"
    const val SENDING = "COMMAND_REPLY_SENDING"
    const val DELIVERED = "COMMAND_REPLY_DELIVERED"
    const val FAILED = "COMMAND_REPLY_FAILED"
    const val UNKNOWN = "COMMAND_REPLY_UNKNOWN"
}

@Dao
interface PlatformRouteDao {
    @Query(
        "SELECT * FROM platform_conversation_bindings " +
            "WHERE pluginId = :pluginId AND channelId = :channelId " +
            "AND accountId = :accountId AND conversationId = :conversationId LIMIT 1",
    )
    suspend fun getBinding(
        pluginId: String,
        channelId: String,
        accountId: String,
        conversationId: String,
    ): PlatformConversationBindingEntity?

    @Query("SELECT * FROM platform_owner_bindings WHERE pluginId = :pluginId LIMIT 1")
    suspend fun getOwnerBinding(pluginId: String): PlatformOwnerBindingEntity?

    @Query("SELECT * FROM platform_owner_bindings WHERE pluginId = :pluginId LIMIT 1")
    fun observeOwnerBinding(pluginId: String): Flow<PlatformOwnerBindingEntity?>

    @Query("SELECT * FROM platform_conversation_bindings WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getForSession(sessionId: String): PlatformConversationBindingEntity?

    @Query("SELECT * FROM platform_conversation_bindings WHERE sessionId = :sessionId LIMIT 1")
    fun observeForSession(sessionId: String): Flow<PlatformConversationBindingEntity?>

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE sessionId = :sessionId " +
            "AND processing = 'CONTEXT_ONLY' AND historyVisibility = 'GROUP_PUBLIC' " +
            "AND status = 'STORED_CONTEXT' ORDER BY receivedAtEpochMillis, id",
    )
    fun observeGroupContextForSession(sessionId: String): Flow<List<PlatformInboundInboxEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBinding(item: PlatformConversationBindingEntity): Long

    @Update
    suspend fun updateBinding(item: PlatformConversationBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOwnerBinding(item: PlatformOwnerBindingEntity)

    @Query("DELETE FROM platform_owner_bindings WHERE pluginId = :pluginId")
    suspend fun deleteOwnerBinding(pluginId: String): Int

    @Query(
        "UPDATE platform_owner_bindings SET ownerBound = 0, autoGrantedPrivatePermission = :autoGrantedPrivatePermission, " +
            "updatedAtEpochMillis = :now WHERE pluginId = :pluginId",
    )
    suspend fun markOwnerUnbound(pluginId: String, autoGrantedPrivatePermission: Boolean, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInbound(item: PlatformInboundInboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInboundSegments(items: List<PlatformInboundSegmentEntity>)

    @Query("SELECT * FROM platform_inbound_segments WHERE inboxId = :inboxId ORDER BY segmentIndex")
    suspend fun inboundSegments(inboxId: String): List<PlatformInboundSegmentEntity>

    @Query("SELECT privatePath FROM platform_inbound_segments WHERE privatePath IS NOT NULL")
    suspend fun referencedInboundMediaPaths(): List<String>

    @Query("UPDATE platform_inbound_segments SET privatePath = NULL WHERE inboxId = :inboxId")
    suspend fun clearInboundMediaPaths(inboxId: String): Int

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE pluginId = :pluginId AND channelId = :channelId " +
            "AND accountId = :accountId AND conversationId = :conversationId AND messageId = :messageId LIMIT 1",
    )
    suspend fun getInbound(
        pluginId: String,
        channelId: String,
        accountId: String,
        conversationId: String,
        messageId: String,
    ): PlatformInboundInboxEntity?

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE sessionId = :sessionId AND status = 'QUEUED' " +
            "ORDER BY receivedAtEpochMillis, id",
    )
    suspend fun queuedForSession(sessionId: String): List<PlatformInboundInboxEntity>

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE sessionId = :sessionId " +
            "AND turnSequence = :turnSequence AND processing = 'TRIGGER_AGENT' LIMIT 1",
    )
    suspend fun getTriggeredInboundForTurn(sessionId: String, turnSequence: Long): PlatformInboundInboxEntity?

    @Query("SELECT DISTINCT sessionId FROM platform_inbound_inbox WHERE status = 'QUEUED' ORDER BY sessionId")
    suspend fun queuedSessionIds(): List<String>

    @Query("UPDATE platform_inbound_inbox SET status = 'RUNNING', turnSequence = :turnSequence, updatedAtEpochMillis = :now WHERE id = :id AND status = 'QUEUED'")
    suspend fun markRunning(id: String, turnSequence: Long, now: Long): Int

    @Query("UPDATE platform_inbound_inbox SET status = :status, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun updateInboundStatus(id: String, status: String, now: Long): Int

    @Query("UPDATE platform_inbound_inbox SET status = 'FAILED_UNKNOWN', updatedAtEpochMillis = :now WHERE status = 'RUNNING'")
    suspend fun recoverRunningAsUnknown(now: Long): Int

    @Query(
        "UPDATE platform_inbound_inbox SET status = 'COMMAND_REPLY_PENDING', commandReceiptText = :receipt, " +
            "updatedAtEpochMillis = :now WHERE id = :id AND status = 'QUEUED' AND commandReceiptText IS NULL",
    )
    suspend fun queueCommandReceipt(id: String, receipt: String, now: Long): Int

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE status = 'COMMAND_REPLY_PENDING' " +
            "ORDER BY receivedAtEpochMillis, id",
    )
    suspend fun pendingCommandReceipts(): List<PlatformInboundInboxEntity>

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE status = 'COMMAND_REPLY_PENDING' " +
            "ORDER BY receivedAtEpochMillis, id",
    )
    fun observePendingCommandReceipts(): Flow<List<PlatformInboundInboxEntity>>

    @Query(
        "UPDATE platform_inbound_inbox SET status = 'COMMAND_REPLY_SENDING', updatedAtEpochMillis = :now " +
            "WHERE id = :id AND status = 'COMMAND_REPLY_PENDING' AND commandReceiptText IS NOT NULL",
    )
    suspend fun markCommandReceiptSending(id: String, now: Long): Int

    @Query(
        "UPDATE platform_inbound_inbox SET status = :status, updatedAtEpochMillis = :now " +
            "WHERE id = :id AND status = 'COMMAND_REPLY_SENDING' AND " +
            ":status IN ('COMMAND_REPLY_DELIVERED', 'COMMAND_REPLY_FAILED', 'COMMAND_REPLY_UNKNOWN')",
    )
    suspend fun finishCommandReceipt(id: String, status: String, now: Long): Int

    @Query(
        "UPDATE platform_inbound_inbox SET status = 'COMMAND_REPLY_FAILED', updatedAtEpochMillis = :now " +
            "WHERE id = :id AND status = 'COMMAND_REPLY_PENDING'",
    )
    suspend fun failPendingCommandReceipt(id: String, now: Long): Int

    @Query(
        "UPDATE platform_inbound_inbox SET status = 'COMMAND_REPLY_UNKNOWN', updatedAtEpochMillis = :now " +
            "WHERE status = 'COMMAND_REPLY_SENDING'",
    )
    suspend fun recoverSendingCommandReceiptsAsUnknown(now: Long): Int

    @Query(
        "SELECT * FROM platform_inbound_inbox WHERE sessionId = :sessionId " +
            "AND processing = 'CONTEXT_ONLY' AND historyVisibility = 'GROUP_PUBLIC' " +
            "AND accountId = :accountId AND conversationId = :conversationId " +
            "AND installationInstanceId = :installationInstanceId AND status = 'STORED_CONTEXT' " +
            "ORDER BY receivedAtEpochMillis DESC LIMIT :limit",
    )
    suspend fun recentGroupContext(
        sessionId: String,
        accountId: String,
        conversationId: String,
        installationInstanceId: String,
        limit: Int,
    ): List<PlatformInboundInboxEntity>

    @Query(
        "DELETE FROM platform_inbound_inbox WHERE sessionId = :sessionId " +
            "AND processing = 'CONTEXT_ONLY' AND historyVisibility = 'GROUP_PUBLIC' " +
            "AND installationInstanceId = :installationInstanceId AND id NOT IN (" +
            "SELECT id FROM platform_inbound_inbox WHERE sessionId = :sessionId " +
            "AND processing = 'CONTEXT_ONLY' AND historyVisibility = 'GROUP_PUBLIC' " +
            "AND installationInstanceId = :installationInstanceId " +
            "ORDER BY receivedAtEpochMillis DESC LIMIT :limit)",
    )
    suspend fun pruneGroupContext(sessionId: String, installationInstanceId: String, limit: Int): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTurnRoute(item: PlatformTurnRouteEntity)

    @Query("SELECT * FROM platform_turn_routes WHERE sessionId = :sessionId AND turnSequence = :turnSequence LIMIT 1")
    suspend fun getTurnRoute(sessionId: String, turnSequence: Long): PlatformTurnRouteEntity?

    @Query("SELECT * FROM platform_turn_routes WHERE sessionId = :sessionId AND turnSequence = :turnSequence LIMIT 1")
    fun observeTurnRoute(sessionId: String, turnSequence: Long): Flow<PlatformTurnRouteEntity?>
}
