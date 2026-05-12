package com.astrbot.android.core.backup

interface BackupParticipant {
    val key: String
    val displayName: String
    val coverage: BackupParticipantCoverage

    suspend fun snapshot(): BackupParticipantSnapshot

    suspend fun restore(snapshot: BackupParticipantSnapshot): BackupParticipantRestoreResult
}

enum class BackupParticipantCoverage {
    SUPPORTED,
    PLANNED,
}

data class BackupParticipantSnapshot(
    val key: String,
    val recordCount: Int = 0,
    val hasFiles: Boolean = false,
)

data class BackupParticipantRestoreResult(
    val key: String,
    val restoredCount: Int = 0,
)

class BackupParticipantRegistry(
    participants: Set<BackupParticipant>,
) {
    val participants: List<BackupParticipant> = participants.sortedBy { participant -> participant.key }

    init {
        val duplicateKeys = participants
            .groupingBy { participant -> participant.key }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateKeys.isEmpty()) {
            "Backup participant keys must be unique: $duplicateKeys"
        }
    }

    fun supportedParticipants(): List<BackupParticipant> {
        return participants.filter { participant -> participant.coverage == BackupParticipantCoverage.SUPPORTED }
    }
}
