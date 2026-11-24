package com.elymbot.android.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.elymbot.core.contract.PlatformMediaReadResult
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class StoredAsset(
    val relativePath: String,
    val mediaType: String,
    val byteSize: Long,
)

sealed interface PlatformAssetImportResult {
    data class Stored(val asset: StoredAsset, val contentType: String) : PlatformAssetImportResult

    data class Rejected(val result: PlatformMediaReadResult) : PlatformAssetImportResult
}

class PrivateAssetStore(
    context: Context,
    private val resolver: ContentResolver = context.applicationContext.contentResolver,
    private val root: File = File(context.applicationContext.filesDir, "media"),
) {
    suspend fun importPlatformMedia(
        maxBytes: Long,
        copy: suspend (OutputStream) -> PlatformMediaReadResult,
    ): PlatformAssetImportResult {
        val pendingPath = AtomicReference<String?>(null)
        try {
            val result = withContext(Dispatchers.IO) {
                if (maxBytes <= 0) {
                    return@withContext PlatformAssetImportResult.Rejected(PlatformMediaReadResult.Failed)
                }
                val relativePath = "media/${UUID.randomUUID()}.bin"
                val destination = resolve(relativePath)
                pendingPath.set(relativePath)
                destination.parentFile?.mkdirs()
                val bounded = try {
                    BoundedOutputStream(FileOutputStream(destination), maxBytes)
                } catch (failure: Throwable) {
                    destination.delete()
                    pendingPath.set(null)
                    if (failure is CancellationException) throw failure
                    return@withContext PlatformAssetImportResult.Rejected(PlatformMediaReadResult.Failed)
                }
                try {
                    val copied = try {
                        copy(bounded)
                    } finally {
                        bounded.close()
                    }
                    when (copied) {
                        is PlatformMediaReadResult.Copied -> {
                            if (bounded.bytesWritten != copied.sizeBytes || copied.sizeBytes <= 0 ||
                                copied.sizeBytes > maxBytes
                            ) {
                                destination.delete()
                                pendingPath.set(null)
                                PlatformAssetImportResult.Rejected(PlatformMediaReadResult.Failed)
                            } else {
                                PlatformAssetImportResult.Stored(
                                    asset = StoredAsset(relativePath, copied.contentType, copied.sizeBytes),
                                    contentType = copied.contentType,
                                )
                            }
                        }
                        else -> {
                            destination.delete()
                            pendingPath.set(null)
                            PlatformAssetImportResult.Rejected(copied)
                        }
                    }
                } catch (failure: Throwable) {
                    destination.delete()
                    pendingPath.set(null)
                    if (failure is CancellationException) throw failure
                    PlatformAssetImportResult.Rejected(
                        if (failure is AssetLimitExceeded) PlatformMediaReadResult.TooLarge
                        else PlatformMediaReadResult.Failed,
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            if (result is PlatformAssetImportResult.Stored) {
                pendingPath.compareAndSet(result.asset.relativePath, null)
            }
            return result
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                pendingPath.getAndSet(null)?.let { resolve(it).delete() }
            }
            throw failure
        }
    }

    suspend fun import(uri: Uri): StoredAsset = withContext(Dispatchers.IO) {
        val mediaType = resolver.getType(uri) ?: "application/octet-stream"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mediaType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?.let { ".$it" }
            .orEmpty()
        val relativePath = "media/${UUID.randomUUID()}$extension"
        val destination = resolve(relativePath)
        destination.parentFile?.mkdirs()
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Unable to open selected image")
            val byteSize = input.use { source ->
                destination.outputStream().use { target -> source.copyTo(target) }
            }
            StoredAsset(relativePath, mediaType, byteSize)
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    suspend fun delete(relativePath: String?) = withContext(Dispatchers.IO) {
        if (relativePath != null) resolve(relativePath).delete()
    }

    fun uri(relativePath: String?): Uri? = relativePath?.let { Uri.fromFile(resolve(it)) }

    fun relativePath(uri: Uri): String? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val file = File(path).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (!file.toPath().startsWith(canonicalRoot.toPath()) || file == canonicalRoot) return null
        return "media/${file.relativeTo(canonicalRoot).invariantSeparatorsPath}"
    }

    fun file(relativePath: String): File = resolve(relativePath)

    suspend fun removeUnreferenced(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relative = "media/${file.relativeTo(root).invariantSeparatorsPath}"
                if (relative !in referencedPaths) file.delete()
            }
        root.walkBottomUp().filter { it.isDirectory && it != root }.forEach { it.delete() }
    }

    private fun resolve(relativePath: String): File {
        val assetName = relativePath.removePrefix("media/")
        require(assetName.isNotEmpty() && assetName != relativePath)
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, assetName).canonicalFile
        require(file != canonicalRoot && file.toPath().startsWith(canonicalRoot.toPath()))
        return file
    }

    private class BoundedOutputStream(
        output: OutputStream,
        private val maxBytes: Long,
    ) : FilterOutputStream(output) {
        var bytesWritten: Long = 0
            private set

        override fun write(value: Int) {
            ensureWithinLimit(1)
            out.write(value)
            bytesWritten += 1
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureWithinLimit(length)
            out.write(buffer, offset, length)
            bytesWritten += length
        }

        private fun ensureWithinLimit(length: Int) {
            if (length < 0 || bytesWritten > maxBytes - length) throw AssetLimitExceeded()
        }
    }

    private class AssetLimitExceeded : IOException()
}
