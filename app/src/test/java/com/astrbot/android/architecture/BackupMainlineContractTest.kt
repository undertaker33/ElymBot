package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMainlineContractTest {
    private val projectRoot: Path = detectProjectRoot()
    private val appMainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val appIntegrationMainRoot: Path =
        projectRoot.resolve("app-integration/src/main/java/com/astrbot/android")

    @Test
    fun backup_screen_must_use_hilt_backup_services_not_static_repositories() {
        val source = backupScreenPath().readText()
        val forbiddenTokens = listOf("AppBackupRepository", "ConversationBackupRepository")

        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "BackupScreen must call Hilt-provided backup services/ViewModels instead of static repositories. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun backup_data_ports_must_use_qq_login_port_not_napcat_static_repository() {
        val source = appIntegrationMainRoot.resolve("di/BackupDataPortAdapter.kt").readText()

        assertTrue(
            "BackupDataPortAdapter.kt must not read NapCatLoginRepository directly.",
            !source.contains("NapCatLoginRepository"),
        )
        assertTrue(
            "BackupDataPortAdapter.kt must receive QQ login state through QqLoginRepositoryPort.",
            source.contains("QqLoginRepositoryPort"),
        )
    }

    @Test
    fun backup_mainline_must_expose_services_and_participant_contract() {
        val appBackupSource = appMainRoot.resolve("core/db/backup/AppBackupRepository.kt").readText()
        val conversationBackupSource = appMainRoot.resolve("core/db/backup/ConversationBackupRepository.kt").readText()
        val participantSource = projectRoot
            .resolve("core/backup/src/main/java/com/astrbot/android/core/backup/BackupParticipant.kt")

        assertTrue(
            "App backup production entry must expose an injectable AppBackupService.",
            appBackupSource.contains("class AppBackupService"),
        )
        assertTrue(
            "Conversation backup production entry must expose an injectable ConversationBackupService.",
            conversationBackupSource.contains("class ConversationBackupService"),
        )
        assertTrue(
            "BackupParticipant contract must live in :core:backup.",
            participantSource.exists() && participantSource.readText().contains("interface BackupParticipant"),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private fun backupScreenPath(): Path {
        val candidates = listOf(
            appMainRoot.resolve("feature/settings/presentation/BackupScreen.kt"),
            projectRoot.resolve(
                "feature/settings/presentation/src/main/java/com/astrbot/android/ui/settings/BackupScreen.kt",
            ),
        )
        return candidates.firstOrNull { path -> path.exists() }
            ?: error("Unable to find BackupScreen.kt in known settings locations")
    }
}
