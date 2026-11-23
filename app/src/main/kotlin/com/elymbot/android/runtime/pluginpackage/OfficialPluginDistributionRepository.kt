package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class PluginDistributionConfiguration(
    val trustedDirectoryKey: PublicKey,
    val hostApi: String,
    val supportedAbis: Set<String>,
    val githubLatestReleaseApiUrl: String = OFFICIAL_LATEST_RELEASE_API,
    internal val allowInsecureLocalhostForTests: Boolean = false,
) {
    init {
        require(trustedDirectoryKey is RSAPublicKey)
        require(trustedDirectoryKey.modulus.bitLength() in MIN_RSA_BITS..MAX_RSA_BITS)
        require(hostApi.matches(TOKEN_PATTERN))
        require(supportedAbis.isNotEmpty() && supportedAbis.all(ABI_PATTERN::matches))
        require(isAllowedUrl(githubLatestReleaseApiUrl, allowInsecureLocalhostForTests, DistributionSourceKind.GITHUB_API))
        require(allowInsecureLocalhostForTests || githubLatestReleaseApiUrl == OFFICIAL_LATEST_RELEASE_API)
    }

    internal companion object {
        const val OFFICIAL_LATEST_RELEASE_API =
            "https://api.github.com/repos/undertaker33/ElymBot/releases/latest"
        const val MIN_RSA_BITS = 2048
        const val MAX_RSA_BITS = 4096
        val TOKEN_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")
        val ABI_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,39}")
    }
}

internal enum class PluginDirectoryStatus {
    NOT_CONFIGURED,
    READY,
    DIRECTORY_MISSING,
    NETWORK_ERROR,
    INVALID_DIRECTORY,
}

internal enum class PluginDirectoryFailure {
    TRANSFER,
    INVALID_METADATA,
    PRIVATE_STORAGE,
    INTERNAL,
}

internal data class PluginDirectorySnapshot(
    val status: PluginDirectoryStatus,
    val releases: List<PluginReleaseSummary> = emptyList(),
    val usingLastGoodDirectory: Boolean = false,
    val failure: PluginDirectoryFailure? = null,
)

private enum class DistributionSourceKind { GITHUB_API, GITHUB, GITEE }

internal data class PluginReleaseSummary(
    val pluginId: String,
    val publisherId: String,
    val version: String,
    val compatible: Boolean,
)

internal class VerifiedPluginDownload internal constructor(
    val packageFile: File,
) : Closeable {
    override fun close() {
        packageFile.delete()
    }
}

