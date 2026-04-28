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
    "core/runtime/src/main/java",
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
                jvmTarget = "17"
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
    dependsOn(":app:architectureDebugUnitTest")
}

tasks.register("architectureSourceRootsReport") {
    group = "verification"
    description = "Writes the repo-wide source roots scanned by architecture checks."

    val reportFile = layout.projectDirectory.file(architectureSourceRootsReportPath)
    outputs.file(reportFile)

    doLast {
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
