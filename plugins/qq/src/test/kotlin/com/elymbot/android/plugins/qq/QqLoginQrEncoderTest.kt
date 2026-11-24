package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformQrMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqLoginQrEncoderTest {
    @Test
    fun encodesChallengeIntoBoundedMatrixWithoutExposingSourceText() {
        val matrix = checkNotNull(QqLoginQrEncoder.encode(TEST_LOGIN_URL))

        assertTrue(matrix.dimension in 1..PlatformQrMatrix.MAX_DIMENSION)
        assertEquals(matrix.dimension * matrix.dimension, matrix.darkModules.size)
        assertTrue(matrix.darkModules.any { it })
        assertTrue(matrix.darkModules.any { !it })
    }

    @Test
    fun matrixCopiesAndProtectsModuleList() {
        val source = mutableListOf(true, false, false, true)
        val matrix = PlatformQrMatrix.copyOf(dimension = 2, darkModules = source)
        source[0] = false

        assertTrue(matrix.darkModules[0])
        val mutation = runCatching {
            (matrix.darkModules as MutableList<Boolean>)[0] = false
        }.exceptionOrNull()
        assertTrue(mutation is UnsupportedOperationException)
    }

    private companion object {
        const val TEST_LOGIN_URL = "https://txz.qq.com/synthetic-login-challenge"
    }
}
