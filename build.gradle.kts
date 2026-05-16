import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ELYMBOT_COMPILE_SDK = 36
val ELYMBOT_MIN_SDK = 29
val ELYMBOT_JVM_TARGET = "17"

val architectureMainSourceRoots = listOf(
    "app/src/main/java",
    "app-integration/src/main/java",
    "core/backup/src/main/java",
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
    "core/ui/src/main/java",
    "download/api/src/main/java",
    "download/impl/src/main/java",
    "feature/bot/api/src/main/java",
    "feature/bot/data/src/main/java",
    "feature/bot/impl/src/main/java",
    "feature/bot/presentation/src/main/java",
    "feature/chat/api/src/main/java",
    "feature/chat/impl/src/main/java",
    "feature/chat/presentation/src/main/java",
    "feature/chat/runtime/src/main/java",
    "feature/config/api/src/main/java",
    "feature/config/data/src/main/java",
    "feature/config/impl/src/main/java",
    "feature/config/presentation/src/main/java",
    "feature/conversation/api/src/main/java",
    "feature/conversation/data/src/main/java",
    "feature/cron/api/src/main/java",
    "feature/cron/data/src/main/java",
    "feature/cron/impl/src/main/java",
    "feature/cron/presentation/src/main/java",
    "feature/cron/runtime/src/main/java",
    "feature/persona/api/src/main/java",
    "feature/persona/data/src/main/java",
    "feature/persona/impl/src/main/java",
    "feature/persona/presentation/src/main/java",
        "feature/plugin/api/src/main/java",
        "feature/plugin/data/src/main/java",
        "feature/plugin/presentation/src/main/java",
        "feature/plugin/runtime/src/main/java",
    "feature/provider/api/src/main/java",
    "feature/provider/data/src/main/java",
    "feature/provider/impl/src/main/java",
    "feature/provider/presentation/src/main/java",
    "feature/provider/runtime/src/main/java",
    "feature/qq/api/src/main/java",
    "feature/qq/data/src/main/java",
    "feature/qq/presentation/src/main/java",
    "feature/qq/runtime/src/main/java",
    "feature/resource/api/src/main/java",
    "feature/resource/data/src/main/java",
    "feature/resource/impl/src/main/java",
    "feature/resource/presentation/src/main/java",
    "feature/settings/api/src/main/java",
    "feature/settings/presentation/src/main/java",
    "feature/voiceasset/api/src/main/java",
    "feature/voiceasset/data/src/main/java",
    "feature/voiceasset/presentation/src/main/java",
)
val architectureSourceRootsReportPath = "build/reports/architecture/source-roots.txt"
val architectureDebtReportPath = "build/reports/architecture/debt.txt"
val globalSingletonAllowlistPath = "app/src/test/resources/architecture/global-singleton-allowlist.txt"
val staticRepositoryUsageAllowlistPath = "app/src/test/resources/architecture/static-repository-usage-allowlist.txt"

fun trackedMainSourceRoots(project: Project): List<String> {
    val output = ByteArrayOutputStream()
    project.exec {
        commandLine("git", "ls-files")
        standardOutput = output
    }
    return output.toString(Charsets.UTF_8.name())
        .lineSequence()
        .map(String::trim)
        .filter { path -> path.endsWith(".kt") || path.endsWith(".java") }
        .mapNotNull { path ->
            val marker = "/src/main/java/"
            val markerIndex = path.indexOf(marker)
            if (markerIndex == -1) {
                null
            } else {
                path.substring(0, markerIndex + marker.length - 1)
            }
        }
        .distinct()
        .sorted()
        .toList()
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            compileSdk = ELYMBOT_COMPILE_SDK
            defaultConfig {
                minSdk = ELYMBOT_MIN_SDK
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            compileSdk = ELYMBOT_COMPILE_SDK
            defaultConfig {
                minSdk = ELYMBOT_MIN_SDK
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.android") {
        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = ELYMBOT_JVM_TARGET
            }
        }
    }

    plugins.withId("com.google.devtools.ksp") {
        extensions.configure<KspExtension>("ksp") {
            arg("room.schemaLocation", project.layout.projectDirectory.dir("schemas").asFile.path)
            arg("room.incremental", "true")
        }
    }
}

project(":app") {
    plugins.withId("com.android.application") {
        afterEvaluate {
            val debugUnitTest = tasks.named<Test>("testDebugUnitTest")
            debugUnitTest.configure {
                dependsOn(rootProject.tasks.named("preparePluginSampleArtifacts"))
            }

            tasks.register<Test>("architectureDebugUnitTest") {
                group = "verification"
                description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

                dependsOn(":architectureSourceRootsReport", ":architectureDebtReport")
                testClassesDirs = debugUnitTest.get().testClassesDirs
                classpath = debugUnitTest.get().classpath
                shouldRunAfter(debugUnitTest)

                filter {
                    includeTestsMatching("com.astrbot.android.architecture.*")
                    includeTestsMatching("com.astrbot.android.di.startup.StrictHiltOnlyStartupHotspotSourceTest")
                }
            }
        }
    }
}

val prepareTemplateSamplePluginPackage by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the template sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/template-sample/package"))
    archiveFileName.set("astrbot_plugin_template.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/template-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareGreetingToolkitSamplePluginPackage by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the greeting toolkit sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/greeting-toolkit-sample/package"))
    archiveFileName.set("greeting-toolkit-1.0.0.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/greeting-toolkit-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareMemeManagerSamplePluginPackage100 by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the meme manager 1.0.0 sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/meme-manager-sample/1.0.0/package"))
    archiveFileName.set("meme-manager-1.0.0.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/meme-manager-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareMemeManagerSamplePluginPackage110 by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the meme manager 1.1.0 sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/meme-manager-sample/1.1.0/package"))
    archiveFileName.set("meme-manager-1.1.0.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/meme-manager-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareMemeManagerSampleCatalog by tasks.registering(Sync::class) {
    group = "verification"
    description = "Copies the meme manager sample catalog fixture to the generated artifact directory."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/meme-manager-sample/catalog"))
    into(layout.projectDirectory.dir("artifacts/plugins/meme-manager-sample/catalog"))
}

