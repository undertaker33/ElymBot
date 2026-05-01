import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ELYMBOT_COMPILE_SDK = 36
val ELYMBOT_MIN_SDK = 29
val ELYMBOT_JVM_TARGET = "17"

val architectureMainSourceRoots = listOf(
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
val architectureSourceRootsReportPath = "build/reports/architecture/source-roots.txt"
val architectureDebtReportPath = "build/reports/architecture/debt.txt"
val globalSingletonAllowlistPath = "app/src/test/resources/architecture/global-singleton-allowlist.txt"
val staticRepositoryUsageAllowlistPath = "app/src/test/resources/architecture/static-repository-usage-allowlist.txt"

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
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

    plugins.withId("org.jetbrains.kotlin.jvm") {
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

tasks.register("architectureCheck") {
    group = "verification"
    description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

    dependsOn("architectureSourceRootsReport")
    dependsOn("architectureDebtReport")
    dependsOn(":app:architectureDebugUnitTest")
}

tasks.register("architectureSourceRootsReport") {
    group = "verification"
    description = "Writes the repo-wide source roots scanned by architecture checks."

    val reportFile = layout.projectDirectory.file(architectureSourceRootsReportPath)
    outputs.file(reportFile)
    outputs.upToDateWhen { false }

    doLast {
        val moduleMainSourceRoots = subprojects
            .map { project -> project.projectDir.resolve("src/main/java") }
            .filter { sourceRoot -> sourceRoot.isDirectory }
            .map { sourceRoot -> sourceRoot.relativeTo(rootDir).path.replace(java.io.File.separatorChar, '/') }
            .sorted()
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
