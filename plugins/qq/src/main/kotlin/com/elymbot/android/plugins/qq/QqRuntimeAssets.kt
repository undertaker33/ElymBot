package com.elymbot.android.plugins.qq

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import com.elymbot.core.contract.PlatformMediaReadResult
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream

internal enum class QqRuntimePreparationFailure {
    ROOTFS_ASSET_MISSING,
    ROOTFS_ASSET_INVALID,
    ROOTFS_EXTRACTION_FAILED,
    ROOT_LAUNCHER_MISSING,
    ROOT_LAUNCHER_INVALID,
}

internal class QqRuntimePreparationException(
    val reason: QqRuntimePreparationFailure,
    cause: Throwable? = null,
) : IOException("QQ runtime preparation failed: $reason", cause)

/** Stages only the signed plugin resources needed by ON-04 into its host-owned private directory. */
internal class QqRuntimeAssets(
    dataDirectory: File,
    private val resourceLoader: ClassLoader,
) {
    private val dataRoot = dataDirectory.canonicalFile
    private val runtimeDirectory = resolvePrivate("runtime")
    private val assetDirectory = resolvePrivate("runtime/assets")
    private val rootfsDirectory = resolvePrivate(ROOTFS_RELATIVE_PATH)
    private val rootfsArchive = resolvePrivate("runtime/assets/ubuntu-rootfs.tar.xz")

    val rootfsRelativePath: String = ROOTFS_RELATIVE_PATH
    val guestEntryPath: String = "/root/$GUEST_ENTRY_PATH"

    fun prepare() {
        ensurePrivateDirectory(runtimeDirectory)
        ensurePrivateDirectory(assetDirectory)
        stageRootfsArchive()
        try {
            QqRootfsInstaller.ensureExtracted(rootfsArchive, rootfsDirectory)
            stageGuestFiles()
            prepareGuestNetwork()
            ensureRuntimeSecrets()
        } catch (failure: QqRuntimePreparationException) {
            throw failure
        } catch (failure: Exception) {
            throw QqRuntimePreparationException(QqRuntimePreparationFailure.ROOTFS_EXTRACTION_FAILED, failure)
        }
    }

    fun guestProgressFile(): File = File(rootfsDirectory, "root/.elymbot/runtime/run/napcat_progress")

    fun guestProgressLabelFile(): File = File(rootfsDirectory, "root/.elymbot/runtime/run/napcat_progress_label")

    fun guestProgressModeFile(): File = File(rootfsDirectory, "root/.elymbot/runtime/run/napcat_progress_mode")

    fun runtimeSecretFile(): File = File(rootfsDirectory, "root/.elymbot/runtime/config/runtime-secrets.env")

    fun oneBotToken(): String = readRuntimeSecrets()[ONEBOT_TOKEN_KEY]
        ?.takeIf(SECRET_VALUE_PATTERN::matches)
        ?: throw IOException("QQ OneBot token is unavailable")

    fun copyMedia(
        source: OneBotMediaSource,
        destination: OutputStream,
        maxBytes: Long,
    ): PlatformMediaReadResult {
        if (maxBytes < 0) return PlatformMediaReadResult.Failed
        val fileToken = source.fileToken?.takeIf(String::isNotBlank) ?: return PlatformMediaReadResult.Unsupported
        if (fileToken.startsWith("http://", true) || fileToken.startsWith("https://", true)) {
            return PlatformMediaReadResult.Unsupported
        }
        val guestPath = fileToken.removePrefix("file://").removePrefix("/")
        val candidate = File(rootfsDirectory, guestPath)
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return PlatformMediaReadResult.NotFound
        if (!canonical.toPath().startsWith(rootfsDirectory.canonicalFile.toPath()) ||
            Files.isSymbolicLink(candidate.toPath()) || !canonical.isFile
        ) {
            return PlatformMediaReadResult.NotFound
        }
        if (canonical.length() > maxBytes) return PlatformMediaReadResult.TooLarge

        var copied = 0L
        return try {
            canonical.inputStream().buffered().use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (copied + count > maxBytes) return PlatformMediaReadResult.TooLarge
                    destination.write(buffer, 0, count)
                    copied += count
                }
            }
            PlatformMediaReadResult.Copied(
                contentType = when (source.segmentType) {
                    "image" -> "image/*"
                    "audio" -> "audio/*"
                    "video" -> "video/*"
                    else -> "application/octet-stream"
                },
                sizeBytes = copied,
                displayName = source.displayName,
            )
        } catch (_: Exception) {
            PlatformMediaReadResult.Failed
        }
    }

    private fun stageRootfsArchive() {
        if (isExpectedAsset(rootfsArchive, ROOTFS_SIZE_BYTES, ROOTFS_SHA256)) return
        stageVerifiedResource(
            resourcePath = ROOTFS_RESOURCE_PATH,
            destination = rootfsArchive,
            expectedSize = ROOTFS_SIZE_BYTES,
            expectedSha256 = ROOTFS_SHA256,
            missingReason = QqRuntimePreparationFailure.ROOTFS_ASSET_MISSING,
            invalidReason = QqRuntimePreparationFailure.ROOTFS_ASSET_INVALID,
        )
    }

    private fun stageGuestFiles() {
        val launcherBytes = readVerifiedResource(
            resourcePath = ROOT_LAUNCHER_RESOURCE_PATH,
            expectedSize = ROOT_LAUNCHER_SIZE_BYTES,
            expectedSha256 = ROOT_LAUNCHER_SHA256,
            missingReason = QqRuntimePreparationFailure.ROOT_LAUNCHER_MISSING,
            invalidReason = QqRuntimePreparationFailure.ROOT_LAUNCHER_INVALID,
        )
        val rootHome = File(rootfsDirectory, "root")
        ensurePrivateDirectory(rootHome)
        writeAtomic(File(rootHome, ROOT_LAUNCHER_GUEST_PATH), launcherBytes, executable = true)
        writeAtomic(File(rootHome, GUEST_ENTRY_PATH), GUEST_ENTRY_SCRIPT.toByteArray(Charsets.UTF_8), executable = true)
        val runtimeHome = File(rootHome, ".elymbot/runtime")
        ensurePrivateDirectory(File(runtimeHome, "run"))
        ensurePrivateDirectory(File(runtimeHome, "config"))
        ensurePrivateDirectory(File(runtimeHome, "assets"))
    }

    private fun prepareGuestNetwork() {
        writeAtomic(
            File(rootfsDirectory, "etc/resolv.conf"),
            RESOLVER_CONFIGURATION.toByteArray(Charsets.UTF_8),
        )
        writeAtomic(
            File(rootfsDirectory, "etc/apt/sources.list"),
            APT_SOURCES_CONFIGURATION.toByteArray(Charsets.UTF_8),
        )
        writeAtomic(
            File(rootfsDirectory, "etc/apt/apt.conf.d/99elymbot"),
            APT_CONFIGURATION.toByteArray(Charsets.UTF_8),
        )
    }

    private fun ensureRuntimeSecrets() {
        val secretFile = runtimeSecretFile()
        if (Files.isSymbolicLink(secretFile.toPath())) throw IOException("QQ runtime secret file is invalid")
        val existing = if (secretFile.isFile && secretFile.length() <= MAX_SECRET_FILE_BYTES) {
            readRuntimeSecrets()
        } else {
            emptyMap()
        }
        val webUiSecret = existing[WEBUI_TOKEN_KEY]?.takeIf(SECRET_VALUE_PATTERN::matches) ?: newSecret()
        val jwtSecret = existing[JWT_TOKEN_KEY]?.takeIf(SECRET_VALUE_PATTERN::matches) ?: newSecret()
        val oneBotToken = existing[ONEBOT_TOKEN_KEY]?.takeIf(SECRET_VALUE_PATTERN::matches) ?: newSecret()
        val content = "$WEBUI_TOKEN_KEY=$webUiSecret\n" +
            "$JWT_TOKEN_KEY=$jwtSecret\n" +
            "$ONEBOT_TOKEN_KEY=$oneBotToken\n"
        writeAtomic(secretFile, content.toByteArray(Charsets.UTF_8), executable = false)
    }

    private fun readRuntimeSecrets(): Map<String, String> {
        val secretFile = runtimeSecretFile()
        if (!secretFile.isFile || Files.isSymbolicLink(secretFile.toPath()) ||
            secretFile.length() > MAX_SECRET_FILE_BYTES
        ) {
            throw IOException("QQ runtime secret file is unavailable")
        }
        return secretFile.readLines(Charsets.UTF_8).mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key in KNOWN_SECRET_KEYS && SECRET_VALUE_PATTERN.matches(value)) key to value else null
        }.toMap()
    }

    private fun newSecret(): String {
        val bytes = ByteArray(SECRET_RANDOM_BYTES)
        SECURE_RANDOM.nextBytes(bytes)
        return bytes.joinToString("") { value -> "%02x".format(value) }
    }

    private fun stageVerifiedResource(
        resourcePath: String,
        destination: File,
        expectedSize: Long,
        expectedSha256: String,
        missingReason: QqRuntimePreparationFailure,
        invalidReason: QqRuntimePreparationFailure,
    ) {
        val input = resourceLoader.getResourceAsStream(resourcePath)
            ?: throw QqRuntimePreparationException(missingReason)
        val temporary = File(destination.parentFile, ".${destination.name}-${UUID.randomUUID()}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            input.use { source ->
                FileOutputStream(temporary).use { fileOutput ->
                    val output = fileOutput.buffered()
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > expectedSize) throw QqRuntimePreparationException(invalidReason)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            if (copied != expectedSize || digest.digest().toHex() != expectedSha256) {
                throw QqRuntimePreparationException(invalidReason)
            }
            moveAtomically(temporary, destination)
        } catch (failure: QqRuntimePreparationException) {
            throw failure
        } catch (failure: Exception) {
            throw QqRuntimePreparationException(invalidReason, failure)
        } finally {
            temporary.delete()
        }
    }

    private fun readVerifiedResource(
        resourcePath: String,
        expectedSize: Long,
        expectedSha256: String,
        missingReason: QqRuntimePreparationFailure,
        invalidReason: QqRuntimePreparationFailure,
    ): ByteArray {
        val input = resourceLoader.getResourceAsStream(resourcePath)
            ?: throw QqRuntimePreparationException(missingReason)
        return input.use { source ->
            val output = java.io.ByteArrayOutputStream(expectedSize.toInt())
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var copied = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                copied += count
                if (copied > expectedSize) throw QqRuntimePreparationException(invalidReason)
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            if (bytes.size.toLong() != expectedSize || sha256(bytes) != expectedSha256) {
                throw QqRuntimePreparationException(invalidReason)
            }
            bytes
        }
    }

    private fun isExpectedAsset(file: File, expectedSize: Long, expectedSha256: String): Boolean {
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) || file.length() != expectedSize) return false
        return runCatching { sha256(file) == expectedSha256 }.getOrDefault(false)
    }

    private fun ensurePrivateDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("QQ private runtime directory is unavailable")
        }
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath()) ||
            !directory.canonicalFile.toPath().startsWith(dataRoot.toPath())
        ) {
            throw IOException("QQ private runtime path is invalid")
        }
    }

    private fun resolvePrivate(relativePath: String): File {
        val target = File(dataRoot, relativePath).canonicalFile
        if (!target.toPath().startsWith(dataRoot.toPath())) {
            throw IllegalArgumentException("QQ runtime path escapes plugin data")
        }
        return target
    }

    private fun writeAtomic(file: File, bytes: ByteArray, executable: Boolean = false) {
        if (file.exists() && Files.isSymbolicLink(file.toPath())) throw IOException("QQ runtime file path is invalid")
        val parent = file.parentFile ?: throw IOException("QQ runtime parent is unavailable")
        ensurePrivateDirectory(parent)
        val temporary = File(parent, ".${file.name}-${UUID.randomUUID()}.part")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (executable && !temporary.setExecutable(true, false)) {
                throw IOException("QQ guest launcher could not be made executable")
            }
            moveAtomically(temporary, file)
        } finally {
            temporary.delete()
        }
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val ROOTFS_RELATIVE_PATH = "runtime/rootfs/ubuntu"
        const val ROOTFS_RESOURCE_PATH = "runtime/ubuntu-rootfs.tar.xz"
        const val ROOTFS_SIZE_BYTES = 64_133_552L
        const val ROOTFS_SHA256 = "91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa"
        const val ROOT_LAUNCHER_RESOURCE_PATH = "runtime/root_launcher.sh"
        const val ROOT_LAUNCHER_SIZE_BYTES = 14_914L
        const val ROOT_LAUNCHER_SHA256 = "db0cafeaba1608235517ac598ac36cd5eb5a2278600dbcadbfb3831cd08ecbb0"
        const val ROOT_LAUNCHER_GUEST_PATH = "elymbot_napcat_entry.sh"
        const val GUEST_ENTRY_PATH = "elymbot_qq_guest_entry.sh"
        const val COPY_BUFFER_BYTES = 16 * 1024
        const val MAX_SECRET_FILE_BYTES = 4 * 1024L
        const val SECRET_RANDOM_BYTES = 32
        const val WEBUI_TOKEN_KEY = "NAPCAT_WEBUI_SECRET_KEY"
        const val JWT_TOKEN_KEY = "NAPCAT_WEBUI_JWT_SECRET_KEY"
        const val ONEBOT_TOKEN_KEY = "ONEBOT_WS_TOKEN"
        val KNOWN_SECRET_KEYS = setOf(WEBUI_TOKEN_KEY, JWT_TOKEN_KEY, ONEBOT_TOKEN_KEY)
        val SECRET_VALUE_PATTERN = Regex("[A-Za-z0-9_-]{16,128}")
        val SECURE_RANDOM = SecureRandom()
        const val RESOLVER_CONFIGURATION = "nameserver 223.5.5.5\nnameserver 119.29.29.29\nnameserver 8.8.8.8\n"
        const val APT_MIRROR = "http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports"
        val APT_SOURCES_CONFIGURATION = """
            deb [trusted=yes] $APT_MIRROR noble main restricted universe multiverse
            deb [trusted=yes] $APT_MIRROR noble-updates main restricted universe multiverse
            deb [trusted=yes] $APT_MIRROR noble-security main restricted universe multiverse
            deb [trusted=yes] $APT_MIRROR noble-backports main restricted universe multiverse
        """.trimIndent() + "\n"
        val APT_CONFIGURATION = """
            Acquire::Retries "2";
            Acquire::http::Timeout "20";
            Acquire::https::Timeout "20";
            Acquire::Languages "none";
            APT::Sandbox::User "root";
            Acquire::AllowInsecureRepositories "true";
            Acquire::AllowDowngradeToInsecureRepositories "true";
            APT::Get::AllowUnauthenticated "true";
        """.trimIndent() + "\n"
        val GUEST_ENTRY_SCRIPT = """
            #!/bin/bash
            set -eu
            export ELYMBOT_APP_HOME=/root/.elymbot
            export PROGRESS_FILE=/root/.elymbot/runtime/run/napcat_progress
            export PROGRESS_LABEL_FILE=/root/.elymbot/runtime/run/napcat_progress_label
            export PROGRESS_MODE_FILE=/root/.elymbot/runtime/run/napcat_progress_mode
            export INSTALLER_CACHE_FILE=/root/.elymbot/runtime/run/napcat_installer_cached
            export ELYMBOT_INSTALL_MODE=online
            export ELYMBOT_APT_MIRROR=$APT_MIRROR
            exec /bin/bash /root/$ROOT_LAUNCHER_GUEST_PATH
        """.trimIndent() + "\n"
    }
}

