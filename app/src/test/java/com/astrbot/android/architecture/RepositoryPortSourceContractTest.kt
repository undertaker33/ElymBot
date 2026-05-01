package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPortSourceContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    private val productionSourceRoots: List<Path> = listOf(
        "app/src/main/java/com/astrbot/android",
        "app-integration/src/main/java/com/astrbot/android",
        "feature/bot/impl/src/main/java/com/astrbot/android",
        "feature/chat/api/src/main/java/com/astrbot/android",
        "feature/chat/impl/src/main/java/com/astrbot/android",
        "feature/config/impl/src/main/java/com/astrbot/android",
        "feature/cron/impl/src/main/java/com/astrbot/android",
        "feature/persona/impl/src/main/java/com/astrbot/android",
        "feature/plugin/impl/src/main/java/com/astrbot/android",
        "feature/provider/impl/src/main/java/com/astrbot/android",
        "feature/qq/impl/src/main/java/com/astrbot/android",
        "feature/resource/impl/src/main/java/com/astrbot/android",
    ).map(projectRoot::resolve).filter { root -> root.exists() }

    @Test
    fun repository_port_module_binds_only_semantic_feature_port_adapters() {
        val source = listOf(
            mainRoot.resolve("di/hilt/RepositoryPortModule.kt"),
            mainRoot.resolve("di/hilt/QqRepositoryPortModule.kt"),
            projectRoot.resolve("app-integration/src/main/java"),
        ).joinToString(separator = "\n") { sourcePath ->
            if (sourcePath.isRegularFile()) {
                sourcePath.readText()
            } else if (sourcePath.exists()) {
                Files.walk(sourcePath).use { stream ->
                    stream
                        .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                        .map { path -> path.readText() }
                        .toList()
                        .joinToString(separator = "\n")
                }
            } else {
                ""
            }
        }

        val requiredTokens = listOf(
            "FeatureBotRepositoryPortAdapter",
            "FeatureConfigRepositoryPortAdapter",
            "FeatureConversationRepositoryPortAdapter",
            "FeatureCronJobRepositoryPortAdapter",
            "FeaturePersonaRepositoryPortAdapter",
            "FeatureProviderRepositoryPortAdapter",
            "FeatureQqConversationPortAdapter",
            "FeatureQqPlatformConfigPortAdapter",
        )
        val forbiddenTokens = listOf(
            "LegacyBotRepositoryAdapter",
            "LegacyConfigRepositoryAdapter",
            "LegacyConversationRepositoryAdapter",
            "LegacyPersonaRepositoryAdapter",
            "LegacyProviderRepositoryAdapter",
            "LegacyQqConversationAdapter",
            "LegacyQqPlatformConfigAdapter",
        )

        assertTrue(
            "RepositoryPortModule must bind semantic feature port adapters",
            requiredTokens.all(source::contains),
        )
        assertTrue(
            "RepositoryPortModule must not bind legacy adapter names",
            forbiddenTokens.none(source::contains),
        )
    }

    @Test
    fun semantic_adapter_files_exist_and_legacy_adapter_files_are_removed() {
        val semanticFiles = listOf(
            "feature/bot/data/FeatureBotRepositoryPortAdapter.kt",
            "feature/chat/data/FeatureConversationRepositoryPortAdapter.kt",
            "feature/config/data/FeatureConfigRepositoryPortAdapter.kt",
            "feature/cron/data/FeatureCronJobRepositoryPortAdapter.kt",
            "feature/persona/data/FeaturePersonaRepositoryPortAdapter.kt",
            "feature/provider/data/FeatureProviderRepositoryPortAdapter.kt",
            "feature/qq/data/FeatureQqConversationPortAdapter.kt",
            "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
            "feature/resource/data/FeatureResourceCenterPortAdapter.kt",
        )
        val legacyFiles = listOf(
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/chat/data/LegacyConversationRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/cron/data/LegacyCronJobRepositoryAdapter.kt",
            "feature/cron/data/LegacyCronSchedulerAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/qq/data/LegacyQqConversationAdapter.kt",
            "feature/qq/data/LegacyQqPlatformConfigAdapter.kt",
            "feature/resource/data/LegacyResourceCenterRepositoryAdapter.kt",
        )

        val missingSemantic = semanticFiles.filterNot(::productionFileExists)
        val remainingLegacy = legacyFiles.filter(::productionFileExists)

        assertTrue("Missing semantic adapter files: $missingSemantic", missingSemantic.isEmpty())
        assertTrue("Legacy adapter files must be removed from production: $remainingLegacy", remainingLegacy.isEmpty())
    }

    @Test
    fun feature_repository_singleton_usage_is_limited_to_semantic_adapters() {
        val tokenToAllowedPaths = mapOf(
            "FeatureBotRepository." to setOf(
                "feature/bot/data/FeatureBotRepository.kt",
                "feature/bot/data/FeatureBotRepositoryPortAdapter.kt",
                "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
            ),
            "FeatureConversationRepository." to setOf(
                "feature/bot/data/FeatureBotRepository.kt",
                "feature/chat/data/FeatureConversationRepository.kt",
                "feature/chat/data/FeatureConversationRepositoryPortAdapter.kt",
                "feature/qq/data/FeatureQqConversationPortAdapter.kt",
            ),
            "FeatureConfigRepository." to setOf(
                "feature/bot/data/FeatureBotRepository.kt",
                "feature/config/data/FeatureConfigRepository.kt",
                "feature/config/data/FeatureConfigRepositoryPortAdapter.kt",
                "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
            ),
            "FeatureCronJobRepository." to setOf(
                "feature/cron/data/FeatureCronJobRepository.kt",
                "feature/cron/data/FeatureCronJobRepositoryPortAdapter.kt",
            ),
            "FeaturePersonaRepository." to setOf(
                "feature/persona/data/FeaturePersonaRepository.kt",
                "feature/persona/data/FeaturePersonaRepositoryPortAdapter.kt",
            ),
            "FeatureProviderRepository." to setOf(
                "feature/provider/data/FeatureProviderRepository.kt",
                "feature/provider/data/ProviderRepositoryWarmup.kt",
                "feature/provider/data/FeatureProviderRepositoryPortAdapter.kt",
            ),
            "FeatureResourceCenterRepository." to setOf(
                "feature/resource/data/FeatureResourceCenterRepository.kt",
                "feature/resource/data/FeatureResourceCenterPortAdapter.kt",
            ),
        )

        val violations = buildList {
            tokenToAllowedPaths.forEach { (token, allowedPaths) ->
                kotlinFilesUnder("feature").forEach { file ->
                    val relative = relativeProductionPath(file)
                    if (file.readText().contains(token) && relative !in allowedPaths) {
                        add("$relative contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Feature singleton repository usage must stay confined to semantic adapters: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun qq_semantic_adapters_must_depend_on_semantic_ports_not_repository_store_types() {
        val qqAdapterFiles = listOf(
            "feature/qq/data/FeatureQqConversationPortAdapter.kt",
            "feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt",
        )
        val forbiddenStoreTypes = listOf(
            "FeatureConversationRepositoryStore",
            "FeatureBotRepositoryStore",
            "FeatureConfigRepositoryStore",
        )

        val violations = qqAdapterFiles.flatMap { relativePath ->
            val source = mainRoot.resolve(relativePath).readText()
            forbiddenStoreTypes.filter(source::contains).map { token ->
                "$relativePath contains $token"
            }
        }

        assertTrue(
            "QQ semantic adapters must wire through semantic ports instead of repository store types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_services_module_must_not_bind_legacy_llm_adapters() {
        val source = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        val forbiddenTokens = listOf(
            "LegacyChatCompletionServiceAdapter",
            "LegacyLlmProviderProbeAdapter",
            "LegacyRuntimeOrchestratorAdapter",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "RuntimeServicesModule must not bind legacy llm adapters: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        return productionSourceRoots.flatMap { sourceRoot ->
            val root = sourceRoot.resolve(relativeRoot)
            if (!root.exists()) {
                emptyList()
            } else {
                Files.walk(root).use { stream ->
                    stream
                        .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                        .toList()
                }
            }
        }
    }

    private fun productionFileExists(relativePath: String): Boolean {
        return productionSourceRoots.any { sourceRoot -> sourceRoot.resolve(relativePath).exists() }
    }

    private fun relativeProductionPath(file: Path): String {
        val sourceRoot = productionSourceRoots.firstOrNull { root -> file.startsWith(root) }
            ?: error("File $file is not under configured production source roots")
        return sourceRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.resolve("src/main/java/com/astrbot/android").exists() &&
                cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
