package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Enumeration
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** Expected release values must come from the host's authoritative publisher and release configuration. */
internal data class PluginPackageExpectation(
    val publisherId: String,
    val pluginId: String,
    val version: String,
    val hostApi: String,
    val supportedAbis: Set<String>,
    val trustedPublisherKey: PublicKey,
)

internal data class PluginPackageFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

internal data class PluginPackageManifest(
    val publisherId: String,
    val pluginId: String,
    val version: String,
    val hostApi: String,
    val abis: List<String>,
    val entryClass: String,
    val files: List<PluginPackageFile>,
)

internal data class StagedPluginPackage(
    val manifest: PluginPackageManifest,
    val directory: File,
)

internal class PluginPackageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Validates an official package and writes a new, private staging directory without activating it. */
internal class PluginPackageStager(context: Context) {
    private val appContext = context.applicationContext

    @Throws(PluginPackageException::class)
    suspend fun stage(packageFile: File, expectation: PluginPackageExpectation): StagedPluginPackage {
        val pendingStage = AtomicReference<StagedPluginPackage?>(null)
        try {
            val staged = withContext(Dispatchers.IO) {
                stageOnIo(packageFile, expectation).also(pendingStage::set)
            }
            currentCoroutineContext().ensureActive()
            pendingStage.set(null)
            return staged
        } catch (failure: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                pendingStage.getAndSet(null)?.directory?.deleteRecursively()
            }
            throw failure
        }
    }

    @Throws(PluginPackageException::class)
    suspend fun verifyInstalledPackage(
        packageDirectory: File,
        expectation: PluginPackageExpectation,
    ): StagedPluginPackage = withContext(Dispatchers.IO) {
        verifyInstalledPackageOnIo(packageDirectory, expectation)
    }

    private suspend fun verifyInstalledPackageOnIo(
        packageDirectory: File,
        expectation: PluginPackageExpectation,
    ): StagedPluginPackage {
        try {
            currentCoroutineContext().ensureActive()
            validateExpectation(expectation)
            val installedRoot = File(appContext.filesDir.canonicalFile, "$PACKAGE_ROOT_NAME/$INSTALLED_DIRECTORY_NAME")
                .canonicalFile
            val directory = packageDirectory.canonicalFile
            if (!directory.isDirectory || Files.isSymbolicLink(packageDirectory.toPath()) ||
                directory == installedRoot || !directory.toPath().startsWith(installedRoot.toPath())
            ) {
                reject("Installed plugin path is outside private storage")
            }
            val topLevelNames = directory.list()?.toSet() ?: reject("Installed plugin package cannot be read")
            if (topLevelNames != setOf(MANIFEST_ENTRY, SIGNATURE_ENTRY, PAYLOAD_DIRECTORY)) {
                reject("Installed plugin package contains unexpected entries")
            }
            val manifestFile = File(directory, MANIFEST_ENTRY)
            val signatureFile = File(directory, SIGNATURE_ENTRY)
            if (!isReadOnlyRegularFile(manifestFile) || !isReadOnlyRegularFile(signatureFile)) {
                reject("Installed plugin metadata is not immutable")
            }
            val manifestBytes = readBoundedFile(manifestFile, MAX_MANIFEST_BYTES)
            val signatureBytes = readBoundedFile(signatureFile, MAX_SIGNATURE_BYTES)
            verifySignature(manifestBytes, signatureBytes, expectation.trustedPublisherKey)
            val manifest = parseManifest(manifestBytes)
            validateManifest(manifest, expectation)

            val payloadRoot = File(directory, PAYLOAD_DIRECTORY).canonicalFile
            if (!payloadRoot.isDirectory || Files.isSymbolicLink(File(directory, PAYLOAD_DIRECTORY).toPath()) ||
                !payloadRoot.toPath().startsWith(directory.toPath())
            ) {
                reject("Installed plugin payload directory is invalid")
            }
            val actualPayloadPaths = collectPayloadPaths(payloadRoot)
            val expectedPayloadPaths = manifest.files.map { it.path }.toSet()
            if (actualPayloadPaths != expectedPayloadPaths) reject("Installed plugin files do not match the signed manifest")

            for (file in manifest.files) {
                currentCoroutineContext().ensureActive()
                val payloadFile = File(payloadRoot, file.path)
                val canonicalPayload = payloadFile.canonicalFile
                if (!isReadOnlyRegularFile(payloadFile) || !canonicalPayload.toPath().startsWith(payloadRoot.toPath()) ||
                    payloadFile.length() != file.size || digestFile(payloadFile, file.size) != file.sha256
                ) {
                    reject("Installed plugin payload integrity check failed")
                }
            }
            return StagedPluginPackage(manifest, directory)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: PluginPackageException) {
            throw failure
        } catch (failure: Exception) {
            throw PluginPackageException("Installed plugin package could not be validated", failure)
        }
    }

    private suspend fun stageOnIo(
        packageFile: File,
        expectation: PluginPackageExpectation,
    ): StagedPluginPackage {
        var stagingDirectory: File? = null
        var keepStagingDirectory = false
        try {
            currentCoroutineContext().ensureActive()
            validateExpectation(expectation)
            val source = packageFile.canonicalFile
            val archiveLength = source.length()
            val privateDataDirectory = appContext.applicationInfo.dataDir?.let { File(it) }?.canonicalFile
                ?: appContext.filesDir.parentFile?.canonicalFile
                ?: reject("App private storage is unavailable")
            if (!source.isFile || !source.canRead() || archiveLength < 1L || archiveLength > MAX_ARCHIVE_BYTES ||
                !source.toPath().startsWith(privateDataDirectory.toPath())
            ) {
                reject("Package file is unavailable or exceeds the size limit")
            }

            val result = ZipFile(source).use { archive ->
                val entries = validateArchiveEntries(archive)
                val manifestEntry = entries[MANIFEST_ENTRY] ?: reject("Package manifest is missing")
                val signatureEntry = entries[SIGNATURE_ENTRY] ?: reject("Package signature is missing")
                val manifestBytes = readEntry(archive, manifestEntry, MAX_MANIFEST_BYTES)
                val signatureBytes = readEntry(archive, signatureEntry, MAX_SIGNATURE_BYTES)
                verifySignature(manifestBytes, signatureBytes, expectation.trustedPublisherKey)

                val manifest = parseManifest(manifestBytes)
                validateManifest(manifest, expectation)
                validatePayloadEntries(entries, manifest)

                val targetDirectory = createStagingDirectory()
                stagingDirectory = targetDirectory
                writeNewFile(targetDirectory, MANIFEST_ENTRY, manifestBytes)
                writeNewFile(targetDirectory, SIGNATURE_ENTRY, signatureBytes)
                val payloadRoot = File(targetDirectory, PAYLOAD_DIRECTORY)
                createPrivateDirectory(payloadRoot, targetDirectory)

                for (file in manifest.files) {
                    currentCoroutineContext().ensureActive()
                    val entry = entries["$PAYLOAD_DIRECTORY/${file.path}"]
                        ?: reject("Package payload is incomplete")
                    copyAndVerifyPayload(archive.getInputStream(entry), payloadRoot, file)
                }
                if (manifest.files.none { it.path == PLUGIN_JAR_PATH }) {
                    reject("Plugin package must contain payload/plugin.jar")
                }
                StagedPluginPackage(manifest, targetDirectory)
            }
            keepStagingDirectory = true
            return result
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: PluginPackageException) {
            throw failure
        } catch (failure: Exception) {
            throw PluginPackageException("Plugin package could not be validated or staged", failure)
        } finally {
            if (!keepStagingDirectory) stagingDirectory?.deleteRecursively()
        }
    }

    private fun validateExpectation(expectation: PluginPackageExpectation) {
        if (!isIdentifier(expectation.publisherId) || !isIdentifier(expectation.pluginId)) {
            reject("Expected publisher or plugin identity is invalid")
        }
        if (!isToken(expectation.version) || !isToken(expectation.hostApi)) {
            reject("Expected version or host API is invalid")
        }
        if (expectation.supportedAbis.isEmpty() || expectation.supportedAbis.any { !isAbi(it) }) {
            reject("Supported ABI set is invalid")
        }
        val key = expectation.trustedPublisherKey as? RSAPublicKey
            ?: reject("Trusted publisher key is not RSA")
        if (key.modulus.bitLength() !in MIN_RSA_BITS..MAX_RSA_BITS) {
            reject("Trusted publisher key size is unsupported")
        }
    }

    private fun validateArchiveEntries(archive: ZipFile): Map<String, ZipEntry> {
        val entries = LinkedHashMap<String, ZipEntry>()
        var totalExpandedBytes = 0L
        val enumeration: Enumeration<out ZipEntry> = archive.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (entries.size >= MAX_ARCHIVE_ENTRIES) reject("Package has too many entries")
            if (entry.isDirectory || entry.name.length > MAX_ENTRY_PATH_LENGTH || !isSafeArchiveName(entry.name)) {
                reject("Package contains an invalid path")
            }
            if (entry.method != ZipEntry.DEFLATED && entry.method != ZipEntry.STORED) {
                reject("Package uses an unsupported compression method")
            }
            if (entry.size < 0 || entry.compressedSize < 0 || entry.size > MAX_ENTRY_BYTES ||
                entry.compressedSize > MAX_ARCHIVE_BYTES
            ) {
                reject("Package entry size is invalid")
            }
            if (entry.size > MAX_TOTAL_EXPANDED_BYTES - totalExpandedBytes) {
                reject("Package expanded size exceeds the limit")
            }
            totalExpandedBytes += entry.size
            if (entries.containsKey(entry.name)) reject("Package contains duplicate paths")
            entries[entry.name] = entry
        }
        if (entries.size < MIN_ARCHIVE_ENTRIES) reject("Package is incomplete")
        return entries
    }

    private fun verifySignature(manifestBytes: ByteArray, signatureBytes: ByteArray, key: PublicKey) {
        val rsaKey = key as? RSAPublicKey ?: reject("Trusted publisher key is not RSA")
        val expectedLength = (rsaKey.modulus.bitLength() + 7) / 8
        if (signatureBytes.size != expectedLength) reject("Package signature has an invalid size")
        try {
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(rsaKey)
            verifier.update(manifestBytes)
            if (!verifier.verify(signatureBytes)) reject("Package signature is invalid")
        } catch (failure: PluginPackageException) {
            throw failure
        } catch (failure: Exception) {
            throw PluginPackageException("Package signature could not be verified", failure)
        }
    }

    private fun parseManifest(bytes: ByteArray): PluginPackageManifest {
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (failure: Exception) {
            throw PluginPackageException("Package manifest is not valid UTF-8", failure)
        }

        try {
            val reader = JsonReader(StringReader(text))
            reader.isLenient = false
            reader.use {
                it.beginObject()
                val seen = HashSet<String>()
                var formatVersion: Int? = null
                var publisherId: String? = null
                var pluginId: String? = null
                var version: String? = null
                var hostApi: String? = null
                var abis: List<String>? = null
                var entryClass: String? = null
                var files: List<PluginPackageFile>? = null
                while (it.hasNext()) {
                    val name = it.nextName()
                    if (!seen.add(name)) reject("Package manifest repeats a field")
                    when (name) {
                        "formatVersion" -> {
                            val parsedVersion = readLong(it)
                            if (parsedVersion != FORMAT_VERSION.toLong()) {
                                reject("Package format version is unsupported")
                            }
                            formatVersion = parsedVersion.toInt()
                        }
                        "publisherId" -> publisherId = readString(it)
                        "pluginId" -> pluginId = readString(it)
                        "version" -> version = readString(it)
                        "hostApi" -> hostApi = readString(it)
                        "abis" -> abis = readAbis(it)
                        "entryClass" -> entryClass = readString(it)
                        "files" -> files = readFiles(it)
                        else -> reject("Package manifest contains an unknown field")
                    }
                }
                it.endObject()
                if (it.peek() != JsonToken.END_DOCUMENT) reject("Package manifest has trailing data")
                if (formatVersion != FORMAT_VERSION) reject("Package format version is unsupported")
                return PluginPackageManifest(
                    publisherId = publisherId ?: reject("Package publisher is missing"),
                    pluginId = pluginId ?: reject("Package plugin identity is missing"),
                    version = version ?: reject("Package version is missing"),
                    hostApi = hostApi ?: reject("Package host API is missing"),
                    abis = abis ?: reject("Package ABI list is missing"),
                    entryClass = entryClass ?: reject("Package entry class is missing"),
                    files = files ?: reject("Package file list is missing"),
                )
            }
        } catch (failure: PluginPackageException) {
            throw failure
        } catch (failure: Exception) {
            throw PluginPackageException("Package manifest is invalid", failure)
        }
    }

    private fun readAbis(reader: JsonReader): List<String> {
        reader.beginArray()
        val values = ArrayList<String>()
        val seen = HashSet<String>()
        while (reader.hasNext()) {
            val abi = readString(reader)
            if (!isAbi(abi) || !seen.add(abi)) reject("Package ABI list is invalid")
            values += abi
            if (values.size > MAX_ABIS) reject("Package ABI list is too large")
        }
        reader.endArray()
        if (values.isEmpty() || values != values.sorted()) reject("Package ABI list must be non-empty and sorted")
        return values
    }

    private fun readFiles(reader: JsonReader): List<PluginPackageFile> {
        reader.beginArray()
        val files = ArrayList<PluginPackageFile>()
        val seen = HashSet<String>()
        var totalSize = 0L
        while (reader.hasNext()) {
            if (files.size >= MAX_PAYLOAD_FILES) reject("Package file list is too large")
            reader.beginObject()
            val fileFields = HashSet<String>()
            var path: String? = null
            var size: Long? = null
            var sha256: String? = null
            while (reader.hasNext()) {
                val field = reader.nextName()
                if (!fileFields.add(field)) reject("Package file entry repeats a field")
                when (field) {
                    "path" -> path = readString(reader)
                    "size" -> size = readLong(reader)
                    "sha256" -> sha256 = readString(reader)
                    else -> reject("Package file entry has an unknown field")
                }
            }
            reader.endObject()
            val safePath = path ?: reject("Package file path is missing")
            val fileSize = size ?: reject("Package file size is missing")
            val digest = sha256 ?: reject("Package file digest is missing")
            if (!isSafePayloadPath(safePath) || !seen.add(safePath)) reject("Package file path is invalid")
            if (fileSize < 0 || fileSize > MAX_ENTRY_BYTES || fileSize > MAX_TOTAL_EXPANDED_BYTES - totalSize) {
                reject("Package file size is invalid")
            }
            if (!SHA256_PATTERN.matches(digest)) reject("Package file digest is invalid")
            totalSize += fileSize
            files += PluginPackageFile(safePath, fileSize, digest)
        }
        reader.endArray()
        if (files.isEmpty() || files.map { it.path } != files.map { it.path }.sorted()) {
            reject("Package file list must be non-empty and sorted by path")
        }
        return files
    }

    private fun validateManifest(manifest: PluginPackageManifest, expectation: PluginPackageExpectation) {
        if (!isIdentifier(manifest.publisherId) || !isIdentifier(manifest.pluginId) ||
            !isToken(manifest.version) || !isToken(manifest.hostApi) || !isPluginEntryClass(manifest.entryClass)
        ) {
            reject("Package manifest identity is invalid")
        }
        if (manifest.publisherId != expectation.publisherId || manifest.pluginId != expectation.pluginId ||
            manifest.version != expectation.version
        ) {
            reject("Package identity or version does not match the requested release")
        }
        if (manifest.hostApi != expectation.hostApi) reject("Package is incompatible with the host API")
        if (manifest.abis.none(expectation.supportedAbis::contains)) {
            reject("Package is incompatible with this device ABI")
        }
        if (manifest.files.none { it.path == PLUGIN_JAR_PATH }) {
            reject("Plugin package must contain payload/plugin.jar")
        }
    }

    private fun validatePayloadEntries(entries: Map<String, ZipEntry>, manifest: PluginPackageManifest) {
        val expectedEntries = buildSet {
            add(MANIFEST_ENTRY)
            add(SIGNATURE_ENTRY)
            manifest.files.forEach { add("$PAYLOAD_DIRECTORY/${it.path}") }
        }
        if (entries.keys != expectedEntries) reject("Package entry set does not match its manifest")
        for (file in manifest.files) {
            val entry = entries["$PAYLOAD_DIRECTORY/${file.path}"] ?: reject("Package payload is missing")
            if (entry.size != file.size) reject("Package payload size does not match its manifest")
        }
    }

    private fun createStagingDirectory(): File {
        val filesDirectory = appContext.filesDir.canonicalFile
        val packageRoot = File(filesDirectory, PACKAGE_ROOT_NAME)
        createPrivateDirectory(packageRoot, filesDirectory)
        val stagingRoot = File(packageRoot, STAGING_DIRECTORY_NAME)
        createPrivateDirectory(stagingRoot, filesDirectory)
        return Files.createTempDirectory(stagingRoot.toPath(), STAGING_PREFIX).toFile().canonicalFile
    }

    private fun createPrivateDirectory(directory: File, allowedParent: File) {
        val canonicalParent = allowedParent.canonicalFile
        val target = directory.absoluteFile
        if (!target.exists() && !target.mkdir() && !target.isDirectory) {
            throw IOException("Unable to create private package directory")
        }
        if (!target.isDirectory || Files.isSymbolicLink(target.toPath())) {
            reject("Package staging path is not a regular directory")
        }
        val canonicalTarget = target.canonicalFile
        if (!canonicalTarget.toPath().startsWith(canonicalParent.toPath())) {
            reject("Package staging path escapes private storage")
        }
    }

    private fun writeNewFile(root: File, relativePath: String, bytes: ByteArray) {
        val destination = resolveNewPayloadFile(root, relativePath)
        Files.newOutputStream(
            destination.toPath(),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { it.write(bytes) }
        requireReadOnly(destination)
    }

    private suspend fun copyAndVerifyPayload(input: InputStream, payloadRoot: File, file: PluginPackageFile) {
        input.use { source ->
            val destination = resolveNewPayloadFile(payloadRoot, file.path)
            val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
            var copied = 0L
            Files.newOutputStream(
                destination.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            ).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > file.size || copied > MAX_ENTRY_BYTES) reject("Package payload exceeds its declared size")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            if (copied != file.size || digest.digest().toHex() != file.sha256) {
                reject("Package payload integrity check failed")
            }
            requireReadOnly(destination)
        }
    }

    private fun requireReadOnly(file: File) {
        if (!file.setReadOnly() || file.canWrite()) reject("Verified package content could not be made read-only")
    }

    private fun isReadOnlyRegularFile(file: File): Boolean =
        file.isFile && file.canRead() && !file.canWrite() && !Files.isSymbolicLink(file.toPath())

    private suspend fun collectPayloadPaths(payloadRoot: File): Set<String> {
        val paths = LinkedHashSet<String>()
        var totalBytes = 0L
        fun visit(directory: File, prefix: String) {
            val children = directory.listFiles() ?: reject("Installed plugin payload cannot be read")
            if (children.isEmpty()) reject("Installed plugin payload contains an empty directory")
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) reject("Installed plugin payload contains a symbolic link")
                val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (!isSafePayloadPath(relative)) reject("Installed plugin payload path is invalid")
                when {
                    child.isDirectory -> visit(child, relative)
                    child.isFile -> {
                        if (!paths.add(relative) || paths.size > MAX_PAYLOAD_FILES) {
                            reject("Installed plugin payload has too many files")
                        }
                        val size = child.length()
                        if (size > MAX_ENTRY_BYTES || size > MAX_TOTAL_EXPANDED_BYTES - totalBytes) {
                            reject("Installed plugin payload exceeds the size limit")
                        }
                        totalBytes += size
                    }
                    else -> reject("Installed plugin payload contains a non-regular file")
                }
            }
        }
        visit(payloadRoot, "")
        return paths
    }

    private suspend fun readBoundedFile(file: File, maxBytes: Long): ByteArray {
        if (file.length() < 0L || file.length() > maxBytes || file.length() > Int.MAX_VALUE) {
            reject("Installed plugin metadata exceeds the size limit")
        }
        val output = ByteArrayOutputStream(file.length().toInt())
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var copied = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                if (copied > maxBytes) reject("Installed plugin metadata exceeds the size limit")
                output.write(buffer, 0, count)
            }
            if (copied != file.length()) reject("Installed plugin metadata changed while reading")
        }
        return output.toByteArray()
    }

    private suspend fun digestFile(file: File, expectedSize: Long): String {
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var copied = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                if (copied > expectedSize || copied > MAX_ENTRY_BYTES) reject("Installed plugin payload exceeds its declared size")
                digest.update(buffer, 0, count)
            }
            if (copied != expectedSize) reject("Installed plugin payload size does not match its manifest")
        }
        return digest.digest().toHex()
    }

    private fun resolveNewPayloadFile(root: File, relativePath: String): File {
        if (!isSafePayloadPath(relativePath)) reject("Package payload path is invalid")
        var parent = root.canonicalFile
        for (segment in relativePath.split('/').dropLast(1)) {
            val child = File(parent, segment)
            createPrivateDirectory(child, root)
            parent = child.canonicalFile
        }
        val destination = File(parent, relativePath.substringAfterLast('/')).absoluteFile
        val canonicalRoot = root.canonicalFile
        val canonicalDestination = destination.canonicalFile
        if (!canonicalDestination.toPath().startsWith(canonicalRoot.toPath()) ||
            Files.isSymbolicLink(destination.toPath()) || destination.exists()
        ) {
            reject("Package payload path is unsafe")
        }
        return destination
    }

    private fun readEntry(archive: ZipFile, entry: ZipEntry, maxBytes: Long): ByteArray {
        if (entry.size < 0L || entry.size > maxBytes || entry.size > Int.MAX_VALUE) {
            reject("Package metadata size is invalid")
        }
        val output = ByteArrayOutputStream(entry.size.toInt())
        archive.getInputStream(entry).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var copied = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                if (copied > maxBytes) reject("Package metadata exceeds the size limit")
                output.write(buffer, 0, count)
            }
            if (copied != entry.size) reject("Package metadata size does not match its archive entry")
        }
        return output.toByteArray()
    }

    private fun isSafeArchiveName(name: String): Boolean = when (name) {
        MANIFEST_ENTRY, SIGNATURE_ENTRY -> true
        else -> name.startsWith("$PAYLOAD_DIRECTORY/") && isSafePayloadPath(name.removePrefix("$PAYLOAD_DIRECTORY/"))
    }

    private fun isSafePayloadPath(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_ENTRY_PATH_LENGTH || !PORTABLE_PATH_PATTERN.matches(path)) return false
        val segments = path.split('/')
        return segments.all { it.isNotEmpty() && it != "." && it != ".." }
    }

    private fun isIdentifier(value: String): Boolean = IDENTIFIER_PATTERN.matches(value)
    private fun isToken(value: String): Boolean = TOKEN_PATTERN.matches(value)
    private fun isAbi(value: String): Boolean = ABI_PATTERN.matches(value)
    private fun isPluginEntryClass(value: String): Boolean =
        PLUGIN_ENTRY_CLASS_PATTERN.matches(value) &&
            !value.startsWith("java.") &&
            !value.startsWith("android.") &&
            !value.startsWith("com.elymbot.core.")

    private fun readString(reader: JsonReader): String {
        if (reader.peek() != JsonToken.STRING) reject("Package manifest field has the wrong type")
        return reader.nextString()
    }

    private fun readLong(reader: JsonReader): Long {
        if (reader.peek() != JsonToken.NUMBER) reject("Package manifest number has the wrong type")
        return reader.nextLong()
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val result = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            result[index * 2] = digits[value ushr 4]
            result[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(result)
    }

    private fun reject(message: String): Nothing = throw PluginPackageException(message)

    private companion object {
        const val FORMAT_VERSION = 1
        const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        const val SHA256_ALGORITHM = "SHA-256"
        const val MANIFEST_ENTRY = "manifest.json"
        const val SIGNATURE_ENTRY = "signature.bin"
        const val PAYLOAD_DIRECTORY = "payload"
        const val PLUGIN_JAR_PATH = "plugin.jar"
        const val PACKAGE_ROOT_NAME = "plugin-packages"
        const val INSTALLED_DIRECTORY_NAME = "installed"
        const val STAGING_DIRECTORY_NAME = "staging"
        const val STAGING_PREFIX = "verified-"
        const val MIN_ARCHIVE_ENTRIES = 3
        const val MAX_ARCHIVE_ENTRIES = 258
        const val MAX_PAYLOAD_FILES = 256
        const val MAX_ABIS = 16
        const val MAX_ENTRY_PATH_LENGTH = 240
        const val MAX_MANIFEST_BYTES = 64 * 1024L
        const val MAX_SIGNATURE_BYTES = 512L
        const val MAX_ENTRY_BYTES = 64 * 1024 * 1024L
        const val MAX_TOTAL_EXPANDED_BYTES = 128 * 1024 * 1024L
        const val MAX_ARCHIVE_BYTES = 128 * 1024 * 1024L
        const val MIN_RSA_BITS = 2048
        const val MAX_RSA_BITS = 4096
        const val COPY_BUFFER_BYTES = 16 * 1024
        val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val TOKEN_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")
        val ABI_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,39}")
        val PLUGIN_ENTRY_CLASS_PATTERN = Regex("""[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+""")
        val PORTABLE_PATH_PATTERN = Regex("[A-Za-z0-9._/-]+")
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
