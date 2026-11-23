package com.elymbot.android.runtime.container

import android.content.Context
import android.os.Build
import android.os.Process as AndroidProcess
import android.system.Os
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.lang.Process as JavaProcess
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Starts a fixed guest shell through the native proot packaged with the App. */
internal class ProotGuestLauncher(context: Context) {
    private val appContext = context.applicationContext

    @Throws(IOException::class)
    fun launch(
        pluginPrivateDirectory: File,
        guestRootfsDirectory: File,
        guestEntry: String,
    ): ProotGuestProcess {
        check(AndroidProcess.is64Bit() && Build.SUPPORTED_ABIS.firstOrNull() == ARM64_ABI)

        val pluginDirectory = requireAppPrivateDirectory(pluginPrivateDirectory)
        val rootfsDirectory = requireDirectoryWithin(pluginDirectory, guestRootfsDirectory)
        val guestEntryPath = requireGuestEntry(rootfsDirectory, guestEntry)

        // Read this for each launch because Android can move native libraries on update.
        val nativeLibraryDirectory = File(appContext.applicationInfo.nativeLibraryDir).canonicalFile
        val proot = requireFile(nativeLibraryDirectory, PROOT_BINARY, executable = true)
        val loader = requireFile(nativeLibraryDirectory, PROOT_LOADER, executable = true)
        val talloc = requireFile(nativeLibraryDirectory, TALLOC_BINARY, executable = false)

        val temporaryDirectory = Files.createTempDirectory(
            appContext.cacheDir.toPath(),
            TEMP_DIRECTORY_PREFIX,
        ).toFile()
        try {
            val librarySearchDirectory = File(temporaryDirectory, "lib")
            if (!librarySearchDirectory.mkdirs() && !librarySearchDirectory.isDirectory) {
                throw IOException()
            }
            val tallocLink = File(librarySearchDirectory, TALLOC_SONAME)
            Os.symlink(talloc.absolutePath, tallocLink.absolutePath)

            val guestTempPath = temporaryDirectory.absolutePath
            val command = listOf(
                proot.absolutePath,
                "-0",
                "-r",
                rootfsDirectory.absolutePath,
                "--link2symlink",
                "-b",
                "/dev",
                "-b",
                "/proc",
                "-b",
                "/sys",
                "-b",
                "/system",
                "-b",
                "/apex",
                "-b",
                "$guestTempPath:$guestTempPath",
                "-b",
                "$guestTempPath:/dev/shm",
                "-w",
                "/",
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "TMPDIR=$guestTempPath",
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/bash",
                guestEntryPath,
            )
            val processBuilder = ProcessBuilder(command)
            processBuilder.environment().clear()
            processBuilder.environment()["LD_LIBRARY_PATH"] =
                "${librarySearchDirectory.absolutePath}:${nativeLibraryDirectory.absolutePath}"
            processBuilder.environment()["PROOT_LOADER"] = loader.absolutePath
            processBuilder.environment()["PROOT_TMP_DIR"] = guestTempPath
            processBuilder.environment()["TMPDIR"] = guestTempPath
            processBuilder.redirectErrorStream(true)
            processBuilder.redirectOutput(File(DISCARD_OUTPUT))

            return ProotGuestProcess(processBuilder.start(), temporaryDirectory)
        } catch (failure: Exception) {
            temporaryDirectory.deleteRecursively()
            throw failure
        }
    }

    private fun requireAppPrivateDirectory(directory: File): File {
        val canonicalDirectory = directory.canonicalFile
        val appFilesPath = appContext.filesDir.canonicalFile.toPath()
        require(canonicalDirectory.isDirectory)
        require(canonicalDirectory.toPath() != appFilesPath)
        require(canonicalDirectory.toPath().startsWith(appFilesPath))
        return canonicalDirectory
    }

    private fun requireDirectoryWithin(parent: File, directory: File): File {
        val canonicalDirectory = directory.canonicalFile
        require(canonicalDirectory.isDirectory)
        require(canonicalDirectory.toPath().startsWith(parent.canonicalFile.toPath()))
        return canonicalDirectory
    }

    private fun requireGuestEntry(rootfsDirectory: File, guestEntry: String): String {
        require(guestEntry.startsWith('/'))
        val segments = guestEntry.split('/').filter { it.isNotEmpty() }
        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." })

        val entryFile = File(rootfsDirectory, segments.joinToString(File.separator)).canonicalFile
        require(entryFile.isFile)
        require(entryFile.toPath().startsWith(rootfsDirectory.canonicalFile.toPath()))
        return "/${segments.joinToString("/")}"
    }

    private fun requireFile(directory: File, name: String, executable: Boolean): File {
        val file = File(directory, name)
        if (!file.isFile || !file.canRead() || (executable && !file.canExecute())) {
            throw IOException()
        }
        return file
    }

    private companion object {
        const val ARM64_ABI = "arm64-v8a"
        const val PROOT_BINARY = "libproot.so"
        const val PROOT_LOADER = "libloader.so"
        const val TALLOC_BINARY = "liblibtalloc.so.2.so"
        const val TALLOC_SONAME = "libtalloc.so.2"
        const val TEMP_DIRECTORY_PREFIX = "elymbot-proot-"
        const val DISCARD_OUTPUT = "/dev/null"
    }
}

/** Owns the proot process and its temporary native-library search directory. */
internal class ProotGuestProcess internal constructor(
    private val process: JavaProcess,
    private val temporaryDirectory: File,
) : Closeable {
    val isAlive: Boolean
        get() = process.isAlive

    fun awaitExit(timeoutMillis: Long): Int? {
        require(timeoutMillis >= 0)
        return if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.exitValue()
        } else {
            null
        }
    }

    fun stop(): Boolean {
        if (!process.isAlive) return true

        process.destroy()
        if (process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return true

        process.destroyForcibly()
        return process.waitFor(FORCE_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun close() {
        if (stop()) {
            temporaryDirectory.deleteRecursively()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_SECONDS = 2L
        const val FORCE_STOP_TIMEOUT_SECONDS = 2L
    }
}
