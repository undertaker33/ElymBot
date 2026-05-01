
package com.astrbot.android.core.runtime.container

import android.content.Context
import android.system.Os
import com.astrbot.android.core.common.logging.RuntimeLogger
import com.astrbot.android.core.runtime.secret.RuntimeSecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ContainerRuntimeInstaller @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bridgeStatePort: ContainerBridgeStatePort,
    private val runtimeLogger: RuntimeLogger,
    private val runtimeSecretStore: RuntimeSecretStore,
) : ContainerRuntimeInstallerPort {
    private val scriptNames = listOf(
        "container_env.sh",
        "bootstrap_container.sh",
        "prepare_tts_assets.sh",
        "clear_tts_assets.sh",
        "convert_tencent_silk.sh",
        "encode_tencent_silk.py",
        "root_launcher.sh",
        "start_napcat.sh",
        "logout_qq.sh",
        "stop_napcat.sh",
        "status_napcat.sh",
    )

    private val bundledAssetNames = listOf(
        "ubuntu-rootfs.tar.xz",
        "napcat-installer.sh",
        "NapCat.Shell.zip",
        "QQ.deb",
        "launcher.cpp",
        "offline-debs.tar",
        "offline-rootfs-overlay.tar.xz",
    )

    private val installMutex = Mutex()
    @Volatile
    private var installCompleted = false
    @Volatile
    private var warmupJob: Job? = null

    override fun warmUpAsync(scope: CoroutineScope) {
        if (installCompleted || warmupJob?.isActive == true) return
        warmupJob = scope.launch(Dispatchers.IO) {
            try {
                ensureInstalled()
            } finally {
                warmupJob = null
            }
        }
    }

    override suspend fun ensureInstalled() {
        if (installCompleted) return

        installMutex.withLock {
            if (installCompleted) return
            installInternal()
            installCompleted = true
            warmupJob = null
        }
    }

    private fun installInternal() {
        runtimeSecretStore.ensureSecrets()
        val runtimeDir = File(appContext.filesDir, "runtime")
        val binDir = File(runtimeDir, "bin")
        val scriptDir = File(runtimeDir, "scripts")
        val assetsDir = File(runtimeDir, "assets")
        binDir.mkdirs()
        scriptDir.mkdirs()
        assetsDir.mkdirs()

        scriptNames.forEach { name ->
            val target = File(scriptDir, name)
            appContext.assets.open("runtime/scripts/$name").use { input ->
                val normalizedScript = input.readBytes()
                    .toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                target.writeText(normalizedScript, StandardCharsets.UTF_8)
            }
            target.setExecutable(true, true)
        }

        val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir
        runtimeLogger.append("nativeLibraryDir=$nativeLibraryDir")
        installRuntimeLinks(binDir, nativeLibraryDir)
        runtimeLogger.append("Using native binaries from runtime symlinks")

        bundledAssetNames.forEach { name ->
            copyBundledAsset(appContext, "runtime/assets/$name", File(assetsDir, name))
        }

        val ubuntuArchive = File(assetsDir, "ubuntu-rootfs.tar.xz")

        val rootfsDir = File(runtimeDir, "rootfs/ubuntu")
        try {
            RootfsExtractor.ensureExtracted(ubuntuArchive, rootfsDir, runtimeLogger)
            runtimeLogger.append("Network install mode enabled: only Ubuntu rootfs is bundled")
            runtimeLogger.append("Ubuntu rootfs ready at ${rootfsDir.absolutePath}")
        } catch (error: Exception) {
            runtimeLogger.append("Rootfs extraction failed: ${error.message ?: error.javaClass.simpleName}")
            throw IllegalStateException("Required Ubuntu rootfs extraction failed", error)
        }

        bridgeStatePort.applyRuntimeDefaults(
            ContainerBridgeConfig(
                endpoint = "ws://127.0.0.1:6199/ws",
                healthUrl = "http://127.0.0.1:6099",
                autoStart = false,
                commandPreview = "Start NapCat runtime",
            ),
        )
        runtimeLogger.append("Runtime scripts installed to ${scriptDir.absolutePath}")
    }

    private fun copyBundledAsset(context: Context, assetPath: String, target: File) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            runtimeLogger.append("Bundled asset ready: ${target.name}")
        }.onFailure { error ->
            runtimeLogger.append(
                "Bundled asset missing or skipped: ${target.name} (${error.message ?: error.javaClass.simpleName})",
            )
        }
    }

    private fun installRuntimeLinks(binDir: File, nativeLibraryDir: String) {
        val linkMap = listOf(
            "proot" to "libproot.so",
            "loader" to "libloader.so",
            "busybox" to "libbusybox.so",
            "libtalloc.so.2" to "liblibtalloc.so.2.so",
            "bash" to "libbash.so",
            "sudo" to "libsudo.so",
        )

        linkMap.forEach { (linkName, targetName) ->
            val targetFile = File(nativeLibraryDir, targetName)
            if (!targetFile.exists()) {
                runtimeLogger.append("Native dependency missing: ${targetFile.absolutePath}")
                return@forEach
            }

            val linkFile = File(binDir, linkName)
            runCatching {
                if (linkFile.exists() || linkFile.isSymbolicLink()) {
                    linkFile.delete()
                }
                Os.symlink(targetFile.absolutePath, linkFile.absolutePath)
                runtimeLogger.append("Linked $linkName -> $targetName")
            }.onFailure { error ->
                runtimeLogger.append("Failed to link $linkName: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun File.isSymbolicLink(): Boolean = runCatching {
        java.nio.file.Files.isSymbolicLink(toPath())
    }.getOrDefault(false)

}
