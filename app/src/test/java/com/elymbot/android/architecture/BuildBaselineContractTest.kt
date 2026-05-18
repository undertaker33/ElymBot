package com.elymbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildBaselineContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val wrapperProperties: Path =
        projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val moduleBuildRoots = listOf("app-integration", "core", "download", "feature")

    @Test
    fun gradle_wrapper_must_use_stable_9_4_1_distribution() {
        assertTrue(
            "Gradle wrapper properties must exist: $wrapperProperties",
            wrapperProperties.exists(),
        )

        val distributionUrl = wrapperProperties.readLines(UTF_8)
            .firstOrNull { line -> line.startsWith("distributionUrl=") }
            .orEmpty()

        assertTrue(
            "Gradle wrapper must use stable Gradle 9.4.1 bin distribution. Found: $distributionUrl",
            distributionUrl.contains("gradle-9.4.1-bin.zip"),
        )

        val forbiddenQualifiers = listOf("milestone", "rc", "alpha", "beta")
        val matchedQualifier = forbiddenQualifiers.firstOrNull { qualifier ->
            distributionUrl.contains(qualifier, ignoreCase = true)
        }
        assertTrue(
            "Gradle wrapper must not use milestone/rc/alpha/beta distributions. Found: $distributionUrl",
            matchedQualifier == null,
        )
    }

    @Test
    fun gradle_wrapper_distribution_url_validation_must_remain_enabled() {
        val text = wrapperProperties.readText(UTF_8)

        assertTrue(
            "Gradle wrapper must keep validateDistributionUrl=true",
            text.contains("validateDistributionUrl=true"),
        )
        assertTrue(
            "Gradle wrapper networkTimeout must allow stable distribution downloads.",
            Regex("""networkTimeout\s*=\s*(?:[6-9]\d{4}|[1-9]\d{5,})""").containsMatchIn(text),
        )
    }

    @Test
    fun android_gradle_plugin_must_use_9_2_with_agp9_kotlin_tooling() {
        val text = rootBuildFile.readText(UTF_8)

        assertTrue(
            "AGP must use 9.2.0 for the accepted build-chain upgrade.",
            text.contains("""id("com.android.application") version "9.2.0" apply false"""),
        )
        assertTrue(
            "KSP must use a stable line with AGP 9 built-in Kotlin support.",
            text.contains("""id("com.google.devtools.ksp") version "2.3.8" apply false"""),
        )
        assertTrue(
            "Compose modules must use the Kotlin Compose compiler plugin on Kotlin 2.x.",
            text.contains("""id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false"""),
        )
        assertFalse(
            "AGP 9 built-in Kotlin replaces the kotlin-android plugin.",
            text.contains("org.jetbrains.kotlin.android"),
        )
    }

    @Test
    fun android_compile_sdk_must_be_36_1_and_target_sdk_must_be_36() {
        val rootText = rootBuildFile.readText(UTF_8)
        val text = appBuildFile.readText(UTF_8)

        assertTrue(
            "compileSdk major level must stay on API 36.",
            rootText.contains("val ELYMBOT_COMPILE_SDK = 36"),
        )
        assertTrue(
            "compileSdk minor level must target API 36.1 on AGP 9.2.",
            rootText.contains("val ELYMBOT_COMPILE_SDK_MINOR = 1"),
        )
        assertTrue(
            "targetSdk must be 36 once SDK 36 baseline is accepted.",
            Regex("""targetSdk\s*=\s*36""").containsMatchIn(text),
        )
    }

    @Test
    fun android_library_modules_must_use_root_shared_build_baseline() {
        val rootBuildText = rootBuildFile.readText(UTF_8)

        listOf(
            "compileSdk = ELYMBOT_COMPILE_SDK",
            "compileSdkMinor = ELYMBOT_COMPILE_SDK_MINOR",
            "minSdk = ELYMBOT_MIN_SDK",
            "JavaVersion.VERSION_17",
        ).forEach { requiredToken ->
            assertTrue(
                "Root build file must own shared Android baseline token: $requiredToken",
                rootBuildText.contains(requiredToken),
            )
        }

        val duplicatedModuleConfig = moduleBuildFiles()
            .filter { path -> path.readText(UTF_8).contains("id(\"com.android.library\")") }
            .filter { path ->
                val text = path.readText(UTF_8)
                listOf(
                    Regex("""compileSdk\s*="""),
                    Regex("""compileSdkMinor\s*="""),
                    Regex("""minSdk\s*="""),
                    Regex("""sourceCompatibility\s*="""),
                    Regex("""targetCompatibility\s*="""),
                    Regex("""kotlinOptions\s*\{"""),
                ).any { regex -> regex.containsMatchIn(text) }
            }
            .map { path -> projectRoot.relativize(path).toString().replace('\\', '/') }

        assertTrue(
            "Android library modules must not duplicate shared SDK/JVM baseline. Found: $duplicatedModuleConfig",
            duplicatedModuleConfig.isEmpty(),
        )

        val kotlinAndroidPluginModules = moduleBuildFiles()
            .filter { path -> path.readText(UTF_8).contains("org.jetbrains.kotlin.android") }
            .map { path -> projectRoot.relativize(path).toString().replace('\\', '/') }

        assertTrue(
            "Android modules must use AGP 9 built-in Kotlin instead of kotlin-android. Found: $kotlinAndroidPluginModules",
            kotlinAndroidPluginModules.isEmpty(),
        )
    }

    @Test
    fun architecture_check_must_report_all_main_source_roots() {
        val rootBuildText = rootBuildFile.readText(UTF_8)

        assertTrue(
            "architectureCheck must depend on the repo-wide source root report task.",
            rootBuildText.contains("""dependsOn("architectureSourceRootsReport")"""),
        )
        assertTrue(
            "architectureCheck must depend on the repo-wide debt report task.",
            rootBuildText.contains("""dependsOn("architectureDebtReport")"""),
        )
        assertTrue(
            "architectureDebugUnitTest must generate architecture reports before source-level contracts run.",
            rootBuildText.contains("""dependsOn(":architectureSourceRootsReport", ":architectureDebtReport")"""),
        )
        assertTrue(
            "Architecture report tasks must stay fresh during architectureCheck.",
            rootBuildText.contains("""outputs.upToDateWhen { false }"""),
        )
        assertTrue(
            "architecture source root report must be written under build/reports/architecture.",
            rootBuildText.contains("build/reports/architecture/source-roots.txt"),
        )
        assertTrue(
            "architecture debt report must be written under build/reports/architecture.",
            rootBuildText.contains("build/reports/architecture/debt.txt"),
        )

        val expectedRoots = listOf(
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
            "download/api/src/main/java",
            "download/impl/src/main/java",
            "feature/bot/api/src/main/java",
            "feature/bot/data/src/main/java",
            "feature/bot/impl/src/main/java",
            "feature/chat/api/src/main/java",
            "feature/chat/impl/src/main/java",
            "feature/chat/presentation/src/main/java",
            "feature/chat/runtime/src/main/java",
            "feature/config/api/src/main/java",
            "feature/config/data/src/main/java",
            "feature/config/impl/src/main/java",
            "feature/conversation/api/src/main/java",
            "feature/conversation/data/src/main/java",
            "feature/cron/api/src/main/java",
            "feature/cron/impl/src/main/java",
            "feature/persona/api/src/main/java",
            "feature/persona/data/src/main/java",
            "feature/persona/impl/src/main/java",
            "feature/plugin/api/src/main/java",
            "feature/plugin/data/src/main/java",
            "feature/plugin/presentation/src/main/java",
            "feature/plugin/runtime/src/main/java",
            "feature/provider/api/src/main/java",
            "feature/provider/data/src/main/java",
            "feature/provider/impl/src/main/java",
            "feature/qq/api/src/main/java",
            "feature/qq/data/src/main/java",
            "feature/qq/presentation/src/main/java",
            "feature/qq/runtime/src/main/java",
            "feature/resource/api/src/main/java",
            "feature/resource/data/src/main/java",
            "feature/resource/impl/src/main/java",
        )

        expectedRoots.forEach { sourceRoot ->
            assertTrue(
                "architecture source root report must include $sourceRoot",
                rootBuildText.contains(sourceRoot),
            )
        }
    }

    @Test
    fun feature_api_modules_must_have_matching_impl_modules() {
        val settingsText = projectRoot.resolve("settings.gradle.kts").readText(UTF_8)
        val featureNames = listOf(
            "bot",
            "config",
            "cron",
            "persona",
            "plugin",
            "provider",
            "qq",
            "resource",
        )

        featureNames.forEach { featureName ->
            assertTrue(
                "Feature module must declare :feature:$featureName:api",
                settingsText.contains("""include(":feature:$featureName:api")"""),
            )
            assertTrue(
                "Feature module must declare :feature:$featureName:impl as the Gradle hard-boundary target.",
                settingsText.contains("""include(":feature:$featureName:impl")"""),
            )
            assertTrue(
                "Feature impl build file must exist for :feature:$featureName:impl",
                projectRoot.resolve("feature/$featureName/impl/build.gradle.kts").exists(),
            )
        }
    }

    @Test
    fun app_integration_module_must_exist_as_wiring_boundary() {
        val settingsText = projectRoot.resolve("settings.gradle.kts").readText(UTF_8)
        val appBuildText = appBuildFile.readText(UTF_8)

        assertTrue(
            "settings.gradle.kts must declare :app-integration as the Gradle wiring boundary.",
            settingsText.contains("""include(":app-integration")"""),
        )
        assertTrue(
            "app module must depend on :app-integration for cross-feature wiring.",
            appBuildText.contains("""implementation(project(":app-integration"))"""),
        )
        assertTrue(
            "app-integration/build.gradle.kts must exist.",
            projectRoot.resolve("app-integration/build.gradle.kts").exists(),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/build.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("app/build.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private fun moduleBuildFiles(): List<Path> {
        return moduleBuildRoots.flatMap { root ->
            val moduleRoot = projectRoot.resolve(root)
            if (!moduleRoot.exists()) {
                emptyList()
            } else {
                java.nio.file.Files.walk(moduleRoot).use { stream ->
                    stream
                        .filter { path -> path.isRegularFile() && path.fileName.toString() == "build.gradle.kts" }
                        .toList()
                }
            }
        }
    }
}
