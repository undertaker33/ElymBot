package com.astrbot.android.feature.chat.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureConversationRepositoryPortAdapterContractTest {
    private val projectRoot: File = detectProjectRoot()
    private val sourceRoots: List<File> = listOf(
        projectRoot.resolve("feature/chat/impl/src/main/java/com/astrbot/android"),
        projectRoot.resolve("app/src/main/java/com/astrbot/android"),
        projectRoot.resolve("src/main/java/com/astrbot/android"),
    )

    @Test
    fun semantic_adapter_file_exists_and_legacy_file_is_removed() {
        val semantic = productionFile("feature/chat/data/FeatureConversationRepositoryPortAdapter.kt")

        val legacyCandidates = listOf(
            productionFileOrNull("feature/chat/data/LegacyConversationRepositoryAdapter.kt"),
        )

        assertTrue(semantic.exists())
        assertTrue(legacyCandidates.none { it?.exists() == true })
    }

    @Test
    fun semantic_adapter_explicitly_wraps_conversation_repository_port() {
        val source = productionFile("feature/chat/data/FeatureConversationRepositoryPortAdapter.kt").readText()

        assertTrue(source.contains("ConversationRepositoryPort"))
        assertTrue(source.contains("FeatureConversationRepository"))
        assertFalse(source.contains("RuntimeOrchestrator"))
        assertFalse(source.contains("LegacyConversationRepositoryAdapter"))
    }

    private fun productionFile(relativePath: String): File {
        return productionFileOrNull(relativePath)
            ?: error("Missing production file: $relativePath")
    }

    private fun productionFileOrNull(relativePath: String): File? {
        return sourceRoots
            .map { root -> root.resolve(relativePath) }
            .firstOrNull { file -> file.exists() }
    }

    private fun detectProjectRoot(): File {
        val cwd = File("").absoluteFile
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parentFile?.resolve("settings.gradle.kts")?.exists() == true -> requireNotNull(cwd.parentFile)
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
