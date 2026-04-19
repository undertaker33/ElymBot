package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPortSourceContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun domain_ports_exist() {
        val required = listOf(
            "feature/provider/domain/ProviderRepositoryPort.kt",
            "feature/config/domain/ConfigRepositoryPort.kt",
            "feature/bot/domain/BotRepositoryPort.kt",
            "feature/persona/domain/PersonaRepositoryPort.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing domain port files: $missing", missing.isEmpty())
    }

    @Test
    fun legacy_adapters_exist() {
        val required = listOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing legacy adapter files: $missing", missing.isEmpty())
    }

    @Test
    fun domain_ports_do_not_import_legacy_repositories() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val forbiddenImports = listOf(
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.PersonaRepository",
        )

        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        }

        assertTrue(
            "Domain ports must not import legacy repository singletons: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun domain_ports_do_not_expose_mutable_flows() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("MutableStateFlow") || text.contains("MutableSharedFlow")) {
                    listOf("$relative exposes mutable flow")
                } else {
                    emptyList()
                }
            }
        }

        assertTrue(
            "Domain ports must not expose mutable flows: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun new_feature_domain_files_do_not_import_android_or_legacy_packages() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val forbiddenPrefixes = listOf(
            "import android.",
            "import androidx.compose.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )

        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenPrefixes.mapNotNull { prefix ->
                    if (text.contains(prefix)) "$relative imports $prefix" else null
                }
            }
        }

        assertTrue(
            "Domain files must not import Android framework, Compose, or legacy packages: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun legacy_adapters_do_not_add_runBlocking() {
        val adapterFiles = listOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
        )

        val violations = adapterFiles.mapNotNull { path ->
            val file = mainRoot.resolve(path)
            if (!file.exists()) return@mapNotNull null
            val text = file.readText()
            if (text.contains("runBlocking")) "$path uses runBlocking" else null
        }

        assertTrue(
            "Legacy adapters must not add runBlocking: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun initialization_coordinator_exists() {
        val required = listOf(
            "core/di/AppInitializer.kt",
            "core/di/InitializationCoordinator.kt",
            "feature/provider/data/ProviderRepositoryInitializer.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing initialization coordinator files: $missing", missing.isEmpty())
    }

    @Test
    fun app_container_bootstrap_uses_coordinator_for_configuration_domains() {
        val source = appBootstrapperSource()
        val bootstrapBody = functionBody(source, "bootstrap")

        assertTrue(
            "AppBootstrapper.bootstrap must delegate configuration-domain startup to InitializationCoordinator",
            bootstrapBody.contains("InitializationCoordinator("),
        )
        val requiredInitializers = listOf(
            "ProviderRepositoryInitializer()",
            "ConfigRepositoryInitializer()",
            "PersonaRepositoryInitializer()",
            "BotRepositoryInitializer()",
        )
        val missingInitializers = requiredInitializers.filterNot(bootstrapBody::contains)
        assertTrue(
            "Bootstrap coordinator wiring is missing initializers: $missingInitializers",
            missingInitializers.isEmpty(),
        )
        assertTrue(
            "Bootstrap must execute the configuration-domain coordinator",
            bootstrapBody.contains(".initializeAll(application)"),
        )

        val forbiddenDirectCalls = listOf(
            "ProviderRepository.initialize(application)",
            "ConfigRepository.initialize(application)",
            "PersonaRepository.initialize(application)",
            "BotRepository.initialize(application)",
        )
        val remainingDirectCalls = forbiddenDirectCalls.filter(bootstrapBody::contains)
        assertTrue(
            "Bootstrap must not directly initialize repositories now owned by InitializationCoordinator: $remainingDirectCalls",
            remainingDirectCalls.isEmpty(),
        )
    }

    @Test
    fun app_bootstrapper_avoids_sync_tts_and_duplicate_config_initialization() {
        val bootstrapBody = functionBody(appBootstrapperSource(), "bootstrap")

        assertTrue(
            "AppBootstrapper.bootstrap must not synchronously initialize TTS voice assets on the main startup path",
            !bootstrapBody.contains("TtsVoiceAssetRepository.initialize(application)"),
        )
        assertEquals(
            "AppBootstrapper.bootstrap must wire ConfigRepositoryInitializer exactly once during phase-2 startup",
            1,
            "ConfigRepositoryInitializer()".toRegex(RegexOption.LITERAL).findAll(bootstrapBody).count(),
        )
    }

    @Test
    fun app_backup_wiring_uses_shared_production_port_with_durable_conversation_restore() {
        val source = appBootstrapperSource()
        val bootstrapBody = functionBody(source, "bootstrap")
        val productionPortBody = functionBody(source, "createProductionAppBackupDataPort")

        assertTrue(
            "AppBootstrapper must install the shared production AppBackupDataPort factory instead of duplicating inline wiring",
            bootstrapBody.contains("createProductionAppBackupDataPort()"),
        )
        assertTrue(
            "Production AppBackupDataPort must route conversation restore through restoreSessionsDurable()",
            productionPortBody.contains("ConversationRepository.restoreSessionsDurable(sessions)"),
        )
    }

    @Test
    fun startup_blocking_phase2_repositories_do_not_use_runblocking_dispatchers_io() {
        val targetFiles = listOf(
            "feature/bot/data/FeatureBotRepository.kt",
            "feature/config/data/FeatureConfigRepository.kt",
            "core/runtime/audio/TtsVoiceAssetRepository.kt",
        )
        val offenders = targetFiles.mapNotNull { relativePath ->
            val source = mainRoot.resolve(relativePath).readText()
            if (source.contains("runBlocking(Dispatchers.IO)")) relativePath else null
        }

        assertTrue(
            "Phase-2 repositories must remove runBlocking(Dispatchers.IO) from initialization and persistence paths: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun repository_selection_mutators_do_not_eagerly_assign_selected_state() {
        val botSelectBody = functionBody(
            mainRoot.resolve("feature/bot/data/FeatureBotRepository.kt").readText(),
            "select",
        )
        val configSelectBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "select",
        )
        val configSaveBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "save",
        )
        val configDeleteBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "delete",
        )
        val configRestoreBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "restoreProfiles",
        )

        assertTrue(
            "FeatureBotRepository.select must not directly assign selectedBotId before persistence flow catches up",
            !containsDirectAssignment(botSelectBody, "_selectedBotId.value"),
        )
        assertTrue(
            "FeatureBotRepository.select must not directly assign botProfile before persistence flow catches up",
            !containsDirectAssignment(botSelectBody, "_botProfile.value"),
        )
        assertTrue(
            "FeatureConfigRepository.select must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configSelectBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.save must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configSaveBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.save must not directly assign profiles before persistence flow catches up",
            !containsDirectAssignment(configSaveBody, "_profiles.value"),
        )
        assertTrue(
            "FeatureConfigRepository.delete must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configDeleteBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.delete must not directly assign profiles before persistence flow catches up",
            !containsDirectAssignment(configDeleteBody, "_profiles.value"),
        )
        assertTrue(
            "FeatureConfigRepository.restoreProfiles must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configRestoreBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.restoreProfiles must not directly assign profiles before persistence flow catches up",
            !containsDirectAssignment(configRestoreBody, "_profiles.value"),
        )
    }

    @Test
    fun initialization_coordinator_wiring_does_not_include_out_of_scope_initializers() {
        val source = appBootstrapperSource()
        val bootstrapBody = functionBody(source, "bootstrap")

        val forbiddenInitializerTokens = listOf(
            "ConversationRepositoryInitializer",
            "RuntimeInitializer",
            "PlatformBridgeInitializer",
            "QQRepositoryInitializer",
            "QqRepositoryInitializer",
            "OneBotInitializer",
            "PluginRepositoryInitializer",
            "ToolSourceInitializer",
        )
        val violations = forbiddenInitializerTokens.filter(bootstrapBody::contains)
        assertTrue(
            "Fourth-phase coordinator must only wire Provider/Config/Persona/Bot initializers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_repository_imports_are_limited_to_legacy_adapters_and_initializers() {
        val featureDirs = listOf(
            "feature/provider",
            "feature/config",
            "feature/bot",
            "feature/persona",
        )
        val allowed = setOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/provider/data/ProviderRepositoryInitializer.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
        )
        val forbiddenRepositoryImports = listOf(
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.PersonaRepository",
        )

        val violations = featureDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (relative in allowed) {
                    emptyList()
                } else {
                    forbiddenRepositoryImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }
                }
            }
        }

        assertTrue(
            "Only legacy adapters and initializers may import legacy repository singletons: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun view_model_dependency_delete_shells_delegate_to_phase3_transaction_service() {
        val source = mainRoot.resolve("di/AstrBotViewModelDependencies.kt").readText()
        val botDeleteBody = functionBody(objectBody(source, "HiltBotViewModelDependencies"), "delete")
        val configDeleteBody = functionBody(objectBody(source, "HiltConfigViewModelDependencies"), "deleteConfigProfile")
        val bootstrapBody = functionBody(appBootstrapperSource(), "bootstrap")

        assertTrue(
            "HiltBotViewModelDependencies.delete must delegate to a single phase-3 transaction service",
            botDeleteBody.contains("Phase3DataTransactionServiceRegistry.service.deleteBotProfile(botId)"),
        )
        assertTrue(
            "HiltBotViewModelDependencies must not keep hand-rolled conversation cleanup sequencing",
            !botDeleteBody.contains("ConversationRepository.deleteSessionsForBot(") &&
                !botDeleteBody.contains("ConversationRepository.restoreSessions(") &&
                !botDeleteBody.contains("BotRepository.delete(botId)"),
        )

        assertTrue(
            "HiltConfigViewModelDependencies.deleteConfigProfile must delegate to a single phase-3 transaction service",
            configDeleteBody.contains("Phase3DataTransactionServiceRegistry.service.deleteConfigProfile(profileId)"),
        )
        assertTrue(
            "HiltConfigViewModelDependencies must not keep hand-rolled config delete / bot rebind sequencing",
            !configDeleteBody.contains("BotRepository.replaceConfigBinding(") &&
                !configDeleteBody.contains("ConfigRepository.delete(profileId)"),
        )
        assertTrue(
            "AppBootstrapper must install the production phase-3 transaction service for delete shells",
            bootstrapBody.contains("installProductionPhase3DataTransactionService("),
        )
    }

    @Test
    fun phase3_delete_transaction_service_is_suspend_and_does_not_use_runblocking() {
        val source = mainRoot.resolve("di/AstrBotViewModelDependencies.kt").readText()
        val serviceBody = interfaceBody(source, "Phase3DataTransactionService")

        assertTrue(
            "Phase3DataTransactionService.deleteConfigProfile must be suspend",
            serviceBody.contains("suspend fun deleteConfigProfile(profileId: String)"),
        )
        assertTrue(
            "Phase3DataTransactionService.deleteBotProfile must be suspend",
            serviceBody.contains("suspend fun deleteBotProfile(botId: String)"),
        )
        assertTrue(
            "Phase3 delete transaction service must not use runBlocking in the service file",
            !source.contains("runBlocking"),
        )
    }

    @Test
    fun persona_port_exposes_designed_tool_enablement_and_explicit_enabled_semantics() {
        val port = mainRoot.resolve("feature/persona/domain/PersonaRepositoryPort.kt").readText()
        val adapter = mainRoot.resolve("feature/persona/data/LegacyPersonaRepositoryAdapter.kt").readText()

        assertTrue(
            "PersonaRepositoryPort must expose all persona tool enablement snapshots",
            port.contains("fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>"),
        )
        assertTrue(
            "PersonaRepositoryPort must preserve lookup by persona id for existing callers",
            port.contains("fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot?"),
        )
        assertTrue(
            "PersonaRepositoryPort must expose explicit enabled state updates",
            port.contains("suspend fun toggleEnabled(id: String, enabled: Boolean)"),
        )
        assertTrue(
            "PersonaRepositoryPort must preserve existing toggle semantics for existing callers",
            port.contains("suspend fun toggleEnabled(id: String)"),
        )
        assertTrue(
            "LegacyPersonaRepositoryAdapter must implement the all-snapshot port API",
            adapter.contains("override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>"),
        )
        assertTrue(
            "LegacyPersonaRepositoryAdapter must implement explicit enabled updates",
            adapter.contains("override suspend fun toggleEnabled(id: String, enabled: Boolean)"),
        )
    }

    private fun kotlinFilesUnder(relative: String): List<Path> {
        val root = mainRoot.resolve(relative)
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun functionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName(")
        assertNotEquals("Missing function: $functionName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for function: $functionName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun appBootstrapperSource(): String {
        val file = mainRoot.resolve("di/AppBootstrapper.kt")
        assertTrue("AppBootstrapper.kt must exist", file.exists())
        return file.readText()
    }

    private fun objectBody(source: String, objectName: String): String {
        val signatureIndex = source.indexOf("object $objectName")
        assertNotEquals("Missing object: $objectName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for object: $objectName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun interfaceBody(source: String, interfaceName: String): String {
        val signatureIndex = source.indexOf("interface $interfaceName")
        assertNotEquals("Missing interface: $interfaceName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for interface: $interfaceName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun containsDirectAssignment(source: String, lhs: String): Boolean {
        val pattern = Regex("""${Regex.escape(lhs)}\s*=\s*[^=]""")
        return pattern.containsMatchIn(source)
    }

    private fun matchingBraceIndex(source: String, openIndex: Int): Int {
        return matchingIndex(source, openIndex, '{', '}')
    }

    private fun matchingIndex(source: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                open -> depth += 1
                close -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        throw AssertionError("No matching '$close' found")
    }
}