/** Extracts the exact ON-04 Ubuntu archive and preserves its symlink and hardlink layout. */
internal object QqRootfsInstaller {
    private const val ROOTFS_MARKER = ".elymbot_qq_rootfs_v1"

    fun ensureExtracted(archiveFile: File, rootfsDirectory: File) {
        if (isValidRootfs(rootfsDirectory)) return
        val parent = rootfsDirectory.parentFile ?: throw IOException("QQ rootfs parent is unavailable")
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) throw IOException("QQ rootfs parent is unavailable")

        val extractionDirectory = File(parent, ".qq-rootfs-${UUID.randomUUID()}")
        val backupDirectory = File(parent, ".qq-rootfs-old-${UUID.randomUUID()}")
        if (!extractionDirectory.mkdirs()) throw IOException("QQ rootfs staging directory is unavailable")
        var movedOld = false
        try {
            extractArchive(archiveFile, extractionDirectory)
            val extractedRoot = selectSourceRoot(extractionDirectory)
            if (!isValidRootfsContents(extractedRoot)) throw IOException("ON-04 Ubuntu rootfs layout is invalid")
            File(extractedRoot, ROOTFS_MARKER).writeText(ROOTFS_SHA256)

            if (rootfsDirectory.exists()) {
                Files.move(rootfsDirectory.toPath(), backupDirectory.toPath())
                movedOld = true
            }
            try {
                Files.move(extractedRoot.toPath(), rootfsDirectory.toPath())
            } catch (failure: Exception) {
                if (movedOld && !rootfsDirectory.exists()) {
                    Files.move(backupDirectory.toPath(), rootfsDirectory.toPath())
                    movedOld = false
                }
                throw failure
            }
            if (movedOld) backupDirectory.deleteRecursively()
        } finally {
            extractionDirectory.deleteRecursively()
            if (movedOld && backupDirectory.exists() && !rootfsDirectory.exists()) {
                Files.move(backupDirectory.toPath(), rootfsDirectory.toPath())
            }
        }
    }

    private fun extractArchive(archiveFile: File, destination: File) {
        if (!archiveFile.isFile || Files.isSymbolicLink(archiveFile.toPath())) throw IOException("QQ rootfs archive is unavailable")
        XZInputStream(FileInputStream(archiveFile)).use { xzInput ->
            TarArchiveInputStream(xzInput).use { tarInput ->
                var entry = tarInput.nextEntry
                while (entry != null) {
                    val outputFile = resolveEntryFile(destination, entry)
                    val outputParent = outputFile.parentFile ?: throw IOException("QQ rootfs entry has no parent")
                    when {
                        entry.isDirectory -> {
                            if (!outputFile.exists() && !outputFile.mkdirs()) throw IOException("QQ rootfs directory could not be created")
                        }
                        entry.isSymbolicLink -> {
                            ensureParentDirectory(outputParent)
                            Files.deleteIfExists(outputFile.toPath())
                            Files.createSymbolicLink(outputFile.toPath(), Paths.get(entry.linkName))
                        }
                        entry.isLink -> {
                            ensureParentDirectory(outputParent)
                            val target = resolveHardLinkTarget(destination, outputFile, entry.linkName)
                            Files.deleteIfExists(outputFile.toPath())
                            try {
                                Files.createLink(outputFile.toPath(), target.toPath())
                            } catch (_: Exception) {
                                target.copyTo(outputFile, overwrite = true)
                            }
                        }
                        else -> {
                            ensureParentDirectory(outputParent)
                            FileOutputStream(outputFile).use { output ->
                                tarInput.copyTo(output)
                                output.fd.sync()
                            }
                            outputFile.setExecutable((entry.mode and 0b001_001_001) != 0, false)
                        }
                    }
                    entry = tarInput.nextEntry
                }
            }
        }
    }

    private fun ensureParentDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("QQ rootfs parent directory could not be created")
        }
    }

    private fun isValidRootfs(rootfsDirectory: File): Boolean =
        isValidRootfsContents(rootfsDirectory) &&
            File(rootfsDirectory, ROOTFS_MARKER).takeIf(File::isFile)?.readText() == ROOTFS_SHA256

    private fun isValidRootfsContents(rootfsDirectory: File): Boolean =
        File(rootfsDirectory, "usr/bin/env").isFile &&
            File(rootfsDirectory, "usr/bin/bash").isFile &&
            File(rootfsDirectory, "usr/lib/ld-linux-aarch64.so.1").isFile &&
            Files.isSymbolicLink(File(rootfsDirectory, "bin").toPath()) &&
            Files.isSymbolicLink(File(rootfsDirectory, "lib").toPath())

    private fun selectSourceRoot(directory: File): File {
        val children = directory.listFiles().orEmpty()
        val onlyChild = children.singleOrNull()
        return if (onlyChild?.isDirectory == true) onlyChild else directory
    }

    private fun resolveEntryFile(baseDirectory: File, entry: TarArchiveEntry): File {
        val safeName = entry.name.removePrefix("./").trimStart('/')
        return resolvePath(baseDirectory, safeName)
    }

    private fun resolveHardLinkTarget(baseDirectory: File, outputFile: File, linkName: String): File {
        val target = runCatching { resolvePath(baseDirectory, linkName.removePrefix("./").trimStart('/')) }.getOrNull()
        if (target?.exists() == true) return target
        val relative = Paths.get(linkName.removePrefix("./").trimStart('/'))
        val parent = outputFile.parentFile ?: throw IOException("QQ rootfs hardlink has no parent")
        val parentTarget = parent.toPath().resolve(relative).normalize().toFile()
        if (!parentTarget.canonicalFile.toPath().startsWith(baseDirectory.canonicalFile.toPath())) {
            throw IOException("QQ rootfs hardlink target escapes its archive")
        }
        return parentTarget
    }

    private fun resolvePath(baseDirectory: File, relativePath: String): File {
        val basePath = baseDirectory.canonicalFile.toPath()
        val output = File(baseDirectory, relativePath).canonicalFile
        if (!output.toPath().startsWith(basePath)) throw IOException("QQ rootfs archive path escapes its destination")
        return output
    }

    private const val ROOTFS_SHA256 = "91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa"
}
