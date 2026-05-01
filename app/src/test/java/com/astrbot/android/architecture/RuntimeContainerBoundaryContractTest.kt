package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeContainerBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val appIntegrationBuildFile: Path = projectRoot.resolve("app-integration/build.gradle.kts")
    private val coarseRuntimeContainerRoot: Path =
        projectRoot.resolve("core/runtime/src/main/java/com/astrbot/android/core/runtime/container")
    private val appContainerRoot: Path =
        projectRoot.resolve("app/src/main/java/com/astrbot/android/core/runtime/container")

    @Test
    fun runtime_container_module_must_be_registered_reported_and_consumed() {
        val settingsText = settingsFile.readText(UTF_8)
        val rootBuildText = rootBuildFile.readText(UTF_8)
        val appBuildText = appBuildFile.readText(UTF_8)
        val appIntegrationBuildText = appIntegrationBuildFile.readText(UTF_8)

        assertTrue(
            ":core:runtime-container must be registered in settings.gradle.kts.",
            settingsText.contains("""include(":core:runtime-container")"""),
        )
        assertTrue(
            "Architecture source roots must include core/runtime-container/src/main/java.",
            rootBuildText.contains("core/runtime-container/src/main/java"),
        )
        assertTrue(
            "App must depend on :core:runtime-container while Android service adapters remain in :app.",
            appBuildText.contains("""implementation(project(":core:runtime-container"))"""),
        )
        assertTrue(
            "app-integration must depend on :core:runtime-container for container runtime port wiring.",
            appIntegrationBuildText.contains("""implementation(project(":core:runtime-container"))"""),
        )
    }

    @Test
    fun runtime_container_contracts_must_live_in_core_runtime_container() {
        val expectedFiles = listOf(
            "CommandRunner.kt",
            "ContainerRuntimeController.kt",
            "ContainerRuntimeInstallerPort.kt",
            "RuntimeBridgeController.kt",
        )

        val missing = expectedFiles
            .map { fileName -> projectRoot.resolve("core/runtime-container/src/main/java/com/astrbot/android/core/runtime/container/$fileName") }
            .filterNot { path -> path.exists() }
            .map { path -> projectRoot.relativize(path).toString().replace('\\', '/') }

        assertEquals(
            "Container runtime cross-layer contracts must be owned by :core:runtime-container.",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun coarse_runtime_must_not_own_container_sources() {
        val residualFiles = kotlinFilesUnder(coarseRuntimeContainerRoot)
            .map(::relativePath)
            .sorted()

        assertEquals(
            "Container runtime sources must live in :core:runtime-container or explicit :app Android adapters.",
            emptyList<String>(),
            residualFiles,
        )
    }

    @Test
    fun app_container_package_must_only_keep_android_service_adapters() {
        val allowedAppAdapterFiles = setOf(
            "app/src/main/java/com/astrbot/android/core/runtime/container/ContainerBridgeService.kt",
        )

        val residualFiles = kotlinFilesUnder(appContainerRoot)
            .map(::relativePath)
            .filterNot(allowedAppAdapterFiles::contains)
            .sorted()

        assertEquals(
            "App may keep only Android service/controller adapters for container runtime; portable owners belong to :core:runtime-container.",
            emptyList<String>(),
            residualFiles,
        )
    }

    @Test
    fun production_hot_paths_must_not_call_static_container_facades() {
        val allowedCompatAdapterFiles = setOf(
            "app/src/main/java/com/astrbot/android/core/runtime/container/ContainerBridgeService.kt",
        )
        val forbiddenTokens = listOf(
            "BridgeCommandRunner.execute(",
            "ContainerBridgeController.start(",
            "ContainerBridgeController.stop(",
            "ContainerBridgeController.check(",
            "NapCatContainerRuntime.startBridge(",
            "NapCatContainerRuntime.stopBridge(",
        )

        val violations = productionKotlinFiles()
            .filterNot { file -> relativePath(file) in allowedCompatAdapterFiles }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenTokens.mapNotNull { token ->
                    if (text.contains(token)) "${relativePath(file)} contains $token" else null
                }
            }

        assertTrue(
            "Production UI/QQ/audio/data paths must use injected container ports instead of static facades. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun bridge_command_execution_must_not_use_shell_c_as_cross_layer_protocol() {
        val violations = productionKotlinFiles()
            .filterNot { file -> relativePath(file).endsWith("ContainerCommandCompat.kt") }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                listOf(
                    "ProcessBuilder(\"/system/bin/sh\", \"-c\"",
                    "ProcessBuilder(\"sh\", \"-c\"",
                    "\"sh -c \$command\"",
                ).mapNotNull { token ->
                    if (text.contains(token)) "${relativePath(file)} contains $token" else null
                }
            }

        assertTrue(
            "Container commands must cross layers as CommandSpec/CommandRunner, not arbitrary sh -c strings. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun container_installer_must_use_injected_secret_store() {
        val installerCandidates = productionKotlinFiles()
            .filter { file -> file.fileName.toString() == "ContainerRuntimeInstaller.kt" }
        val violations = installerCandidates
            .filter { file ->
                val text = file.readText(UTF_8)
                text.contains("RuntimeSecretRepository") || !text.contains("RuntimeSecretStore")
            }
            .map(::relativePath)

        assertTrue(
            "ContainerRuntimeInstaller must inject RuntimeSecretStore instead of reading RuntimeSecretRepository static compat facade. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun container_installer_must_propagate_required_rootfs_extraction_failures() {
        val installerSource = projectRoot
            .resolve("core/runtime-container/src/main/java/com/astrbot/android/core/runtime/container/ContainerRuntimeInstaller.kt")
            .readText(UTF_8)
        val extractionCallIndex = installerSource.indexOf("RootfsExtractor.ensureExtracted(")
        assertTrue(
            "ContainerRuntimeInstaller must call RootfsExtractor.ensureExtracted for required Ubuntu rootfs.",
            extractionCallIndex >= 0,
        )

        val catchIndex = installerSource.indexOf("catch (error: Exception)", extractionCallIndex)
        val defaultsIndex = installerSource.indexOf("bridgeStatePort.applyRuntimeDefaults", extractionCallIndex)
        assertTrue(
            "Rootfs extraction failure handling must remain before runtime defaults are applied.",
            catchIndex > extractionCallIndex && defaultsIndex > catchIndex,
        )

        val failureHandler = installerSource.substring(catchIndex, defaultsIndex)
        assertTrue(
            "Ubuntu rootfs is a required artifact; extraction failure must be logged and rethrown so ensureInstalled can fail/retry.",
            failureHandler.contains("Rootfs extraction failed:") &&
                (
                    failureHandler.contains("throw IllegalStateException(") ||
                        failureHandler.contains("throw error")
                    ),
        )
    }

    private fun productionKotlinFiles(): List<Path> {
        val roots = listOf(
            "app/src/main/java",
            "app-integration/src/main/java",
            "core/runtime/src/main/java",
            "core/runtime-container/src/main/java",
            "feature/qq/impl/src/main/java",
        )
        return roots.map(projectRoot::resolve).flatMap(::kotlinFilesUnder)
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        return projectRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
