package com.elymbot.android.session

import com.elymbot.android.data.PlatformAssetImportResult
import com.elymbot.android.data.PrivateAssetStore
import com.elymbot.android.data.StoredAsset
import com.elymbot.core.contract.MediaResourceReference
import com.elymbot.core.contract.PlatformMediaReadResult
import com.elymbot.core.contract.PlatformPluginHandle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal sealed interface PlatformInboundImageResult {
    data class Stored(val asset: StoredAsset, val mediaType: String) : PlatformInboundImageResult

    data object Unauthorized : PlatformInboundImageResult

    data class Rejected(val status: Status) : PlatformInboundImageResult

    enum class Status {
        TOO_LARGE,
        UNSUPPORTED,
        UNAVAILABLE,
    }
}

/** Copies a normalized image only while the host's route and installation check remains valid. */
internal class PlatformInboundMediaReader(private val assets: PrivateAssetStore) {
    suspend fun copyImage(
        handle: PlatformPluginHandle,
        reference: MediaResourceReference,
        maxBytes: Long,
        stillAuthorized: suspend () -> Boolean,
    ): PlatformInboundImageResult {
        if (!reference.mediaType.startsWith("image/", ignoreCase = true) || maxBytes <= 0) {
            return PlatformInboundImageResult.Rejected(PlatformInboundImageResult.Status.UNSUPPORTED)
        }
        if (!stillAuthorized()) return PlatformInboundImageResult.Unauthorized

        var pendingAssetPath: String? = null
        try {
            var authorizedBeforeCopy = true
            val imported = assets.importPlatformMedia(maxBytes) { output ->
                if (!stillAuthorized()) {
                    authorizedBeforeCopy = false
                    PlatformMediaReadResult.Failed
                } else {
                    handle.copyMedia(reference, output, maxBytes)
                }
            }
            if (imported is PlatformAssetImportResult.Stored) {
                pendingAssetPath = imported.asset.relativePath
            }
            if (!authorizedBeforeCopy || !stillAuthorized()) return PlatformInboundImageResult.Unauthorized

            return when (imported) {
                is PlatformAssetImportResult.Stored -> {
                    val mediaType = detectPlatformImageMimeType(assets.file(imported.asset.relativePath))
                    if (mediaType == null) {
                        PlatformInboundImageResult.Rejected(PlatformInboundImageResult.Status.UNSUPPORTED)
                    } else {
                        pendingAssetPath = null
                        PlatformInboundImageResult.Stored(imported.asset, mediaType)
                    }
                }
                is PlatformAssetImportResult.Rejected -> PlatformInboundImageResult.Rejected(
                    when (imported.result) {
                        PlatformMediaReadResult.TooLarge -> PlatformInboundImageResult.Status.TOO_LARGE
                        PlatformMediaReadResult.Unsupported -> PlatformInboundImageResult.Status.UNSUPPORTED
                        else -> PlatformInboundImageResult.Status.UNAVAILABLE
                    },
                )
            }
        } finally {
            withContext(NonCancellable) {
                pendingAssetPath?.let { assets.delete(it) }
            }
        }
    }
}
