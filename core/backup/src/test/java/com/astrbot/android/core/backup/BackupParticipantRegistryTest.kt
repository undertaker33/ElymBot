package com.astrbot.android.core.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupParticipantRegistryTest {
    @Test
    fun registry_orders_participants_by_key_and_filters_supported_coverage() = runTest {
        val registry = BackupParticipantRegistry(
            setOf(
                participant(key = "qq", coverage = BackupParticipantCoverage.PLANNED),
                participant(key = "bots", coverage = BackupParticipantCoverage.SUPPORTED),
            ),
        )

        assertEquals(listOf("bots", "qq"), registry.participants.map { it.key })
        assertEquals(listOf("bots"), registry.supportedParticipants().map { it.key })
    }

    @Test
    fun registry_rejects_duplicate_participant_keys() {
        try {
            BackupParticipantRegistry(
                setOf(
                    participant(key = "bots", displayName = "Bots A"),
                    participant(key = "bots", displayName = "Bots B"),
                ),
            )
            fail("Expected duplicate participant keys to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun participant(
        key: String,
        displayName: String = key,
        coverage: BackupParticipantCoverage = BackupParticipantCoverage.SUPPORTED,
    ): BackupParticipant {
        return object : BackupParticipant {
            override val key: String = key
            override val displayName: String = displayName
            override val coverage: BackupParticipantCoverage = coverage

            override suspend fun snapshot(): BackupParticipantSnapshot {
                return BackupParticipantSnapshot(key = key)
            }

            override suspend fun restore(snapshot: BackupParticipantSnapshot): BackupParticipantRestoreResult {
                return BackupParticipantRestoreResult(key = key)
            }
        }
    }
}
