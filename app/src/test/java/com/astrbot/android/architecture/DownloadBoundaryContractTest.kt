package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val manifestFile: Path = projectRoot.resolve("app/src/main/AndroidManifest.xml")
    private val appMainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val downloadApiRoot: Path =
        projectRoot.resolve("download/api/src/main/java/com/astrbot/android/download")
    private val downloadImplRoot: Path =
        projectRoot.resolve("download/impl/src/main/java/com/astrbot/android/download")

    @Test
    fun download_modules_must_be_registered_and_reported() {
        val settingsText = settingsFile.readText(UTF_8)
        val rootBuildText = rootBuildFile.readText(UTF_8)
        val appBuildText = appBuildFile.readText(UTF_8)

        listOf(":download:api", ":download:impl").forEach { module ->
            assertTrue(
                "$module must be registered in settings.gradle.kts for phase 11.",
                settingsText.contains("""include("$module")"""),
            )
            assertTrue(
                "$module must be reported by architecture source roots.",
                rootBuildText.contains(module.removePrefix(":").replace(':', '/') + "/src/main/java"),
            )
            assertTrue(
                "app must depend on $module while app shell still wires download entry points.",
                appBuildText.contains("""implementation(project("$module"))"""),
            )
        }
    }

    @Test
    fun app_shell_must_not_own_download_implementation_files() {
        val oldDownloadFiles = kotlinFilesUnder(appMainRoot.resolve("download"))
            .map { file -> appMainRoot.relativize(file).toString().replace('\\', '/') }

        assertTrue(
            "Download implementation belongs to :download:impl; app shell must not keep app/download files: $oldDownloadFiles",
            oldDownloadFiles.isEmpty(),
        )

        val requiredApiFiles = listOf(
            downloadApiRoot.resolve("DownloadModels.kt"),
            downloadApiRoot.resolve("DownloadManagerPort.kt"),
        )
        val requiredImplFiles = listOf(
            downloadImplRoot.resolve("DefaultDownloadManager.kt"),
            downloadImplRoot.resolve("DownloadForegroundService.kt"),
            downloadImplRoot.resolve("ResumableHttpDownloader.kt"),
        )
        val missing = (requiredApiFiles + requiredImplFiles).filterNot { it.exists() }
        assertTrue(
            "Download api/impl modules must own the phase 11 download files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun production_callers_must_use_download_manager_port_instead_of_static_facade() {
        val callers = listOf(
            appMainRoot.resolve("data/RuntimeAssetRepository.kt"),
            appMainRoot.resolve("feature/plugin/runtime/PluginInstaller.kt"),
        )

        val violations = callers
            .filter { it.exists() }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                listOf(
                    "AppDownloadManager",
                    "AppDownloadManagerBootstrap",
                ).mapNotNull { forbidden ->
                    if (text.contains(forbidden)) {
                        "${appMainRoot.relativize(file).toString().replace('\\', '/')} contains $forbidden"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Runtime asset and plugin download callers must inject DownloadManagerPort instead of AppDownloadManager: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun manifest_must_register_download_impl_service_real_class() {
        val manifest = manifestFile.readText(UTF_8)

        assertTrue(
            "AndroidManifest.xml must register the download impl foreground service by real class name.",
            manifest.contains("""android:name="com.astrbot.android.download.DownloadForegroundService""""),
        )
        assertTrue(
            "AndroidManifest.xml must not keep app-relative .download.DownloadForegroundService.",
            !manifest.contains("""android:name=".download.DownloadForegroundService""""),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
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
