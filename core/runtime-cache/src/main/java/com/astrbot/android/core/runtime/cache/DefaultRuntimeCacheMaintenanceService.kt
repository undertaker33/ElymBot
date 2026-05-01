package com.astrbot.android.core.runtime.cache

import com.astrbot.android.core.common.logging.RuntimeLogger
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DefaultRuntimeCacheMaintenanceService @Inject constructor(
    filesDir: File,
    private val logger: RuntimeLogger,
    private val fileSystem: RuntimeCacheFileSystem = RealRuntimeCacheFileSystem,
) : RuntimeCacheMaintenancePort {
    private val runtimeRoot = filesDir.canonicalFile
    private val cleanupTargets = listOf(
        CleanupTarget("runtime/downloads", recreate = true),
        CleanupTarget("runtime/tts-out", recreate = true),
        CleanupTarget(
            relativePath = "runtime/rootfs/ubuntu/var/cache/apt/archives",
            recreate = true,
            extraDirectories = listOf("partial"),
        ),
        CleanupTarget(
            relativePath = "runtime/rootfs/ubuntu/root/.config/QQ/nt_qq/global/nt_data/Log",
            recreate = true,
        ),
    )

    private val _state = MutableStateFlow(RuntimeCacheCleanupState())
    override val state: StateFlow<RuntimeCacheCleanupState> = _state.asStateFlow()

    override suspend fun clearSafeRuntimeCaches(): String = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isRunning = true)
        runCatching {
            val clearedBytes = cleanupTargets.sumOf { target ->
                clearDirectoryContents(resolveTarget(target), target)
            }
            val formattedBytes = formatBytes(clearedBytes)
            val summary = "Cleared $formattedBytes from runtime cache."
            logger.append("Runtime cache cleanup finished: $formattedBytes freed")
            _state.value = RuntimeCacheCleanupState(
                isRunning = false,
                lastSummary = summary,
            )
            summary
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            logger.append("Runtime cache cleanup failed: $summary")
            _state.value = RuntimeCacheCleanupState(
                isRunning = false,
                lastSummary = summary,
            )
            throw error
        }
    }

    private fun resolveTarget(target: CleanupTarget): File {
        require(!target.relativePath.startsWith("/") && !target.relativePath.contains("..")) {
            "Runtime cache cleanup target must be a fixed relative path: ${target.relativePath}"
        }
        val directory = File(runtimeRoot, target.relativePath).canonicalFile
        require(directory.isInside(runtimeRoot)) {
            "Runtime cache cleanup target escaped runtime root: ${directory.absolutePath}"
        }
        target.extraDirectories.forEach { child ->
            require(!child.startsWith("/") && !child.contains("/") && !child.contains("..")) {
                "Runtime cache cleanup extra directory must be a fixed child name: $child"
            }
        }
        return directory
    }

    private fun clearDirectoryContents(directory: File, target: CleanupTarget): Long {
        if (!directory.exists()) {
            if (target.recreate) recreateDirectory(directory, target.extraDirectories)
            return 0L
        }
        require(directory.isDirectory) {
            "Runtime cache cleanup target is not a directory: ${directory.absolutePath}"
        }

        val targetBoundary = directory.canonicalFile
        val bytes = fileSystem.listFiles(directory).sumOf { child ->
            val safeChild = child.canonicalFile
            require(safeChild.isInside(targetBoundary) && safeChild.isInside(runtimeRoot)) {
                "Runtime cache cleanup child escaped target: ${safeChild.absolutePath}"
            }
            val childBytes = safeChild.directorySizeWithin(targetBoundary)
            if (!fileSystem.deleteRecursively(safeChild)) {
                throw IllegalStateException("Failed to clear ${safeChild.absolutePath}")
            }
            childBytes
        }
        if (target.recreate) recreateDirectory(directory, target.extraDirectories)
        return bytes
    }

    private fun recreateDirectory(directory: File, extraDirectories: List<String>) {
        val safeDirectory = directory.canonicalFile
        require(safeDirectory.isInside(runtimeRoot)) {
            "Runtime cache cleanup recreate target escaped runtime root: ${safeDirectory.absolutePath}"
        }
        if (!safeDirectory.exists() && !fileSystem.mkdirs(safeDirectory)) {
            throw IllegalStateException("Failed to recreate ${safeDirectory.absolutePath}")
        }
        extraDirectories.forEach { child ->
            val nested = File(safeDirectory, child).canonicalFile
            require(nested.isInside(safeDirectory) && nested.isInside(runtimeRoot)) {
                "Runtime cache cleanup extra directory escaped target: ${nested.absolutePath}"
            }
            if (!nested.exists() && !fileSystem.mkdirs(nested)) {
                throw IllegalStateException("Failed to recreate ${nested.absolutePath}")
            }
        }
    }

    private fun File.directorySizeWithin(boundary: File): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return walkTopDown().onEach { descendant ->
            val safeDescendant = descendant.canonicalFile
            require(safeDescendant.isInside(boundary) && safeDescendant.isInside(runtimeRoot)) {
                "Runtime cache cleanup descendant escaped target: ${safeDescendant.absolutePath}"
            }
        }.filter { it.isFile }.sumOf { it.length() }
    }

    private fun File.isInside(parent: File): Boolean {
        val parentPath = parent.canonicalFile.toPath()
        val childPath = canonicalFile.toPath()
        return childPath == parentPath || childPath.startsWith(parentPath)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        val pattern = if (value >= 10 || unitIndex == 0) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, pattern, value, units[unitIndex])
    }

    private data class CleanupTarget(
        val relativePath: String,
        val recreate: Boolean,
        val extraDirectories: List<String> = emptyList(),
    )
}

interface RuntimeCacheFileSystem {
    fun listFiles(directory: File): List<File>

    fun mkdirs(directory: File): Boolean

    fun deleteRecursively(file: File): Boolean
}

object RealRuntimeCacheFileSystem : RuntimeCacheFileSystem {
    override fun listFiles(directory: File): List<File> {
        return directory.listFiles().orEmpty().toList()
    }

    override fun mkdirs(directory: File): Boolean {
        return directory.mkdirs()
    }

    override fun deleteRecursively(file: File): Boolean {
        return file.deleteRecursively()
    }
}
