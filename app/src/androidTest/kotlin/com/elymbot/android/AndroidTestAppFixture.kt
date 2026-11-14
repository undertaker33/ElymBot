package com.elymbot.android

import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.data.ConversationEntity
import com.elymbot.android.data.CheckpointEntity
import com.elymbot.android.data.ElymBotDataRepository
import com.elymbot.android.data.ElymBotDatabase
import com.elymbot.android.data.PersonaEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class AndroidTestAppFixture {
    lateinit var context: android.content.Context
        private set
    lateinit var database: ElymBotDatabase
        private set
    lateinit var prefix: String
        private set
    lateinit var personaId: String
        private set
    lateinit var personaName: String
        private set

    private var previousMain: ConversationEntity? = null
    private var initialDedicatedIds: Set<String> = emptySet()

    fun start() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefix = "w01-${UUID.randomUUID()}"
        personaId = "${prefix}-persona"
        personaName = "${prefix} test persona"
        database = ElymBotDatabase.open(context)
        runBlocking {
            withContext(Dispatchers.IO) {
                val personaDao = database.personaDao()
                val conversationDao = database.conversationDao()
                previousMain = conversationDao.getMain()
                initialDedicatedIds = conversationDao.getAllDedicated().map { it.id }.toSet()
                // Seed a test-owned persona before initialization so tests work with an empty production database.
                personaDao.insert(
                    PersonaEntity(
                        id = personaId,
                        name = personaName,
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
                        sortOrder = Int.MIN_VALUE,
                    ),
                )
                database.withTransaction {
                    val main = previousMain
                    if (main != null) {
                        conversationDao.update(main.copy(personaId = personaId))
                    } else {
                        val timestamp = System.currentTimeMillis()
                        conversationDao.insert(
                            ConversationEntity(
                                id = "main",
                                kind = "MAIN",
                                personaId = personaId,
                                title = null,
                                mainKey = "main",
                                currentCheckpointId = "empty:main",
                                nextTurnSequence = 1,
                                unread = false,
                                createdAt = timestamp,
                                lastActivityAt = timestamp,
                            ),
                        )
                        database.checkpointDao().insert(
                            CheckpointEntity("empty:main", "main", 1, 0, 1, timestamp),
                        )
                    }
                }
                ElymBotDataRepository(context, database).initialize()
                conversationDao.setActivePersona(personaId)
            }
        }
    }

    fun close() {
        if (!::database.isInitialized) return
        try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    val app = context.applicationContext as ElymBotApplication
                    val sessionService = app.uiService
                    val createdSessions = database.conversationDao().getDedicated(personaId)
                        .filter { it.id !in initialDedicatedIds }
                    if (createdSessions.isNotEmpty()) {
                        val createdIds = createdSessions.map { it.id }
                        try {
                            createdSessions.forEach { session ->
                                if (database.turnDao().isRunning(session.id)) sessionService.stop(session.id)
                            }
                            sessionService.deleteSessions(personaId, createdIds.toSet())
                        } catch (_: Exception) {
                            database.conversationDao().delete(createdIds)
                        }
                    }

                    val main = previousMain
                    if (main != null) database.conversationDao().update(main)
                    else database.conversationDao().delete(listOf("main"))
                    database.personaDao().delete(personaId)
                }
            }
        } finally {
            database.close()
        }
    }
}

internal class AndroidTestAppFixtureRule : TestRule {
    lateinit var fixture: AndroidTestAppFixture
        private set

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                var created: AndroidTestAppFixture? = null
                try {
                    val initialized = AndroidTestAppFixture()
                    created = initialized
                    fixture = initialized
                    initialized.start()
                    base.evaluate()
                } finally {
                    created?.close()
                }
            }
        }
}
