package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.JsonReader
import android.util.JsonToken
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class OfficialAppUpdateConfiguration(
    val packageName: String = OFFICIAL_PACKAGE_NAME,
    val trustedSignerSha256: String = OFFICIAL_SIGNER_SHA256,
    val githubLatestReleaseApiUrl: String = OFFICIAL_LATEST_RELEASE_API,
    internal val allowInsecureLocalhostForTests: Boolean = false,
) {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN))
        require(SHA256_PATTERN.matches(trustedSignerSha256))
        require(isAllowedAppUrl(githubLatestReleaseApiUrl, allowInsecureLocalhostForTests, AppDownloadSource.GITHUB_API))
        require(allowInsecureLocalhostForTests || githubLatestReleaseApiUrl == OFFICIAL_LATEST_RELEASE_API)
    }

    internal companion object {
        const val OFFICIAL_PACKAGE_NAME = "com.elymbot.android"
        const val OFFICIAL_SIGNER_SHA256 = "FD4EF592C4DDB82BB26411F977180F689313E8138F1795BA18064B301CF1928F"
        const val OFFICIAL_LATEST_RELEASE_API =
            "https://api.github.com/repos/undertaker33/ElymBot/releases/latest"
        val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}

internal enum class AppUpdateStatus {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    METADATA_MISSING,
    NETWORK_ERROR,
    INVALID_RELEASE,
    INCOMPATIBLE_PACKAGE,
    INCOMPATIBLE_INSTALLATION,
}

internal enum class AppUpdateFailure {
    NETWORK,
    RELEASE_METADATA,
    RELEASE_ASSET_DIGEST,
    INSTALLED_PACKAGE,
}

internal data class AppUpdateSnapshot(
    val status: AppUpdateStatus,
    val installedVersionCode: Long? = null,
    val installedVersionName: String? = null,
    val availableUpdate: AppUpdateOffer? = null,
    val failure: AppUpdateFailure? = null,
)

internal class AppUpdateOffer internal constructor(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    internal val installedVersionCode: Long,
    internal val downloadUrl: String,
    internal val downloadSize: Long,
    internal val downloadSha256: String,
    internal val ownerToken: Any,
)

internal class VerifiedAppUpdateApk internal constructor(
    val packageFile: File,
    val versionCode: Long,
    val versionName: String,
    internal val ownerToken: Any,
) : Closeable {
    override fun close() {
        packageFile.delete()
    }
}

