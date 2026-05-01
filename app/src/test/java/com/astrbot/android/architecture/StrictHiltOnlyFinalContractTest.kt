package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictHiltOnlyFinalContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val productionSourceRoots: List<Path> = architectureMainSourceRoots()

    @Test
    fun startup_and_runtime_hotspots_must_not_retain_out_of_graph_bypass_tokens() {
        val forbiddenTokensByFile = mapOf(
            "di/startup/RepositoryInitializationStartupChain.kt" to listOf(
                "InitializationCoordinator(",
                "AppInitializer",
                "RepositoryInitializer",
                "ConfigRepositoryInitializer",
                "BotRepositoryInitializer",
                "PersonaRepositoryInitializer",
                ".initialize(application)",
                "providerRepositoryWarmup.warmUp()",
            ),
            "feature/provider/data/ProviderRepositoryWarmup.kt" to listOf(
                "@ApplicationContext",
                "FeatureProviderRepository.initialize(",
            ),
            "../app-integration/src/main/java/com/astrbot/android/app/integration/db/DatabaseModule.kt" to listOf("AstrBotDatabase.get("),
            "core/runtime/container/ContainerRuntimeInstaller.kt" to listOf(
                "EntryPointAccessors.fromApplication(",
                "EntryPoints.get(",
            ),
            "feature/qq/runtime/QqOneBotBridgeServer.kt" to listOf(
                "installRuntimeDependencies(",
                "installRuntimeDependencies =",
                "RuntimeDependenciesTestInstaller",
                "updateRuntimeDependenciesForTests(",
            ),
            "data/http/AstrBotHttpClient.kt" to listOf(
                "SharedRuntimeNetworkTransport.get(",
                "RuntimeNetworkTransportRegistry",
            ),
            "feature/plugin/runtime/toolsource/WebSearchToolSourceProvider.kt" to listOf(
                "SharedRuntimeNetworkTransport.get(",
                "RuntimeNetworkTransportRegistry",
                "resolveProductionRuntimeNetworkTransport()",
            ),
            "di/hilt/RuntimeNetworkModule.kt" to listOf(
                "SharedRuntimeNetworkTransport.get(",
                "RuntimeNetworkTransportRegistry",
                "installFromHilt(",
            ),
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt" to listOf(
                "QqPluginDispatchService(",
                "QqMessageRuntimeService(",
                "QqBotCommandRuntimeService(",
                "QqStreamingReplyService(",
            ),
        )

        val violations = buildList {
            forbiddenTokensByFile.forEach { (relativePath, forbiddenTokens) ->
                val file = if (relativePath.startsWith("../")) {
                    projectRoot.resolve(relativePath.removePrefix("../"))
                } else {
                    resolveProductionHotspot(relativePath)
                }
                assertTrue("Expected strict-Hilt hotspot to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Strict Hilt-only final hotspots must not retain manual bypass tokens: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun repository_initialization_startup_chain_run_must_not_restore_legacy_initializer_subgraph() {
        val file = resolveProductionHotspot("di/startup/RepositoryInitializationStartupChain.kt")
        assertTrue("Expected repository startup chain to exist: ${file.toAbsolutePath()}", file.exists())
        val runBody = functionBody(file.readText(), "run")
        val forbiddenTokens = listOf(
            "InitializationCoordinator(",
            "AppInitializer",
            "RepositoryInitializer",
            "ConfigRepositoryInitializer",
            "BotRepositoryInitializer",
            "PersonaRepositoryInitializer",
            ".initialize(application)",
            "providerRepositoryWarmup.warmUp()",
        )
        val violations = forbiddenTokens.filter(runBody::contains)

        assertTrue(
            "RepositoryInitializationStartupChain.run must not restore legacy initializer or manual startup subgraph residue: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun legacy_initializer_files_must_stay_deleted_from_production() {
        val deletedInitializerFiles = listOf(
            "core/di/AppInitializer.kt",
            "core/di/InitializationCoordinator.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
        )
        val present = deletedInitializerFiles.filter { relativePath ->
            productionSourceRoots.any { root -> root.resolve(relativePath).exists() }
        }

        assertTrue(
            "Hilt-only startup must not restore legacy initializer production files: $present",
            present.isEmpty(),
        )
    }

    @Test
    fun closed_mainlines_must_not_reference_legacy_adapters_or_transition_helpers() {
        val hotspotFiles = listOf(
            "feature/chat/presentation/ChatViewModel.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
            "feature/plugin/presentation/PluginViewModel.kt",
            "feature/plugin/runtime/PluginRuntimeFacade.kt",
            "feature/qq/runtime/QqOneBotBridgeServer.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/qq/runtime/QqPluginDispatchService.kt",
        )
        val forbiddenTokens = listOf(
            "LegacyBotRepositoryAdapter",
            "LegacyConfigRepositoryAdapter",
            "LegacyPersonaRepositoryAdapter",
            "LegacyProviderRepositoryAdapter",
            "LegacyConversationRepositoryAdapter",
            "LegacyQqConversationAdapter",
            "LegacyQqPlatformConfigAdapter",
            "LegacyCronJobRepositoryAdapter",
            "LegacyCronSchedulerAdapter",
            "LegacyResourceCenterRepositoryAdapter",
            "LegacyChatCompletionServiceAdapter",
            "LegacyLlmProviderProbeAdapter",
            "LegacyRuntimeOrchestratorAdapter",
            "PluginRuntimeDependencyBridge.",
            "createCompatPluginHostCapabilityGatewayFactory(",
            "createCompatPluginHostCapabilityGateway(",
        )

        val violations = buildList {
            hotspotFiles.forEach { relativePath ->
                val file = mainRoot.resolve(relativePath)
                assertTrue("Expected closed mainline to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Closed mainlines must remain free of legacy adapters and transition helpers: $violations",
            violations.isEmpty(),
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

    private fun architectureMainSourceRoots(): List<Path> {
        val relativeRoots = listOf(
            "app/src/main/java",
            "app-integration/src/main/java",
            "core/common/src/main/java",
            "core/db/src/main/java",
            "core/logging/src/main/java",
            "core/network/src/main/java",
            "core/runtime/src/main/java",
            "core/runtime-audio/src/main/java",
            "core/runtime-cache/src/main/java",
            "core/runtime-container/src/main/java",
            "core/runtime-context/src/main/java",
            "core/runtime-llm/src/main/java",
            "core/runtime-search/src/main/java",
            "core/runtime-secret/src/main/java",
            "core/runtime-session/src/main/java",
            "core/runtime-tool/src/main/java",
            "feature/bot/api/src/main/java",
            "feature/bot/impl/src/main/java",
            "feature/chat/api/src/main/java",
            "feature/chat/impl/src/main/java",
            "feature/config/api/src/main/java",
            "feature/config/impl/src/main/java",
            "feature/cron/api/src/main/java",
            "feature/cron/impl/src/main/java",
            "feature/persona/api/src/main/java",
            "feature/persona/impl/src/main/java",
            "feature/plugin/api/src/main/java",
            "feature/plugin/impl/src/main/java",
            "feature/provider/api/src/main/java",
            "feature/provider/impl/src/main/java",
            "feature/qq/api/src/main/java",
            "feature/qq/impl/src/main/java",
            "feature/resource/api/src/main/java",
            "feature/resource/impl/src/main/java",
        )
        return relativeRoots
            .map { relativeRoot -> projectRoot.resolve(relativeRoot).resolve("com/astrbot/android") }
            .filter { root -> root.exists() }
    }

    private fun resolveProductionHotspot(relativePath: String): Path {
        return productionSourceRoots
            .map { root -> root.resolve(relativePath) }
            .firstOrNull { file -> file.exists() }
            ?: mainRoot.resolve(relativePath)
    }

    private fun functionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName(")
        require(signatureIndex >= 0) { "Missing function: $functionName" }
        val bodyStart = source.indexOf('{', signatureIndex)
        require(bodyStart >= 0) { "Missing body for function: $functionName" }
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun matchingBraceIndex(source: String, openIndex: Int): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        error("No matching closing brace found")
    }
}
