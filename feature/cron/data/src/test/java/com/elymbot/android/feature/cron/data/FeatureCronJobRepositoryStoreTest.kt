package com.elymbot.android.feature.cron.data

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.data.db.CronJobEntity
import com.elymbot.android.data.db.CronJobExecutionRecordEntity
import com.elymbot.android.data.db.cron.CronJobDao
import com.elymbot.android.data.db.cron.CronJobExecutionRecordDao
import com.elymbot.android.feature.cron.domain.model.CronJob
import com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCronJobRepositoryStoreTest {
    @Test
    fun create_maps_legacy_payload_target_into_flat_entity_fields() = runBlocking {
        val dao = InMemoryCronJobDao()
        val store = newStore(dao = dao)
        val job = CronJob(
            jobId = "job-1",
            name = "Drink water",
            description = "Reminder",
            payloadJson = """
                {
                  "session": "legacy-session",
                  "target": {
                    "platform": "app",
                    "conversation_id": "chat-1",
                    "bot_id": "bot-1",
                    "config_profile_id": "cfg-1",
                    "persona_id": "persona-1",
                    "provider_id": "provider-1",
                    "origin": "tool"
                  },
                  "note": "drink water"
                }
            """.trimIndent(),
            createdAt = 42L,
        )

        store.create(job)

        val entity = dao.entities.getValue("job-1")
        assertEquals("app", entity.platform)
        assertEquals("chat-1", entity.conversationId)
        assertEquals("bot-1", entity.botId)
        assertEquals("cfg-1", entity.configProfileId)
        assertEquals("persona-1", entity.personaId)
        assertEquals("provider-1", entity.providerId)
        assertEquals("tool", entity.origin)
        assertEquals(42L, entity.createdAt)
        assertTrue(entity.updatedAt > 0L)

        val roundTripped = store.getByJobId("job-1")
        assertEquals("chat-1", roundTripped?.conversationId)
        assertEquals("provider-1", roundTripped?.providerId)
    }

    @Test
    fun updateStatus_preserves_existing_last_run_and_error_when_optional_values_are_absent() = runBlocking {
        val dao = InMemoryCronJobDao()
        val store = newStore(dao = dao)
        dao.seed(
            cronEntity(
                jobId = "job-1",
                status = "scheduled",
                lastRunAt = 100L,
                lastError = "old-error",
                updatedAt = 200L,
            ),
        )

        store.updateStatus(jobId = "job-1", status = "paused")

        val entity = dao.entities.getValue("job-1")
        assertEquals("paused", entity.status)
        assertEquals(100L, entity.lastRunAt)
        assertEquals("old-error", entity.lastError)
        assertTrue(entity.updatedAt >= 200L)
    }

    @Test
    fun execution_record_queries_clamp_recent_limit_and_map_records() = runBlocking {
        val executionDao = InMemoryExecutionRecordDao()
        val store = newStore(executionDao = executionDao)
        val record = CronJobExecutionRecord(
            executionId = "exec-1",
            jobId = "job-1",
            status = "SUCCEEDED",
            startedAt = 10L,
            completedAt = 30L,
            durationMs = 20L,
            attempt = 2,
            trigger = "run_now",
            deliverySummary = """{"delivered_message_count":1}""",
        )

        store.recordExecutionStarted(record)
        store.updateExecutionRecord(record.copy(status = "FAILED", errorCode = "provider_unavailable"))

        val recent = store.listRecentExecutionRecords(jobId = "job-1", limit = 0)
        val latest = store.latestExecutionRecord("job-1")

        assertEquals(1, executionDao.lastRequestedLimit)
        assertEquals("FAILED", recent.single().status)
        assertEquals("provider_unavailable", latest?.errorCode)
    }

    @Test
    fun port_adapter_forwards_repository_store_operations() = runBlocking {
        val dao = InMemoryCronJobDao()
        val executionDao = InMemoryExecutionRecordDao()
        val adapter = FeatureCronJobRepositoryPortAdapter(
            newStore(dao = dao, executionDao = executionDao),
        )
        val job = CronJob(jobId = "job-1", name = "Created", enabled = true)
        val record = CronJobExecutionRecord(executionId = "exec-1", jobId = "job-1", status = "RUNNING")

        adapter.create(job)
        adapter.update(job.copy(name = "Updated", enabled = false))
        adapter.recordExecutionStarted(record)

        assertEquals("Updated", adapter.getByJobId("job-1")?.name)
        assertEquals(emptyList<CronJob>(), adapter.listEnabled())
        assertEquals("exec-1", adapter.latestExecutionRecord("job-1")?.executionId)
    }

    private fun newStore(
        dao: InMemoryCronJobDao = InMemoryCronJobDao(),
        executionDao: InMemoryExecutionRecordDao = InMemoryExecutionRecordDao(),
        logger: RecordingRuntimeLogger = RecordingRuntimeLogger(),
    ): FeatureCronJobRepositoryStore {
        return FeatureCronJobRepositoryStore(
            dao = dao,
            executionRecordDao = executionDao,
            runtimeLogger = logger,
        )
    }
}

