package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.ToolInstallationIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class FileScheduledTaskRepositoryTest {
    @Test
    fun dataRoundTripsWithSavedZoneAndRunIdentityAcrossRepositoryInstances() {
        val directory = Files.createTempDirectory("scheduled-task-store").toFile()
        val identity = ToolInstallationIdentity("scheduled-tasks", "instance-a")
        val task = TaskRecord(
            id = "task-1",
            revision = 4,
            title = "Evening review",
            prompt = "Review today's notes",
            startLocalDateTime = LocalDateTime.parse("2026-11-23T20:30"),
            timeZoneId = "America/Los_Angeles",
            repeatRule = TaskRepeatRule(TaskRepeatKind.WEEKLY),
            endCondition = TaskEndCondition(TaskEndKind.DATE, endDate = LocalDate.parse("2026-12-31")),
            enabled = true,
            eligibleFrom = Instant.parse("2026-11-23T18:00:00Z"),
            creationSource = TaskCreationSource.AGENT,
            createdAt = Instant.parse("2026-11-20T12:00:00Z"),
            updatedAt = Instant.parse("2026-11-22T12:00:00Z"),
        )
        val run = TaskRunRecord(
            taskId = task.id,
            runId = "run-1",
            occurrenceId = "occurrence-1",
            scheduleRevision = task.revision,
            plannedAt = Instant.parse("2026-11-23T04:30:00Z"),
            triggeredAt = Instant.parse("2026-11-23T04:32:00Z"),
            origin = TaskRunOrigin.SCHEDULED,
            installationIdentity = identity,
            requestId = null,
            status = TaskRunStatus.QUEUED,
            updatedAt = Instant.parse("2026-11-23T04:32:00Z"),
        )
        val state = TaskStoreState(
            tasks = mapOf(task.id to task),
            runs = mapOf(run.runId to run),
            mutationReceipts = mapOf(
                "request-1" to MutationReceipt(
                    requestId = "request-1",
                    fingerprint = "fingerprint",
                    resultKind = MutationResultKind.CREATED,
                    taskId = task.id,
                    revision = task.revision,
                ),
            ),
            firstTaskCreated = true,
            guideSkipped = true,
        )
        try {
            val repository = FileScheduledTaskRepository(directory)

            assertTrue(directory.listFiles().orEmpty().isEmpty())
            repository.transact { state to Unit }
            val restored = FileScheduledTaskRepository(directory).snapshot()

            assertEquals(state, restored)
            assertEquals("America/Los_Angeles", restored.tasks.getValue(task.id).timeZoneId)
            assertEquals(identity, restored.runs.getValue(run.runId).installationIdentity)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacyTaskStoreInfersFirstUseFromCreateReceiptAfterDeletionAndMigratesOnNextWrite() {
        val directory = Files.createTempDirectory("scheduled-task-store-legacy").toFile()
        val currentRepository = FileScheduledTaskRepository(directory)
        try {
            currentRepository.transact { state ->
                state.copy(
                    mutationReceipts = mapOf(
                        "legacy-create" to MutationReceipt(
                            requestId = "legacy-create",
                            fingerprint = "create-fingerprint",
                            resultKind = MutationResultKind.CREATED,
                            taskId = "legacy-task",
                            revision = 1,
                        ),
                        "legacy-delete" to MutationReceipt(
                            requestId = "legacy-delete",
                            fingerprint = "delete-fingerprint",
                            resultKind = MutationResultKind.DELETED,
                            taskId = "legacy-task",
                        ),
                    ),
                    firstTaskCreated = true,
                    guideSkipped = true,
                ) to Unit
            }
            val storeFile = File(directory, "scheduled-tasks.bin").toPath()
            val currentBytes = Files.readAllBytes(storeFile)
            val legacyBytes = currentBytes.copyOf(currentBytes.size - 2)
            ByteBuffer.wrap(legacyBytes).putInt(4, 1)
            Files.write(storeFile, legacyBytes)

            val legacyRepository = FileScheduledTaskRepository(directory)
            val legacyState = legacyRepository.snapshot()
            assertTrue(legacyState.firstTaskCreated)
            assertFalse(legacyState.guideSkipped)

            legacyRepository.transact { state -> state.copy(guideSkipped = true) to Unit }
            val migratedBytes = Files.readAllBytes(storeFile)
            assertEquals(2, ByteBuffer.wrap(migratedBytes).getInt(4))
            val migratedState = FileScheduledTaskRepository(directory).snapshot()
            assertTrue(migratedState.firstTaskCreated)
            assertTrue(migratedState.guideSkipped)
            assertTrue(migratedState.tasks.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
