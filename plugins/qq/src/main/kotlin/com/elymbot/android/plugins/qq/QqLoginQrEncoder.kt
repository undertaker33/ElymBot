package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformQrMatrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.util.ArrayList

/** Encodes the short-lived NapCat challenge locally; its source text never leaves the QQ plugin. */
internal object QqLoginQrEncoder {
    fun encode(content: String): PlatformQrMatrix? {
        if (content.isBlank() || content.length > MAX_CHALLENGE_LENGTH) return null
        val encoded = try {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                MINIMUM_OUTPUT_SIZE,
                MINIMUM_OUTPUT_SIZE,
                mapOf(EncodeHintType.MARGIN to 0),
            )
        } catch (_: WriterException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        val dimension = encoded.width
        if (dimension != encoded.height || dimension !in 1..PlatformQrMatrix.MAX_DIMENSION) return null
        val modules = ArrayList<Boolean>(dimension * dimension)
        for (y in 0 until dimension) {
            for (x in 0 until dimension) modules += encoded[x, y]
        }
        return PlatformQrMatrix.copyOf(dimension, modules)
    }

    private const val MINIMUM_OUTPUT_SIZE = 1
    private const val MAX_CHALLENGE_LENGTH = 4_096
}
