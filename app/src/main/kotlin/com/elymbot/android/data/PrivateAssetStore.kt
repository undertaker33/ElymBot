package com.elymbot.android.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredAsset(
    val relativePath: String,
    val mediaType: String,
    val byteSize: Long,
)

class PrivateAssetStore(
    context: Context,
    private val resolver: ContentResolver = context.applicationContext.contentResolver,
    private val root: File = File(context.applicationContext.filesDir, "media"),
) {
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
}
