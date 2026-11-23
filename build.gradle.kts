import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val packageablePluginIds = setOf("scheduled-tasks", "qq")
val selectedPluginId = providers.gradleProperty("elymbotPluginId").orNull

tasks.register("packagePluginDex") {
    dependsOn(":core:jar")
    if (selectedPluginId != null) {
        if (selectedPluginId !in packageablePluginIds) {
            throw GradleException("Unsupported plugin ID for DEX packaging")
        }
        dependsOn(":plugins:$selectedPluginId:bundleReleaseAar")
    }

    doLast {
        val pluginId = selectedPluginId
            ?: throw GradleException("Pass -PelymbotPluginId=scheduled-tasks or -PelymbotPluginId=qq")
        val pluginProject = rootProject.project(":plugins:$pluginId")
        val sdkPath = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw GradleException("Set ANDROID_HOME or ANDROID_SDK_ROOT to the installed Android SDK")
        val sdkDirectory = File(sdkPath).canonicalFile
        val buildToolsVersion = libs.versions.android.buildTools.get()
        val compileSdk = libs.versions.android.compileSdk.get()
        val minSdk = libs.versions.android.minSdk.get()
        val d8Jar = File(sdkDirectory, "build-tools/$buildToolsVersion/lib/d8.jar")
        val androidJar = listOf("android-$compileSdk", "android-${compileSdk}.0")
            .distinct()
            .map { platformName -> File(sdkDirectory, "platforms/$platformName/android.jar") }
            .firstOrNull { it.isFile }
        val javaExecutable = File(File(System.getProperty("java.home"), "bin"), "java.exe")
        if (Runtime.version().feature() != 17) {
            throw GradleException("Plugin DEX packaging requires JDK 17")
        }
        val aarFile = File(pluginProject.buildDir, "outputs/aar/$pluginId-release.aar")
        if (!d8Jar.isFile) throw GradleException("Android SDK Build Tools $buildToolsVersion is missing its lib/d8.jar")
        if (androidJar == null) {
            throw GradleException("Android SDK platform android-$compileSdk or android-${compileSdk}.0 is missing android.jar")
        }
        if (!javaExecutable.isFile) throw GradleException("The running JDK 17 java.exe could not be found")
        if (!aarFile.isFile) throw GradleException("The release AAR for plugin $pluginId is missing")

        val workParent = File(pluginProject.buildDir, "tmp/elymbot-plugin-dex")
        Files.createDirectories(workParent.toPath())
        val workDirectory = Files.createTempDirectory(workParent.toPath(), "$pluginId-").toFile().canonicalFile
        val dexDirectory = File(workDirectory, "dex")
        val extractedDirectory = File(workDirectory, "extracted").apply { mkdirs() }
        Files.createDirectories(dexDirectory.toPath())

        fun extractClassesJar(artifact: File, destination: File): File? {
            if (!artifact.isFile) throw GradleException("Resolved plugin artifact is missing")
            when (artifact.extension.lowercase(Locale.ROOT)) {
                "jar" -> return artifact.canonicalFile
                "aar" -> ZipFile(artifact).use { archive ->
                    val entry = archive.getEntry("classes.jar") ?: return null
                    destination.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    return destination.canonicalFile
                }
                else -> throw GradleException("Unsupported plugin runtime artifact type: ${artifact.extension}")
            }
        }

        fun isHostProvided(artifact: ResolvedArtifactResult): Boolean {
            val component = artifact.id.componentIdentifier
            if (component is ProjectComponentIdentifier) return component.projectPath == ":core"
            val module = component as? ModuleComponentIdentifier ?: return false
            return module.group == "org.jetbrains.kotlin" &&
                (module.module == "kotlin-stdlib" || module.module.startsWith("kotlin-stdlib-")) ||
                module.group == "org.jetbrains" && module.module == "annotations" ||
                module.group == "org.jetbrains.kotlinx" &&
                    (module.module.startsWith("kotlinx-coroutines-") || module.module.startsWith("kotlinx-serialization-"))
        }

        fun artifactLabel(artifact: ResolvedArtifactResult): String = artifact.id.componentIdentifier.displayName

        try {
            val aarClasses = File(extractedDirectory, "plugin-classes.jar")
            extractClassesJar(aarFile, aarClasses)
                ?: throw GradleException("Plugin release AAR has no classes.jar")

            val runtimeConfiguration = pluginProject.configurations.getByName("releaseRuntimeClasspath")
            val runtimeArtifacts = runtimeConfiguration.incoming.artifacts.artifacts.sortedBy(::artifactLabel)
            val programComponents = runtimeArtifacts
                .filterNot(::isHostProvided)
                .map { it.id.componentIdentifier.displayName }
                .toSet()
            val programInputs = mutableListOf(aarClasses)
            var artifactIndex = 0
            for (artifact in runtimeArtifacts) {
                val component = artifact.id.componentIdentifier
                if (component is ProjectComponentIdentifier && component.projectPath != ":core") {
                    throw GradleException("Plugin runtime classpath may not bundle another project implementation")
                }
                if (isHostProvided(artifact)) {
                    logger.lifecycle("Plugin DEX excludes host-provided dependency: ${artifactLabel(artifact)}")
                    continue
                }
                val classes = extractClassesJar(
                    artifact.file,
                    File(extractedDirectory, "runtime-${artifactIndex++}.jar"),
                )
                if (classes != null) {
                    logger.lifecycle("Plugin DEX includes runtime dependency: ${artifactLabel(artifact)}")
                    programInputs += classes
                }
            }

            val compileArtifacts = pluginProject.configurations.getByName("releaseCompileClasspath")
                .incoming.artifacts.artifacts.sortedBy(::artifactLabel)
            val classpathInputs = mutableListOf<File>()
            artifactIndex = 0
            for (artifact in compileArtifacts) {
                if (artifactLabel(artifact) in programComponents) continue
                val component = artifact.id.componentIdentifier
                if (component is ProjectComponentIdentifier && component.projectPath != ":core") {
                    throw GradleException("Plugin compile classpath may not depend on another plugin project")
                }
                val classes = extractClassesJar(
                    artifact.file,
                    File(extractedDirectory, "classpath-${artifactIndex++}.jar"),
                )
                if (classes != null) classpathInputs += classes
            }

            val d8Arguments = mutableListOf(
                "--min-api", minSdk,
                "--lib", androidJar.absolutePath,
                "--output", dexDirectory.absolutePath,
            )
            classpathInputs.distinctBy { it.canonicalPath }.sortedBy(File::getName).forEach { classpathFile ->
                d8Arguments += listOf("--classpath", classpathFile.absolutePath)
            }
            programInputs.distinctBy { it.canonicalPath }.forEach { programFile ->
                d8Arguments += programFile.absolutePath
            }

            logger.lifecycle("Building $pluginId plugin DEX with Android SDK Build Tools $buildToolsVersion (JDK 17, min API $minSdk)")
            val command = listOf(
                javaExecutable.absolutePath,
                "-cp",
                d8Jar.absolutePath,
                "com.android.tools.r8.D8",
            ) + d8Arguments
            val process = ProcessBuilder(command)
                .directory(rootProject.projectDir)
                .inheritIO()
                .start()
            val exitCode = try {
                process.waitFor()
            } catch (interrupted: InterruptedException) {
                process.destroyForcibly()
                Thread.currentThread().interrupt()
                throw GradleException("Plugin DEX packaging was interrupted", interrupted)
            }
            if (exitCode != 0) {
                throw GradleException("D8 failed with exit code $exitCode")
            }

            val dexOutputs = dexDirectory.listFiles().orEmpty().filter { it.isFile && it.extension == "dex" }
            val classesDex = File(dexDirectory, "classes.dex")
            if (!classesDex.isFile || dexOutputs.map(File::getName) != listOf("classes.dex")) {
                throw GradleException("Plugin DEX packaging requires exactly one output file named classes.dex")
            }

            val resources = sortedMapOf<String, File>()
            val resourceRoot = File(pluginProject.projectDir, "src/main/resources")
            if (resourceRoot.exists()) {
                if (!resourceRoot.isDirectory) throw GradleException("Plugin resources path is not a directory")
                pluginProject.fileTree(resourceRoot).files.sortedBy { it.relativeTo(resourceRoot).invariantSeparatorsPath }
                    .forEach { resourceFile ->
                        if (!resourceFile.isFile || Files.isSymbolicLink(resourceFile.toPath())) {
                            throw GradleException("Plugin resources must be regular files")
                        }
                        val resourcePath = resourceFile.relativeTo(resourceRoot).invariantSeparatorsPath
                        val segments = resourcePath.split('/')
                        if (resourcePath.startsWith('/') || '\\' in resourcePath ||
                            segments.any { it.isEmpty() || it == "." || it == ".." }
                        ) {
                            throw GradleException("Plugin resource path is unsafe")
                        }
                        resources[resourcePath] = resourceFile
                    }
            }

            val outputJar = File(pluginProject.buildDir, "outputs/elymbot-plugin/plugin.jar")
            outputJar.parentFile.mkdirs()
            val temporaryJar = File(outputJar.parentFile, ".${outputJar.name}-${UUID.randomUUID()}.tmp")
            try {
                ZipOutputStream(temporaryJar.outputStream().buffered()).use { output ->
                    output.setLevel(Deflater.BEST_SPEED)

                    fun addFile(path: String, file: File, store: Boolean) {
                        val entry = ZipEntry(path).apply {
                            setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0))
                            if (store) {
                                method = ZipEntry.STORED
                                size = file.length()
                                compressedSize = file.length()
                                crc = CRC32().also { checksum ->
                                    file.inputStream().buffered().use { input ->
                                        val buffer = ByteArray(32 * 1024)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            checksum.update(buffer, 0, count)
                                        }
                                    }
                                }.value
                            }
                        }
                        output.putNextEntry(entry)
                        file.inputStream().buffered().use { input -> input.copyTo(output, 32 * 1024) }
                        output.closeEntry()
                    }

                    addFile("classes.dex", classesDex, store = true)
                    resources.forEach { (resourcePath, resourceFile) ->
                        val compressedAsset = listOf(".xz", ".zip", ".gz", ".png", ".jpg", ".jpeg", ".webp")
                            .any(resourcePath.lowercase(Locale.ROOT)::endsWith)
                        addFile(resourcePath, resourceFile, store = compressedAsset)
                    }
                }
                Files.move(
                    temporaryJar.toPath(),
                    outputJar.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                Files.deleteIfExists(temporaryJar.toPath())
            }

            logger.lifecycle("Plugin DEX JAR ready: ${outputJar.relativeTo(rootProject.projectDir)} (${outputJar.length()} bytes)")
        } finally {
            Files.walk(workDirectory.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path ->
                    Files.deleteIfExists(path)
                }
            }
        }
    }
}