/** Reads only a root-signed directory from ElymBot's official GitHub release and downloads its exact artifacts. */
internal class OfficialPluginDistributionRepository(
    context: Context,
    private val configuration: PluginDistributionConfiguration?,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var currentDirectory: VerifiedDirectory? = null

    suspend fun refreshPluginDirectory(): PluginDirectorySnapshot {
        val config = configuration ?: return PluginDirectorySnapshot(PluginDirectoryStatus.NOT_CONFIGURED)
        return withContext(Dispatchers.IO) {
            currentDirectory = readLastGoodDirectory(config) ?: currentDirectory
            try {
                val release = readLatestRelease(config)
                val directoryAsset = release.assets[DIRECTORY_ASSET_NAME]
                    ?: return@withContext snapshot(PluginDirectoryStatus.DIRECTORY_MISSING, usingCache = currentDirectory != null)
                val signatureAsset = release.assets[DIRECTORY_SIGNATURE_ASSET_NAME]
                    ?: return@withContext snapshot(PluginDirectoryStatus.DIRECTORY_MISSING, usingCache = currentDirectory != null)
                validateApiAsset(directoryAsset, MAX_DIRECTORY_BYTES)
                validateApiAsset(signatureAsset, MAX_SIGNATURE_BYTES)
                val rawDirectory = fetchBytes(directoryAsset.downloadUrl, directoryAsset.size, MAX_DIRECTORY_BYTES, config, DistributionSourceKind.GITHUB)
                val signature = fetchBytes(signatureAsset.downloadUrl, signatureAsset.size, MAX_SIGNATURE_BYTES, config, DistributionSourceKind.GITHUB)
                checkApiDigest(directoryAsset, rawDirectory)
                checkApiDigest(signatureAsset, signature)
                verifyRootSignature(rawDirectory, signature, config.trustedDirectoryKey)
                val parsed = parseDirectory(rawDirectory, config)
                persistLastGoodDirectory(rawDirectory, signature)
                val verified = VerifiedDirectory(parsed)
                currentDirectory = verified
                snapshot(PluginDirectoryStatus.READY, usingCache = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: TransferFailure) {
                snapshot(PluginDirectoryStatus.NETWORK_ERROR, usingCache = currentDirectory != null, failure = PluginDirectoryFailure.TRANSFER)
            } catch (_: InvalidDistribution) {
                snapshot(PluginDirectoryStatus.INVALID_DIRECTORY, usingCache = currentDirectory != null, failure = PluginDirectoryFailure.INVALID_METADATA)
            } catch (_: IOException) {
                snapshot(PluginDirectoryStatus.NETWORK_ERROR, usingCache = currentDirectory != null, failure = PluginDirectoryFailure.PRIVATE_STORAGE)
            } catch (_: Exception) {
                snapshot(PluginDirectoryStatus.NETWORK_ERROR, usingCache = currentDirectory != null, failure = PluginDirectoryFailure.INTERNAL)
            }
        }
    }

    suspend fun resolveExpectation(reference: PluginPackageReference): PluginPackageExpectation? {
        val config = configuration ?: return null
        val directory = currentDirectory ?: withContext(Dispatchers.IO) { readLastGoodDirectory(config) }
            ?.also { currentDirectory = it }
            ?: return null
        val release = directory.plugins.singleOrNull {
            it.pluginId == reference.pluginId && it.publisherId == reference.publisherId && it.version == reference.version
        } ?: return null
        if (!isCompatible(release, config)) return null
        return PluginPackageExpectation(
            publisherId = release.publisherId,
            pluginId = release.pluginId,
            version = release.version,
            hostApi = release.hostApi,
            supportedAbis = config.supportedAbis,
            trustedPublisherKey = release.publisherKey,
        )
    }

    suspend fun downloadPlugin(reference: PluginPackageReference): VerifiedPluginDownload {
        val config = configuration ?: throw PluginDistributionException("Plugin directory trust is not configured")
        val directory = currentDirectory ?: withContext(Dispatchers.IO) { readLastGoodDirectory(config) }
            ?.also { currentDirectory = it }
            ?: throw PluginDistributionException("No verified plugin directory is available")
        val release = directory.plugins.singleOrNull {
            it.pluginId == reference.pluginId && it.publisherId == reference.publisherId && it.version == reference.version
        } ?: throw PluginDistributionException("Requested plugin release is not in the verified directory")
        if (!isCompatible(release, config)) throw PluginDistributionException("Plugin release is not compatible with this host")

        val pendingFile = AtomicReference<File?>(null)
        return try {
            val file = withContext(Dispatchers.IO) {
                val output = createDownloadFile()
                pendingFile.set(output)
                try {
                    try {
                        downloadAndVerify(release, release.githubUrl, DistributionSourceKind.GITHUB, output, config)
                    } catch (failure: TransferFailure) {
                        val mirror = release.giteeUrl ?: throw failure
                        downloadAndVerify(release, mirror, DistributionSourceKind.GITEE, output, config)
                    }
                    output
                } catch (failure: Throwable) {
                    output.delete()
                    pendingFile.set(null)
                    throw failure
                }
            }
            currentCoroutineContext().ensureActive()
            pendingFile.set(null)
            VerifiedPluginDownload(file)
        } catch (failure: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { pendingFile.getAndSet(null)?.delete() }
            throw failure
        }
    }

    private suspend fun readLatestRelease(config: PluginDistributionConfiguration): GithubRelease {
        val bytes = requestBytes(config.githubLatestReleaseApiUrl, MAX_RELEASE_API_BYTES, config, DistributionSourceKind.GITHUB_API)
        return parseGithubRelease(bytes, config)
    }

    private fun parseGithubRelease(bytes: ByteArray, config: PluginDistributionConfiguration): GithubRelease {
        val assets = LinkedHashMap<String, GithubReleaseAsset>()
        var foundAssets = false
        JsonReader(StringReader(bytes.toString(Charsets.UTF_8))).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "assets" -> {
                        if (foundAssets) invalid("Release response repeats assets")
                        foundAssets = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val asset = parseReleaseAsset(reader, config)
                            if (assets.put(asset.name, asset) != null) invalid("Release response repeats an asset name")
                            if (assets.size > MAX_RELEASE_ASSETS) invalid("Release response has too many assets")
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        if (!foundAssets) invalid("Release response has no assets list")
        return GithubRelease(assets)
    }

    private fun parseReleaseAsset(reader: JsonReader, config: PluginDistributionConfiguration): GithubReleaseAsset {
        var name: String? = null
        var size: Long? = null
        var digest: String? = null
        var downloadUrl: String? = null
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            if (!seen.add(field)) invalid("Release asset repeats a field")
            when (field) {
                "name" -> name = readString(reader)
                "size" -> size = readLong(reader)
                "digest" -> digest = readOptionalString(reader)
                "browser_download_url" -> downloadUrl = readString(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val assetName = name ?: invalid("Release asset name is missing")
        val assetSize = size ?: invalid("Release asset size is missing")
        val assetUrl = downloadUrl ?: invalid("Release asset URL is missing")
        if (!ASSET_NAME_PATTERN.matches(assetName) || assetSize < 0 || !isAllowedUrl(assetUrl, config.allowInsecureLocalhostForTests, DistributionSourceKind.GITHUB)) {
            invalid("Release asset metadata is invalid")
        }
        val normalizedDigest = digest?.removePrefix("sha256:")?.lowercase(Locale.ROOT)
        if (normalizedDigest != null && !SHA256_PATTERN.matches(normalizedDigest)) invalid("Release asset digest is invalid")
        return GithubReleaseAsset(assetName, assetSize, normalizedDigest, assetUrl)
    }

    private fun parseDirectory(bytes: ByteArray, config: PluginDistributionConfiguration): List<DirectoryPluginRelease> {
        val plugins = mutableListOf<DirectoryPluginRelease>()
        val identities = mutableSetOf<String>()
        var formatVersion: Long? = null
        var hasPlugins = false
        JsonReader(StringReader(bytes.toString(Charsets.UTF_8))).use { reader ->
            reader.beginObject()
            val seenFields = mutableSetOf<String>()
            while (reader.hasNext()) {
                val field = reader.nextName()
                if (!seenFields.add(field)) invalid("Directory repeats a field")
                when (field) {
                    "formatVersion" -> formatVersion = readLong(reader)
                    "plugins" -> {
                        hasPlugins = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            plugins += parsePluginRelease(reader, config)
                            if (plugins.size > MAX_DIRECTORY_PLUGINS) invalid("Directory has too many plugin releases")
                        }
                        reader.endArray()
                    }
                    else -> invalid("Directory contains an unknown field")
                }
            }
            reader.endObject()
        }
        if (formatVersion != DIRECTORY_FORMAT_VERSION.toLong() || !hasPlugins || plugins.isEmpty()) {
            invalid("Directory format or plugin list is invalid")
        }
        for (release in plugins) {
            val identity = "${release.publisherId}\u0000${release.pluginId}\u0000${release.version}"
            if (!identities.add(identity)) invalid("Directory repeats a plugin release")
        }
        val ordered = plugins.sortedWith(compareBy(DirectoryPluginRelease::publisherId, DirectoryPluginRelease::pluginId, DirectoryPluginRelease::version))
        if (plugins != ordered) invalid("Directory plugin releases are not in deterministic order")
        return plugins.toList()
    }

    private fun parsePluginRelease(reader: JsonReader, config: PluginDistributionConfiguration): DirectoryPluginRelease {
        var publisherId: String? = null
        var pluginId: String? = null
        var version: String? = null
        var hostApi: String? = null
        var abis: Set<String>? = null
        var publisherKey: PublicKey? = null
        var size: Long? = null
        var sha256: String? = null
        var githubUrl: String? = null
        var giteeUrl: String? = null
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            if (!seen.add(field)) invalid("Plugin release repeats a field")
            when (field) {
                "publisherId" -> publisherId = readString(reader)
                "pluginId" -> pluginId = readString(reader)
                "version" -> version = readString(reader)
                "hostApi" -> hostApi = readString(reader)
                "abis" -> abis = readStringSet(reader, MAX_DIRECTORY_ABIS)
                "publisherPublicKey" -> publisherKey = parsePublisherKey(readString(reader))
                "package" -> {
                    reader.beginObject()
                    val packageFields = mutableSetOf<String>()
                    while (reader.hasNext()) {
                        val packageField = reader.nextName()
                        if (!packageFields.add(packageField)) invalid("Plugin package repeats a field")
                        when (packageField) {
                            "size" -> size = readLong(reader)
                            "sha256" -> sha256 = readString(reader)
                            "githubUrl" -> githubUrl = readString(reader)
                            "giteeUrl" -> giteeUrl = readOptionalString(reader)
                            else -> invalid("Plugin package contains an unknown field")
                        }
                    }
                    reader.endObject()
                }
                else -> invalid("Plugin release contains an unknown field")
            }
        }
        reader.endObject()
        val publisher = publisherId ?: invalid("Plugin publisher is missing")
        val plugin = pluginId ?: invalid("Plugin id is missing")
        val releaseVersion = version ?: invalid("Plugin version is missing")
        val api = hostApi ?: invalid("Plugin host API is missing")
        val supportedAbis = abis ?: invalid("Plugin ABI list is missing")
        val key = publisherKey ?: invalid("Plugin signing key is missing")
        val packageSize = size ?: invalid("Plugin package size is missing")
        val digest = sha256 ?: invalid("Plugin package digest is missing")
        val primaryUrl = githubUrl ?: invalid("Plugin package GitHub URL is missing")
        if (!IDENTIFIER_PATTERN.matches(publisher) || !IDENTIFIER_PATTERN.matches(plugin) ||
            !TOKEN_PATTERN.matches(releaseVersion) || !TOKEN_PATTERN.matches(api) ||
            supportedAbis.isEmpty() || supportedAbis.any { !ABI_PATTERN.matches(it) } ||
            packageSize !in 1..MAX_PLUGIN_PACKAGE_BYTES || !SHA256_PATTERN.matches(digest) ||
            !isAllowedUrl(primaryUrl, config.allowInsecureLocalhostForTests, DistributionSourceKind.GITHUB) ||
            (giteeUrl != null && !isAllowedUrl(giteeUrl, config.allowInsecureLocalhostForTests, DistributionSourceKind.GITEE))
        ) {
            invalid("Plugin release metadata is invalid")
        }
        return DirectoryPluginRelease(
            publisherId = publisher,
            pluginId = plugin,
            version = releaseVersion,
            hostApi = api,
            abis = supportedAbis,
            publisherKey = key,
            packageSize = packageSize,
            packageSha256 = digest.lowercase(Locale.ROOT),
            githubUrl = primaryUrl,
            giteeUrl = giteeUrl,
        )
    }

    private suspend fun downloadAndVerify(
        release: DirectoryPluginRelease,
        url: String,
        source: DistributionSourceKind,
        destination: File,
        config: PluginDistributionConfiguration,
    ) {
        if (!isAllowedUrl(url, config.allowInsecureLocalhostForTests, source)) invalid("Plugin download URL is not allowed")
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
        val copied = streamToFile(url, release.packageSize, MAX_PLUGIN_PACKAGE_BYTES, destination, config, source, digest)
        if (copied != release.packageSize || digest.digest().toHex() != release.packageSha256) {
            destination.delete()
            invalid("Downloaded plugin package failed its signed size or digest check")
        }
        destination.setReadOnly()
        if (destination.canWrite()) invalid("Verified plugin download could not be made read-only")
    }

    private suspend fun fetchBytes(
        url: String,
        expectedSize: Long,
        maxBytes: Long,
        config: PluginDistributionConfiguration,
        source: DistributionSourceKind,
    ): ByteArray {
        if (expectedSize !in 0..maxBytes || expectedSize > Int.MAX_VALUE) invalid("Release asset size is invalid")
        val result = ByteArrayOutputStream(expectedSize.toInt())
        val copied = streamToOutput(url, expectedSize, maxBytes, result, config, source)
        if (copied != expectedSize) invalid("Release asset size did not match GitHub metadata")
        return result.toByteArray()
    }

    private suspend fun requestBytes(url: String, maxBytes: Long, config: PluginDistributionConfiguration, source: DistributionSourceKind): ByteArray {
        val output = ByteArrayOutputStream()
        streamToOutput(url, null, maxBytes, output, config, source)
        return output.toByteArray()
    }

    private suspend fun streamToFile(
        initialUrl: String,
        expectedSize: Long?,
        maxBytes: Long,
        destination: File,
        config: PluginDistributionConfiguration,
        source: DistributionSourceKind,
        digest: MessageDigest,
    ): Long = FileOutputStream(destination).use { output ->
        stream(initialUrl, expectedSize, maxBytes, config, source) { buffer, count ->
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }

    private suspend fun streamToOutput(
        initialUrl: String,
        expectedSize: Long?,
        maxBytes: Long,
        output: ByteArrayOutputStream,
        config: PluginDistributionConfiguration,
        source: DistributionSourceKind,
    ): Long = stream(initialUrl, expectedSize, maxBytes, config, source) { buffer, count -> output.write(buffer, 0, count) }

    private suspend fun stream(
        initialUrl: String,
        expectedSize: Long?,
        maxBytes: Long,
        config: PluginDistributionConfiguration,
        source: DistributionSourceKind,
        consume: (ByteArray, Int) -> Unit,
    ): Long {
        var current = URI(initialUrl)
        var redirectCount = 0
        while (true) {
            if (!isAllowedUrl(current.toString(), config.allowInsecureLocalhostForTests, source)) {
                throw TransferFailure("Distribution host is not allowed")
            }
            val connection = (URL(current.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", if (source == DistributionSourceKind.GITHUB_API) "application/vnd.github+json" else "application/octet-stream")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ElymBot-Android")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    if (++redirectCount > MAX_REDIRECTS) throw TransferFailure("Too many distribution redirects")
                    val location = connection.getHeaderField("Location") ?: throw TransferFailure("Distribution redirect has no location")
                    current = current.resolve(location)
                    continue
                }
                if (status !in 200..299) throw TransferFailure("Distribution request failed with HTTP $status")
                val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                if (declaredLength > maxBytes || (expectedSize != null && declaredLength >= 0 && declaredLength != expectedSize)) {
                    invalid("Distribution response size is invalid")
                }
                var copied = 0L
                connection.inputStream.use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > maxBytes || (expectedSize != null && copied > expectedSize)) {
                            invalid("Distribution response exceeds its declared size")
                        }
                        consume(buffer, count)
                    }
                }
                return copied
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: InvalidDistribution) {
                throw failure
            } catch (failure: TransferFailure) {
                throw failure
            } catch (failure: IOException) {
                throw TransferFailure("Distribution transfer failed", failure)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun validateApiAsset(asset: GithubReleaseAsset, maximumSize: Long) {
        if (asset.size !in 1..maximumSize || asset.digest == null) invalid("GitHub release asset is missing a valid size or SHA-256")
    }

    private fun checkApiDigest(asset: GithubReleaseAsset, bytes: ByteArray) {
        val digest = asset.digest ?: invalid("GitHub release asset SHA-256 is missing")
        if (bytes.size.toLong() != asset.size || sha256(bytes) != digest) invalid("GitHub release asset failed its API digest check")
    }

    private fun verifyRootSignature(bytes: ByteArray, signatureBytes: ByteArray, key: PublicKey) {
        if (signatureBytes.isEmpty() || signatureBytes.size > MAX_SIGNATURE_BYTES) invalid("Directory signature size is invalid")
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(key)
        verifier.update(bytes)
        if (!verifier.verify(signatureBytes)) invalid("Directory signature is invalid")
    }

    private fun parsePublisherKey(encodedKey: String): PublicKey {
        val encoded = try {
            Base64.getDecoder().decode(encodedKey)
        } catch (failure: IllegalArgumentException) {
            throw InvalidDistribution("Plugin signing key is not valid base64", failure)
        }
        if (encoded.size > MAX_PUBLISHER_KEY_BYTES) invalid("Plugin signing key is too large")
        val key = try {
            java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(encoded))
        } catch (failure: Exception) {
            throw InvalidDistribution("Plugin signing key is invalid", failure)
        }
        if (
            key !is RSAPublicKey ||
            key.modulus.bitLength() !in PluginDistributionConfiguration.MIN_RSA_BITS..PluginDistributionConfiguration.MAX_RSA_BITS
        ) {
            invalid("Plugin signing key size is invalid")
        }
        return key
    }

    private fun readLastGoodDirectory(config: PluginDistributionConfiguration): VerifiedDirectory? {
        val file = directoryCacheFile() ?: return null
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) || file.length() !in 1..MAX_CACHE_BYTES) return null
        return try {
            DataInputStream(FileInputStream(file)).use { input ->
                if (input.readInt() != CACHE_MAGIC || input.readInt() != DIRECTORY_FORMAT_VERSION) return null
                val rawLength = input.readInt()
                val signatureLength = input.readInt()
                if (rawLength !in 1..MAX_DIRECTORY_BYTES || signatureLength !in 1..MAX_SIGNATURE_BYTES) return null
                val raw = ByteArray(rawLength)
                val signature = ByteArray(signatureLength)
                input.readFully(raw)
                input.readFully(signature)
                if (input.read() != -1) return null
                verifyRootSignature(raw, signature, config.trustedDirectoryKey)
                VerifiedDirectory(parseDirectory(raw, config))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun persistLastGoodDirectory(rawDirectory: ByteArray, signature: ByteArray) {
        val directory = cacheDirectory() ?: throw IOException("Private plugin cache is unavailable")
        val destination = File(directory, DIRECTORY_CACHE_FILE)
        val temporary = File.createTempFile("directory-", ".pending", directory)
        try {
            val fileOutput = FileOutputStream(temporary)
            DataOutputStream(fileOutput).use { output ->
                output.writeInt(CACHE_MAGIC)
                output.writeInt(DIRECTORY_FORMAT_VERSION)
                output.writeInt(rawDirectory.size)
                output.writeInt(signature.size)
                output.write(rawDirectory)
                output.write(signature)
                output.flush()
                fileOutput.fd.sync()
            }
            if (!temporary.setReadable(false, false) || !temporary.setReadable(true, true) || !temporary.setWritable(false, false) ||
                !temporary.setWritable(true, true)
            ) throw IOException("Private plugin cache permissions could not be applied")
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    private fun directoryCacheFile(): File? = cacheDirectory()?.let { File(it, DIRECTORY_CACHE_FILE) }

    private fun cacheDirectory(): File? {
        val parent = appContext.filesDir.canonicalFile
        val directory = File(parent, DIRECTORY_CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdir() && !directory.isDirectory) return null
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath()) || !directory.canonicalFile.toPath().startsWith(parent.toPath())) return null
        return directory.canonicalFile
    }

    private fun createDownloadFile(): File {
        val root = appContext.cacheDir.canonicalFile
        val directory = File(root, PLUGIN_DOWNLOAD_DIRECTORY)
        if (!directory.exists() && !directory.mkdir() && !directory.isDirectory) throw IOException("Private plugin download directory is unavailable")
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath()) || !directory.canonicalFile.toPath().startsWith(root.toPath())) {
            throw IOException("Private plugin download path is unsafe")
        }
        return Files.createTempFile(directory.toPath(), "verified-plugin-", ".elymplug").toFile()
    }

    private fun snapshot(
        status: PluginDirectoryStatus,
        usingCache: Boolean,
        failure: PluginDirectoryFailure? = null,
    ): PluginDirectorySnapshot {
        val current = currentDirectory
        return PluginDirectorySnapshot(
            status = status,
            releases = current?.plugins?.map { release ->
                PluginReleaseSummary(release.pluginId, release.publisherId, release.version, configuration?.let { isCompatible(release, it) } == true)
            }.orEmpty(),
            usingLastGoodDirectory = usingCache,
            failure = failure,
        )
    }

    private fun isCompatible(release: DirectoryPluginRelease, config: PluginDistributionConfiguration): Boolean =
        release.hostApi == config.hostApi && release.abis.any(config.supportedAbis::contains)

    private fun readStringSet(reader: JsonReader, maximumCount: Int): Set<String> {
        val values = LinkedHashSet<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (values.size >= maximumCount) invalid("Directory list has too many entries")
            val value = readString(reader)
            if (!values.add(value)) invalid("Directory list contains a duplicate")
        }
        reader.endArray()
        return values
    }

    private fun readString(reader: JsonReader): String {
        if (reader.peek() != JsonToken.STRING) invalid("Distribution field has the wrong type")
        return reader.nextString()
    }

    private fun readOptionalString(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING -> reader.nextString()
        JsonToken.NULL -> { reader.nextNull(); null }
        else -> invalid("Distribution optional field has the wrong type")
    }

    private fun readLong(reader: JsonReader): Long {
        if (reader.peek() != JsonToken.NUMBER) invalid("Distribution number has the wrong type")
        return reader.nextLong()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance(SHA256_ALGORITHM).digest(bytes).toHex()

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(chars)
    }

    private fun invalid(message: String): Nothing = throw InvalidDistribution(message)

    private data class GithubRelease(val assets: Map<String, GithubReleaseAsset>)
    private data class GithubReleaseAsset(val name: String, val size: Long, val digest: String?, val downloadUrl: String)
    private data class VerifiedDirectory(val plugins: List<DirectoryPluginRelease>)
    private data class DirectoryPluginRelease(
        val publisherId: String,
        val pluginId: String,
        val version: String,
        val hostApi: String,
        val abis: Set<String>,
        val publisherKey: PublicKey,
        val packageSize: Long,
        val packageSha256: String,
        val githubUrl: String,
        val giteeUrl: String?,
    )

    private class TransferFailure(message: String, cause: Throwable? = null) : IOException(message, cause)
    private class InvalidDistribution(message: String, cause: Throwable? = null) : IOException(message, cause)

    private companion object {
        const val DIRECTORY_ASSET_NAME = "elymbot-plugin-directory.json"
        const val DIRECTORY_SIGNATURE_ASSET_NAME = "elymbot-plugin-directory.sig"
        const val DIRECTORY_CACHE_DIRECTORY = "plugin-distribution"
        const val DIRECTORY_CACHE_FILE = "verified-directory.bin"
        const val PLUGIN_DOWNLOAD_DIRECTORY = "plugin-downloads"
        const val DIRECTORY_FORMAT_VERSION = 1
        const val CACHE_MAGIC = 0x454C5044
        const val MAX_RELEASE_API_BYTES = 1024 * 1024L
        const val MAX_DIRECTORY_BYTES = 512 * 1024L
        const val MAX_SIGNATURE_BYTES = 1024L
        const val MAX_PUBLISHER_KEY_BYTES = 1024
        const val MAX_CACHE_BYTES = MAX_DIRECTORY_BYTES + MAX_SIGNATURE_BYTES + 32L
        const val MAX_DIRECTORY_PLUGINS = 128
        const val MAX_DIRECTORY_ABIS = 16
        const val MAX_RELEASE_ASSETS = 64
        const val MAX_PLUGIN_PACKAGE_BYTES = 128 * 1024 * 1024L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5
        const val COPY_BUFFER_BYTES = 16 * 1024
        const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        const val SHA256_ALGORITHM = "SHA-256"
        val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val TOKEN_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")
        val ABI_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,39}")
        val ASSET_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,119}")
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}

internal class PluginDistributionException(message: String, cause: Throwable? = null) : IOException(message, cause)

private fun isAllowedUrl(value: String, allowInsecureLocalhost: Boolean, kind: DistributionSourceKind): Boolean {
    val uri = try { URI(value) } catch (_: Exception) { return false }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    if (uri.userInfo != null || uri.fragment != null) return false
    val local = allowInsecureLocalhost && scheme == "http" && host in setOf("localhost", "127.0.0.1", "::1")
    if (local) return true
    if (scheme != "https" || uri.port !in setOf(-1, 443)) return false
    return when (kind) {
        DistributionSourceKind.GITHUB_API -> host == "api.github.com"
        DistributionSourceKind.GITHUB ->
            host == "github.com" || host.endsWith(".github.com") || host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
        DistributionSourceKind.GITEE -> host == "gitee.com" || host.endsWith(".gitee.com")
    }
}
