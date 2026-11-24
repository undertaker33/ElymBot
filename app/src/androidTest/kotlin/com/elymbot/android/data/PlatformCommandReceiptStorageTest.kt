package com.elymbot.android.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformCommandReceiptStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun commandReceiptTransitionsAreSingleUseAndKeepOriginalRoute() = runBlocking {
        val database = newDatabase()
        try {
            val dao = database.platformRouteDao()
            val item = inbound()
            seedSession(database, item.sessionId)
            val now = System.currentTimeMillis()
            assertTrue(dao.insertInbound(item) >= 0L)

            assertEquals(1, dao.queueCommandReceipt(item.id, "Checkpoint saved.", now + 1))
            assertEquals(0, dao.queueCommandReceipt(item.id, "Duplicate receipt.", now + 2))

            val pending = dao.pendingCommandReceipts().single()
            assertEquals("/mark", pending.text)
            assertEquals("Checkpoint saved.", pending.commandReceiptText)
            assertEquals("qq", pending.targetChannelId)
            assertEquals("private:actor-1", pending.targetConversationId)
            assertEquals("account-1", pending.targetAccountId)
            assertEquals("actor-1", pending.targetActorId)
            assertEquals("quoted-1", pending.referenceMessageId)
            assertEquals("instance-1", pending.installationInstanceId)

            assertEquals(1, dao.markCommandReceiptSending(item.id, now + 3))
            assertEquals(0, dao.markCommandReceiptSending(item.id, now + 4))
            assertEquals(1, dao.finishCommandReceipt(item.id, PlatformCommandReceiptStatus.DELIVERED, now + 5))
            assertTrue(dao.pendingCommandReceipts().isEmpty())

            val stored = dao.getInbound("qq", "qq", "account-1", "private:actor-1", "remote-1")
            assertEquals(PlatformCommandReceiptStatus.DELIVERED, stored?.status)
            assertEquals("/mark", stored?.text)
            assertEquals("Checkpoint saved.", stored?.commandReceiptText)
        } finally {
            database.close()
        }
    }

    @Test
    fun interruptedCommandReceiptBecomesUnknownAndIsNotPendingAgain() = runBlocking {
        val database = newDatabase()
        try {
            val dao = database.platformRouteDao()
            val item = inbound()
            seedSession(database, item.sessionId)
            val now = System.currentTimeMillis()
            dao.insertInbound(item)
            assertEquals(1, dao.queueCommandReceipt(item.id, "Done.", now + 1))
            assertEquals(1, dao.markCommandReceiptSending(item.id, now + 2))

            assertEquals(1, dao.recoverSendingCommandReceiptsAsUnknown(now + 3))
            assertEquals(0, dao.recoverSendingCommandReceiptsAsUnknown(now + 4))
            assertTrue(dao.pendingCommandReceipts().isEmpty())
            assertEquals(0, dao.markCommandReceiptSending(item.id, now + 5))

            val stored = dao.getInbound("qq", "qq", "account-1", "private:actor-1", "remote-1")
            assertEquals(PlatformCommandReceiptStatus.UNKNOWN, stored?.status)
            assertEquals("/mark", stored?.text)
        } finally {
            database.close()
        }
    }

    @Test
    fun v7MigrationKeepsInboundTextAndAddsNullReceiptText() {
        val databaseName = "platform-command-receipt-v7-" + System.nanoTime().toString() + ".db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(database: SupportSQLiteDatabase) {
                            database.execSQL(
                                "CREATE TABLE platform_inbound_inbox (" +
                                    "id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, text TEXT NOT NULL)",
                            )
                            database.execSQL(
                                "INSERT INTO platform_inbound_inbox (id, status, text) " +
                                    "VALUES ('existing-inbox', 'QUEUED', '/mark')",
                            )
                        }

                        override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        try {
            val database = helper.writableDatabase
            ElymBotDatabase.MIGRATION_7_8.migrate(database)
            database.query(
                "SELECT id, status, text, commandReceiptText FROM platform_inbound_inbox",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("existing-inbox", cursor.getString(0))
                assertEquals("QUEUED", cursor.getString(1))
                assertEquals("/mark", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, ElymBotDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private suspend fun seedSession(database: ElymBotDatabase, sessionId: String) {
        val personaId = "persona-1"
        database.personaDao().insert(
            PersonaEntity(
                id = personaId,
                name = "Command test persona",
                description = "",
                tagsValue = "",
                prompt = "",
                favorite = false,
                coverPath = null,
                atlasTile = 0,
                coverX = 0.5f,
                coverY = 0.5f,
                avatarX = 0.5f,
                avatarY = 0.5f,
                sortOrder = 0,
            ),
        )
        database.conversationDao().insert(
            ConversationEntity(
                id = sessionId,
                kind = "DEDICATED",
                personaId = personaId,
                title = "Command test session",
                mainKey = null,
                currentCheckpointId = "checkpoint-1",
                nextTurnSequence = 1L,
                unread = false,
                createdAt = 100L,
                lastActivityAt = 100L,
            ),
        )
    }

    private fun inbound() = PlatformInboundInboxEntity(
        id = "inbox-1",
        pluginId = "qq",
        installationInstanceId = "instance-1",
        channelId = "qq",
        accountId = "account-1",
        conversationId = "private:actor-1",
        actorId = "actor-1",
        messageId = "remote-1",
        sessionId = "session-1",
        processing = "TRIGGER_AGENT",
        historyVisibility = "PRIVATE",
        policyRevision = 7,
        status = "QUEUED",
        text = "/mark",
        targetChannelId = "qq",
        targetConversationId = "private:actor-1",
        targetAccountId = "account-1",
        targetActorId = "actor-1",
        referenceMessageId = "quoted-1",
        turnSequence = null,
        receivedAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L,
    )
}