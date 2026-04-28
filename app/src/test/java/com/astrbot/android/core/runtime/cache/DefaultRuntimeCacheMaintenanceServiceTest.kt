package com.astrbot.android.core.runtime.cache

import com.astrbot.android.core.common.logging.RuntimeLogger
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultRuntimeCacheMaintenanceServiceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missing_targets_return_zero_and_recreate_directories() = runTest {
        val filesDir = temporaryFolder.newFolder("files")
        val logger = RecordingRuntimeLogger()
        val service = DefaultRuntimeCacheMaintenanceService(filesDir, logger)

        val summary = service.clearSafeRuntimeCaches()

        assertEquals("Cleared 0 B from runtime cache.", summary)
        assertEquals(RuntimeCacheCleanupState(isRunning = false, lastSummary = summary), service.state.value)
        assertTrue(filesDir.resolve("runtime/downloads").isDirectory)
        assertTrue(filesDir.resolve("runtime/tts-out").isDirectory)
        assertTrue(filesDir.resolve("runtime/rootfs/ubuntu/var/cache/apt/archives").isDirectory)
        assertTrue(filesDir.resolve("runtime/rootfs/ubuntu/var/cache/apt/archives/partial").isDirectory)
        assertTrue(filesDir.resolve("runtime/rootfs/ubuntu/root/.config/QQ/nt_qq/global/nt_data/Log").isDirectory)
        assertEquals(listOf("Runtime cache cleanup finished: 0 B freed"), logger.messages)
    }

    @Test
    fun cleanup_rebuilds_targets_and_apt_partial_without_expanding_target_set() = runTest {
        val filesDir = temporaryFolder.newFolder("files")
        val untouched = filesDir.resolve("runtime/keep/me.txt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("outside target")
        }
        filesDir.resolve("runtime/downloads/download.bin").writeBytesWithParents(ByteArray(1024))
        filesDir.resolve("runtime/tts-out/speech.wav").writeBytesWithParents(ByteArray(512))
        filesDir.resolve("runtime/rootfs/ubuntu/var/cache/apt/archives/pkg.deb").writeBytesWithParents(ByteArray(256))
        filesDir.resolve("runtime/rootfs/ubuntu/var/cache/apt/archives/partial/temp")
            .writeBytesWithParents(ByteArray(128))
        filesDir.resolve("runtime/rootfs/ubuntu/root/.config/QQ/nt_qq/global/nt_data/Log/qq.log")
            .writeBytesWithParents(ByteArray(128))
        val logger = RecordingRuntimeLogger()
        val service = DefaultRuntimeCacheMaintenanceService(filesDir, logger)

        val summary = service.clearSafeRuntimeCaches()

        assertEquals("Cleared 2.0 KB from runtime cache.", summary)
        assertTrue(filesDir.resolve("runtime/downloads").isDirectory)
        assertTrue(filesDir.resolve("runtime/downloads").listFiles().orEmpty().isEmpty())
        assertTrue(filesDir.resolve("runtime/tts-out").isDirectory)
        assertTrue(filesDir.resolve("runtime/rootfs/ubuntu/var/cache/apt/archives").isDirectory)
        assertTrue(filesDir.resolve("runtime/rootfs/ubuntu/var/cache/apt/archives/partial").isDirectory)
        assertTrue(filesDir.resolve("runtime/rootfs/ubuntu/root/.config/QQ/nt_qq/global/nt_data/Log").isDirectory)
        assertTrue("Non-target runtime files must not be deleted", untouched.exists())
        assertEquals(RuntimeCacheCleanupState(isRunning = false, lastSummary = summary), service.state.value)
        assertEquals(listOf("Runtime cache cleanup finished: 2.0 KB freed"), logger.messages)
    }

    @Test
    fun delete_failure_reports_error_and_resets_running_state() = runTest {
        val filesDir = temporaryFolder.newFolder("files")
        val protectedFile = filesDir.resolve("runtime/downloads/protected.bin").also { file ->
            file.parentFile.mkdirs()
            file.writeBytes(ByteArray(32))
        }
        val logger = RecordingRuntimeLogger()
        val service = DefaultRuntimeCacheMaintenanceService(
            filesDir = filesDir,
            logger = logger,
            fileSystem = FailingRuntimeCacheFileSystem(deleteFailure = protectedFile),
        )

        val result = runCatching { service.clearSafeRuntimeCaches() }

        assertTrue(result.isFailure)
        val summary = requireNotNull(result.exceptionOrNull()).message.orEmpty()
        assertTrue(summary.contains("Failed to clear"))
        assertFalse(service.state.value.isRunning)
        assertEquals(summary, service.state.value.lastSummary)
        assertEquals(listOf("Runtime cache cleanup failed: $summary"), logger.messages)
    }

    @Test
    fun recreate_failure_reports_error_and_resets_running_state() = runTest {
        val filesDir = temporaryFolder.newFolder("files")
        val target = filesDir.resolve("runtime/downloads")
        val logger = RecordingRuntimeLogger()
        val service = DefaultRuntimeCacheMaintenanceService(
            filesDir = filesDir,
            logger = logger,
            fileSystem = FailingRuntimeCacheFileSystem(mkdirFailure = target),
        )

        val result = runCatching { service.clearSafeRuntimeCaches() }

        assertTrue(result.isFailure)
        val summary = requireNotNull(result.exceptionOrNull()).message.orEmpty()
        assertTrue(summary.contains("Failed to recreate"))
        assertFalse(service.state.value.isRunning)
        assertEquals(summary, service.state.value.lastSummary)
        assertEquals(listOf("Runtime cache cleanup failed: $summary"), logger.messages)
    }

    private class RecordingRuntimeLogger : RuntimeLogger {
        val messages = mutableListOf<String>()

        override fun append(message: String) {
            messages += message
        }
    }

    private class FailingRuntimeCacheFileSystem(
        private val deleteFailure: File? = null,
        private val mkdirFailure: File? = null,
    ) : RuntimeCacheFileSystem {
        override fun listFiles(directory: File): List<File> {
            return directory.listFiles().orEmpty().toList()
        }

        override fun mkdirs(directory: File): Boolean {
            if (directory.canonicalFile == mkdirFailure?.canonicalFile) return false
            return directory.mkdirs()
        }

        override fun deleteRecursively(file: File): Boolean {
            if (file.canonicalFile == deleteFailure?.canonicalFile) return false
            return file.deleteRecursively()
        }
    }

    private fun File.resolve(relativePath: String): File {
        return File(this, relativePath)
    }

    private fun File.writeBytesWithParents(bytes: ByteArray) {
        parentFile.mkdirs()
        writeBytes(bytes)
    }
}