private class InMemoryCronJobDao : CronJobDao {
    private val observed = MutableStateFlow<List<CronJobEntity>>(emptyList())
    val entities = linkedMapOf<String, CronJobEntity>()

    fun seed(vararg values: CronJobEntity) {
        values.forEach { entity -> entities[entity.jobId] = entity }
        observed.value = entities.values.sortedByDescending(CronJobEntity::createdAt)
    }

    override fun observeAll(): Flow<List<CronJobEntity>> = observed

    override suspend fun listAll(): List<CronJobEntity> =
        entities.values.sortedByDescending(CronJobEntity::createdAt)

    override suspend fun listByType(jobType: String): List<CronJobEntity> =
        listAll().filter { it.jobType == jobType }

    override suspend fun getByJobId(jobId: String): CronJobEntity? = entities[jobId]

    override suspend fun listEnabled(): List<CronJobEntity> =
        entities.values.filter(CronJobEntity::enabled).sortedBy(CronJobEntity::nextRunTime)

    override suspend fun upsert(entity: CronJobEntity) {
        seed(entity)
    }

    override suspend fun update(entity: CronJobEntity) {
        seed(entity)
    }

    override suspend fun deleteByJobId(jobId: String) {
        entities.remove(jobId)
        observed.value = entities.values.toList()
    }

    override suspend fun deleteAll() {
        entities.clear()
        observed.value = emptyList()
    }

    override suspend fun count(): Int = entities.size
}

private class InMemoryExecutionRecordDao : CronJobExecutionRecordDao {
    private val records = linkedMapOf<String, CronJobExecutionRecordEntity>()
    var lastRequestedLimit: Int = -1

    override suspend fun upsert(entity: CronJobExecutionRecordEntity) {
        records[entity.executionId] = entity
    }

    override suspend fun listRecentForJob(
        jobId: String,
        limit: Int,
    ): List<CronJobExecutionRecordEntity> {
        lastRequestedLimit = limit
        return records.values
            .filter { it.jobId == jobId }
            .sortedByDescending(CronJobExecutionRecordEntity::startedAt)
            .take(limit)
    }

    override suspend fun latestForJob(jobId: String): CronJobExecutionRecordEntity? =
        listRecentForJob(jobId, 1).firstOrNull()

    override suspend fun deleteForJob(jobId: String) {
        records.values.removeAll { it.jobId == jobId }
    }

    override suspend fun count(): Int = records.size
}

private class RecordingRuntimeLogger : RuntimeLogger {
    val messages = mutableListOf<String>()

    override fun append(message: String) {
        messages += message
    }
}

private fun cronEntity(
    jobId: String,
    status: String = "scheduled",
    lastRunAt: Long = 0L,
    lastError: String = "",
    updatedAt: Long = 0L,
): CronJobEntity {
    return CronJobEntity(
        jobId = jobId,
        name = "Job",
        description = "Description",
        jobType = "active_agent",
        cronExpression = "",
        timezone = "UTC",
        payloadJson = "{}",
        enabled = true,
        runOnce = false,
        platform = "app",
        conversationId = "chat-1",
        botId = "bot-1",
        configProfileId = "cfg-1",
        personaId = "",
        providerId = "provider-1",
        origin = "test",
        status = status,
        lastRunAt = lastRunAt,
        nextRunTime = 0L,
        lastError = lastError,
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}