/** Checks the official release once per call and downloads an APK only after an explicit caller action. */
internal class OfficialAppUpdateService(
    context: Context,
    private val configuration: OfficialAppUpdateConfiguration = OfficialAppUpdateConfiguration(),
) {
    private val appContext = context.applicationContext
    private val serviceToken = Any()

    @Volatile
    private var currentOffer: AppUpdateOffer? = null

    suspend fun checkForUpdate(): AppUpdateSnapshot {
        currentOffer = null
        val installed = readInstalledApp() ?: return AppUpdateSnapshot(
            AppUpdateStatus.INCOMPATIBLE_PACKAGE,
            failure = AppUpdateFailure.INSTALLED_PACKAGE,
        )
        if (appContext.packageName != configuration.packageName) {
            return AppUpdateSnapshot(
                AppUpdateStatus.INCOMPATIBLE_PACKAGE,
                installed.versionCode,
                installed.versionName,
                failure = AppUpdateFailure.INSTALLED_PACKAGE,
            )
        }
        if (installed.signerSha256 != configuration.trustedSignerSha256.lowercase(Locale.ROOT)) {
            return AppUpdateSnapshot(
                AppUpdateStatus.INCOMPATIBLE_INSTALLATION,
                installed.versionCode,
                installed.versionName,
                failure = AppUpdateFailure.INSTALLED_PACKAGE,
            )
        }

        return try {
            val release = withContext(Dispatchers.IO) {
                try {
                    readLatestRelease()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: AppTransferFailure) {
                    throw failure
                } catch (failure: InvalidAppRelease) {
                    throw failure
                } catch (_: Exception) {
                    invalid(AppUpdateFailure.RELEASE_METADATA)
                }
            }
            val metadataAsset = release.assets[RELEASE_METADATA_ASSET_NAME]
                ?: return AppUpdateSnapshot(
                    AppUpdateStatus.METADATA_MISSING,
                    installed.versionCode,
                    installed.versionName,
                )
            val sidecarBytes = withContext(Dispatchers.IO) {
                readAsset(metadataAsset, MAX_METADATA_BYTES)
            }
            if (sha256(sidecarBytes) != metadataAsset.sha256) {
                throw InvalidAppRelease(AppUpdateFailure.RELEASE_ASSET_DIGEST)
            }
            val metadata = try {
                parseReleaseMetadata(sidecarBytes)
            } catch (failure: InvalidAppRelease) {
                throw failure
            } catch (_: Exception) {
                invalid(AppUpdateFailure.RELEASE_METADATA)
            }
            if (metadata.packageName != configuration.packageName) {
                throw InvalidAppRelease(AppUpdateFailure.RELEASE_METADATA)
            }
            val apkAsset = release.assets[metadata.apkAssetName]
                ?: throw InvalidAppRelease(AppUpdateFailure.RELEASE_METADATA)
            if (apkAsset.size !in 1..MAX_APK_BYTES ||
                metadata.apkSha256 != apkAsset.sha256 ||
                !isAllowedAppUrl(apkAsset.downloadUrl, configuration.allowInsecureLocalhostForTests, AppDownloadSource.GITHUB)
            ) {
                throw InvalidAppRelease(AppUpdateFailure.RELEASE_ASSET_DIGEST)
            }
            if (metadata.versionCode <= installed.versionCode) {
                AppUpdateSnapshot(
                    AppUpdateStatus.UP_TO_DATE,
                    installed.versionCode,
                    installed.versionName,
                )
            } else {
                val offer = AppUpdateOffer(
                    packageName = metadata.packageName,
                    versionCode = metadata.versionCode,
                    versionName = metadata.versionName,
                    installedVersionCode = installed.versionCode,
                    downloadUrl = apkAsset.downloadUrl,
                    downloadSize = apkAsset.size,
                    downloadSha256 = metadata.apkSha256,
                    ownerToken = serviceToken,
                )
                currentOffer = offer
                AppUpdateSnapshot(
                    AppUpdateStatus.UPDATE_AVAILABLE,
                    installed.versionCode,
                    installed.versionName,
                    availableUpdate = offer,
                )
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: AppTransferFailure) {
            AppUpdateSnapshot(
                AppUpdateStatus.NETWORK_ERROR,
                installed.versionCode,
                installed.versionName,
                failure = AppUpdateFailure.NETWORK,
            )
        } catch (failure: InvalidAppRelease) {
            AppUpdateSnapshot(
                AppUpdateStatus.INVALID_RELEASE,
                installed.versionCode,
                installed.versionName,
                failure = failure.failure,
            )
        } catch (_: Exception) {
            AppUpdateSnapshot(
                AppUpdateStatus.NETWORK_ERROR,
                installed.versionCode,
                installed.versionName,
                failure = AppUpdateFailure.NETWORK,
            )
        }
    }

    /** Call only after the user confirms the available release. */
    suspend fun downloadAndVerify(offer: AppUpdateOffer): VerifiedAppUpdateApk {
        if (offer.ownerToken !== serviceToken || currentOffer !== offer) {
            throw AppUpdateException("Update offer is no longer current")
        }
        val installed = readInstalledApp() ?: throw AppUpdateException("Installed app could not be verified")
        if (appContext.packageName != configuration.packageName ||
            installed.signerSha256 != configuration.trustedSignerSha256.lowercase(Locale.ROOT) ||
            installed.versionCode != offer.installedVersionCode
        ) {
            throw AppUpdateException("Installed app changed after the update check")
        }

        val pendingFile = AtomicReference<File?>(null)
        return try {
            val file = withContext(Dispatchers.IO) {
                val output = createTemporaryApk()
                pendingFile.set(output)
                try {
                    val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
                    val copied = streamToFile(offer, output, digest)
                    if (copied != offer.downloadSize || digest.digest().toHex() != offer.downloadSha256) {
                        throw InvalidAppRelease(AppUpdateFailure.RELEASE_ASSET_DIGEST)
                    }
                    if (!output.setReadable(false, false) || !output.setReadable(true, true) ||
                        !output.setWritable(false, false) || output.canWrite()
                    ) {
                        throw AppUpdateException("Verified APK permissions could not be applied")
                    }
                    verifyDownloadedApk(output, offer)
                    output
                } catch (failure: Throwable) {
                    output.delete()
                    pendingFile.set(null)
                    throw failure
                }
            }
            currentCoroutineContext().ensureActive()
            pendingFile.set(null)
            VerifiedAppUpdateApk(file, offer.versionCode, offer.versionName, serviceToken)
        } catch (failure: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { pendingFile.getAndSet(null)?.delete() }
            throw failure
        }
    }

    /** Returns the system package-installer request; caller launches it only from a user-confirmed UI action. */
    fun installerIntent(context: Context, update: VerifiedAppUpdateApk): Intent {
        if (update.ownerToken !== serviceToken || !update.packageFile.isFile || update.packageFile.canWrite()) {
            throw AppUpdateException("Verified update APK is not available")
        }
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", update.packageFile)
        } catch (failure: IllegalArgumentException) {
            throw AppUpdateException("Update APK is outside the installer share path", failure)
        }
        @Suppress("DEPRECATION")
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }

    fun hasInstallerPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installerPermissionSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    private fun readInstalledApp(): InstalledApp? {
        if (appContext.packageName != configuration.packageName) return null
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = try {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(configuration.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val signerDigests = signerDigests(info)
        if (signerDigests.size != 1) return null
        return InstalledApp(
            versionCode = packageVersionCode(info),
            versionName = info.versionName.orEmpty(),
            signerSha256 = signerDigests.single(),
        )
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return signers.map { signature -> sha256(signature.toByteArray()) }.toSet()
    }

    private fun verifyDownloadedApk(file: File, offer: AppUpdateOffer) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val archive = appContext.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw InvalidAppRelease(AppUpdateFailure.RELEASE_METADATA)
        val archiveSigners = signerDigests(archive)
        if (archive.packageName != configuration.packageName ||
            packageVersionCode(archive) != offer.versionCode ||
            archive.versionName != offer.versionName ||
            archiveSigners != setOf(configuration.trustedSignerSha256.lowercase(Locale.ROOT))
        ) {
            throw InvalidAppRelease(AppUpdateFailure.RELEASE_METADATA)
        }
    }

    private suspend fun readLatestRelease(): GithubRelease {
        val bytes = requestBytes(configuration.githubLatestReleaseApiUrl, MAX_RELEASE_API_BYTES, AppDownloadSource.GITHUB_API)
        val assets = LinkedHashMap<String, GithubAsset>()
        var foundAssets = false
        JsonReader(StringReader(bytes.toString(Charsets.UTF_8))).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "assets" -> {
                        if (foundAssets) invalid(AppUpdateFailure.RELEASE_METADATA)
                        foundAssets = true
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val asset = readGithubAsset(reader)
                            if (assets.put(asset.name, asset) != null) invalid(AppUpdateFailure.RELEASE_METADATA)
                            if (assets.size > MAX_ASSETS) invalid(AppUpdateFailure.RELEASE_METADATA)
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        if (!foundAssets) invalid(AppUpdateFailure.RELEASE_METADATA)
        return GithubRelease(assets)
    }

    private fun readGithubAsset(reader: JsonReader): GithubAsset {
        var name: String? = null
        var size: Long? = null
        var digest: String? = null
        var url: String? = null
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            if (!seen.add(field)) invalid(AppUpdateFailure.RELEASE_METADATA)
            when (field) {
                "name" -> name = readString(reader)
                "size" -> size = readLong(reader)
                "digest" -> digest = readNullableString(reader)
                "browser_download_url" -> url = readString(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val assetName = name ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val assetSize = size ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val downloadUrl = url ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val assetDigest = digest?.removePrefix("sha256:")?.lowercase(Locale.ROOT)
        if (!ASSET_NAME_PATTERN.matches(assetName) || assetSize < 0 ||
            assetDigest != null && !OfficialAppUpdateConfiguration.SHA256_PATTERN.matches(assetDigest) ||
            !isAllowedAppUrl(downloadUrl, configuration.allowInsecureLocalhostForTests, AppDownloadSource.GITHUB)
        ) {
            invalid(AppUpdateFailure.RELEASE_METADATA)
        }
        return GithubAsset(assetName, assetSize, assetDigest, downloadUrl)
    }

    private suspend fun readAsset(asset: GithubAsset, maxBytes: Long): ByteArray {
        if (asset.size !in 1..maxBytes || asset.sha256 == null) invalid(AppUpdateFailure.RELEASE_METADATA)
        val output = ByteArrayOutputStream(asset.size.toInt())
        val copied = stream(asset.downloadUrl, asset.size, maxBytes, AppDownloadSource.GITHUB) { buffer, count ->
            output.write(buffer, 0, count)
        }
        if (copied != asset.size || sha256(output.toByteArray()) != asset.sha256) {
            invalid(AppUpdateFailure.RELEASE_ASSET_DIGEST)
        }
        return output.toByteArray()
    }

    private fun parseReleaseMetadata(bytes: ByteArray): ReleaseMetadata {
        var formatVersion: Long? = null
        var packageName: String? = null
        var versionCode: Long? = null
        var versionName: String? = null
        var apkAssetName: String? = null
        var apkSha256: String? = null
        val seen = mutableSetOf<String>()
        JsonReader(StringReader(bytes.toString(Charsets.UTF_8))).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                if (!seen.add(field)) invalid(AppUpdateFailure.RELEASE_METADATA)
                when (field) {
                    "formatVersion" -> formatVersion = readLong(reader)
                    "packageName" -> packageName = readString(reader)
                    "versionCode" -> versionCode = readLong(reader)
                    "versionName" -> versionName = readString(reader)
                    "apkAssetName" -> apkAssetName = readString(reader)
                    "apkSha256" -> apkSha256 = readString(reader).lowercase(Locale.ROOT)
                    else -> invalid(AppUpdateFailure.RELEASE_METADATA)
                }
            }
            reader.endObject()
        }
        val version = versionCode ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val name = versionName ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val declaredPackage = packageName ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val asset = apkAssetName ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        val digest = apkSha256 ?: invalid(AppUpdateFailure.RELEASE_METADATA)
        if (formatVersion != SIDECAR_FORMAT_VERSION.toLong() || version <= 0 ||
            name.isBlank() || name.length > MAX_VERSION_NAME_LENGTH || name.any { character -> character.isISOControl() } ||
            !declaredPackage.matches(OfficialAppUpdateConfiguration.PACKAGE_NAME_PATTERN) ||
            !ASSET_NAME_PATTERN.matches(asset) || !OfficialAppUpdateConfiguration.SHA256_PATTERN.matches(digest)
        ) {
            invalid(AppUpdateFailure.RELEASE_METADATA)
        }
        return ReleaseMetadata(declaredPackage, version, name, asset, digest)
    }

    private suspend fun streamToFile(offer: AppUpdateOffer, output: File, digest: MessageDigest): Long =
        FileOutputStream(output).use { fileOutput ->
            stream(offer.downloadUrl, offer.downloadSize, MAX_APK_BYTES, AppDownloadSource.GITHUB) { buffer, count ->
                digest.update(buffer, 0, count)
                fileOutput.write(buffer, 0, count)
            }
        }

    private suspend fun requestBytes(url: String, maxBytes: Long, source: AppDownloadSource): ByteArray {
        val output = ByteArrayOutputStream()
        stream(url, null, maxBytes, source) { buffer, count -> output.write(buffer, 0, count) }
        return output.toByteArray()
    }

    private suspend fun stream(
        initialUrl: String,
        expectedSize: Long?,
        maxBytes: Long,
        source: AppDownloadSource,
        consume: (ByteArray, Int) -> Unit,
    ): Long {
        var current = URI(initialUrl)
        var redirects = 0
        while (true) {
            if (!isAllowedAppUrl(current.toString(), configuration.allowInsecureLocalhostForTests, source)) {
                throw AppTransferFailure("App release URL is not allowed")
            }
            val connection = (URL(current.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", if (source == AppDownloadSource.GITHUB_API) "application/vnd.github+json" else "application/octet-stream")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ElymBot-Android")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    if (++redirects > MAX_REDIRECTS) throw AppTransferFailure("Too many release redirects")
                    val location = connection.getHeaderField("Location") ?: throw AppTransferFailure("Release redirect has no target")
                    current = current.resolve(location)
                    continue
                }
                if (status !in 200..299) throw AppTransferFailure("Release request failed with HTTP $status")
                val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
                if (contentLength > maxBytes || (expectedSize != null && contentLength >= 0 && contentLength != expectedSize)) {
                    invalid(AppUpdateFailure.RELEASE_ASSET_DIGEST)
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
                            invalid(AppUpdateFailure.RELEASE_ASSET_DIGEST)
                        }
                        consume(buffer, count)
                    }
                }
                return copied
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: InvalidAppRelease) {
                throw failure
            } catch (failure: AppTransferFailure) {
                throw failure
            } catch (failure: IOException) {
                throw AppTransferFailure("Release transfer failed", failure)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun createTemporaryApk(): File {
        val cacheRoot = appContext.cacheDir.canonicalFile
        val directory = File(cacheRoot, APP_UPDATE_DIRECTORY)
        if (!directory.exists() && !directory.mkdir() && !directory.isDirectory) {
            throw AppUpdateException("Private update directory is unavailable")
        }
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath()) ||
            !directory.canonicalFile.toPath().startsWith(cacheRoot.toPath())
        ) {
            throw AppUpdateException("Private update directory is unsafe")
        }
        return Files.createTempFile(directory.toPath(), "elymbot-update-", ".apk").toFile()
    }

    private fun packageVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    private fun readString(reader: JsonReader): String {
        if (reader.peek() != JsonToken.STRING) invalid(AppUpdateFailure.RELEASE_METADATA)
        return reader.nextString()
    }

    private fun readNullableString(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING -> reader.nextString()
        JsonToken.NULL -> { reader.nextNull(); null }
        else -> invalid(AppUpdateFailure.RELEASE_METADATA)
    }

    private fun readLong(reader: JsonReader): Long {
        if (reader.peek() != JsonToken.NUMBER) invalid(AppUpdateFailure.RELEASE_METADATA)
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

    private fun invalid(failure: AppUpdateFailure): Nothing = throw InvalidAppRelease(failure)

    private data class InstalledApp(val versionCode: Long, val versionName: String, val signerSha256: String)
    private data class GithubRelease(val assets: Map<String, GithubAsset>)
    private data class GithubAsset(val name: String, val size: Long, val sha256: String?, val downloadUrl: String)
    private data class ReleaseMetadata(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val apkAssetName: String,
        val apkSha256: String,
    )

    private class AppTransferFailure(message: String, cause: Throwable? = null) : IOException(message, cause)
    private class InvalidAppRelease(val failure: AppUpdateFailure) : IOException("Official app release metadata is invalid")

    private companion object {
        const val RELEASE_METADATA_ASSET_NAME = "elymbot-release.json"
        const val SIDECAR_FORMAT_VERSION = 1
        const val MAX_RELEASE_API_BYTES = 1024 * 1024L
        const val MAX_METADATA_BYTES = 64 * 1024L
        const val MAX_APK_BYTES = 256 * 1024 * 1024L
        const val MAX_ASSETS = 64
        const val MAX_VERSION_NAME_LENGTH = 80
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5
        const val COPY_BUFFER_BYTES = 16 * 1024
        const val APP_UPDATE_DIRECTORY = "app-updates"
        const val SHA256_ALGORITHM = "SHA-256"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        val ASSET_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,119}")
    }
}

internal class AppUpdateException(message: String, cause: Throwable? = null) : IOException(message, cause)

private enum class AppDownloadSource { GITHUB_API, GITHUB }

private fun isAllowedAppUrl(value: String, allowInsecureLocalhost: Boolean, source: AppDownloadSource): Boolean {
    val uri = try { URI(value) } catch (_: Exception) { return false }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    if (uri.userInfo != null || uri.fragment != null) return false
    val local = allowInsecureLocalhost && scheme == "http" && host == "localhost"
    if (local) return true
    if (scheme != "https" || uri.port !in setOf(-1, 443)) return false
    return when (source) {
        AppDownloadSource.GITHUB_API -> host == "api.github.com"
        AppDownloadSource.GITHUB ->
            host == "github.com" || host.endsWith(".github.com") || host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
    }
}