tasks.register("preparePluginSampleArtifacts") {
    group = "verification"
    description = "Builds deterministic sample plugin artifacts required by app unit tests."

    dependsOn(
        prepareTemplateSamplePluginPackage,
        prepareGreetingToolkitSamplePluginPackage,
        prepareMemeManagerSamplePluginPackage100,
        prepareMemeManagerSamplePluginPackage110,
        prepareMemeManagerSampleCatalog,
    )
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

    dependsOn("architectureSourceRootsReport")
    dependsOn("architectureDebtReport")
    dependsOn(":architecture-tests:test")
    dependsOn(":app:architectureDebugUnitTest")
}

tasks.register("architectureSourceRootsReport") {
    group = "verification"
    description = "Writes the repo-wide source roots scanned by architecture checks."

    val reportFile = layout.projectDirectory.file(architectureSourceRootsReportPath)
    outputs.file(reportFile)
    outputs.upToDateWhen { false }

    doLast {
        val moduleMainSourceRoots = trackedMainSourceRoots(project)
        val missingModuleRoots = moduleMainSourceRoots.filterNot { sourceRoot ->
            sourceRoot in architectureMainSourceRoots
        }
        check(missingModuleRoots.isEmpty()) {
            "Architecture source roots are missing current Gradle module roots: $missingModuleRoots"
        }

        val lines = architectureMainSourceRoots.map { sourceRoot ->
            val rootDir = layout.projectDirectory.dir(sourceRoot).asFile
            check(rootDir.isDirectory) {
                "Architecture source root is missing: $sourceRoot"
            }
            val kotlinFileCount = rootDir.walkTopDown()
                .count { file -> file.isFile && file.extension == "kt" }
            "$sourceRoot | kotlinFiles=$kotlinFileCount"
        }

        val output = reportFile.asFile
        output.parentFile.mkdirs()
        output.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }
}

tasks.register("architectureDebtReport") {
    group = "verification"
    description = "Writes the repo-wide architecture debt baseline from source roots and allowlists."

    val reportFile = layout.projectDirectory.file(architectureDebtReportPath)
    val singletonAllowlistFile = layout.projectDirectory.file(globalSingletonAllowlistPath)
    val staticRepositoryAllowlistFile = layout.projectDirectory.file(staticRepositoryUsageAllowlistPath)
    inputs.file(singletonAllowlistFile)
    inputs.file(staticRepositoryAllowlistFile)
    outputs.file(reportFile)
    outputs.upToDateWhen { false }

    doLast {
        fun parseAllowlist(relativePath: String, expectedColumns: Int): List<List<String>> {
            val file = layout.projectDirectory.file(relativePath).asFile
            check(file.isFile) {
                "Architecture allowlist is missing: $relativePath"
            }
            return file.readLines(Charsets.UTF_8)
                .asSequence()
                .map { line -> line.trim() }
                .filter { line -> line.isNotBlank() && !line.startsWith("#") }
                .mapIndexed { index, line ->
                    val parts = line.split("|").map { part -> part.trim() }
                    check(parts.size == expectedColumns) {
                        "Invalid architecture allowlist entry at $relativePath:${index + 1}: $line"
                    }
                    parts
                }
                .toList()
        }

        fun Map<String, Int>.appendTo(builder: StringBuilder) {
            if (isEmpty()) {
                builder.appendLine("- none")
                return
            }
            toSortedMap().forEach { (key, count) ->
                builder.appendLine("- $key: $count")
            }
        }

        val singletonRows = parseAllowlist(globalSingletonAllowlistPath, expectedColumns = 8)
        val staticRepositoryRows = parseAllowlist(staticRepositoryUsageAllowlistPath, expectedColumns = 7)
        val singletonCategoryCounts = singletonRows.groupingBy { parts -> parts[2] }.eachCount()
        val singletonOwnerCounts = singletonRows.groupingBy { parts -> parts[3] }.eachCount()
        val staticOwnerCounts = staticRepositoryRows.groupingBy { parts -> parts[2] }.eachCount()
        val staticSymbolCounts = staticRepositoryRows.groupingBy { parts -> parts[1] }.eachCount()

        val report = buildString {
            appendLine("Architecture Debt Report")
            appendLine()
            appendLine("source roots:")
            architectureMainSourceRoots.forEach { sourceRoot ->
                val rootDir = layout.projectDirectory.dir(sourceRoot).asFile
                check(rootDir.isDirectory) {
                    "Architecture source root is missing: $sourceRoot"
                }
                val kotlinFileCount = rootDir.walkTopDown()
                    .count { file -> file.isFile && file.extension == "kt" }
                appendLine("- $sourceRoot | kotlinFiles=$kotlinFileCount")
            }
            appendLine()
            appendLine("global singleton allowlist:")
            appendLine("- total: ${singletonRows.size}")
            appendLine("by category:")
            singletonCategoryCounts.appendTo(this)
            appendLine("by owner:")
            singletonOwnerCounts.appendTo(this)
            appendLine()
            appendLine("static repository usage allowlist:")
            appendLine("- total: ${staticRepositoryRows.size}")
            appendLine("by owner:")
            staticOwnerCounts.appendTo(this)
            appendLine("by symbol:")
            staticSymbolCounts.appendTo(this)
        }

        val output = reportFile.asFile
        output.parentFile.mkdirs()
        output.writeText(report)
    }
}
