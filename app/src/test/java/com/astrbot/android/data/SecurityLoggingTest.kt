package com.astrbot.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLoggingTest {
    @Test
    fun sanitize_url_masks_query_api_key() {
        val sanitized = ChatCompletionService.sanitizeUrlForLogsForTests(
            "https://example.com/v1/models/gemini:streamGenerateContent?key=super-secret&alt=sse",
        )

        assertTrue(sanitized.contains("key=***"))
        assertFalse(sanitized.contains("super-secret"))
        assertTrue(sanitized.contains("alt=sse"))
    }

    @Test
    fun sanitize_napcat_detail_masks_known_secret_values() {
        val sanitized = NapCatLoginService.sanitizeDetailForLogsForTests(
            "configuredToken=abcdef123456 credential=qwerty1234567890",
        )

        assertFalse(sanitized.contains("abcdef123456"))
        assertFalse(sanitized.contains("qwerty1234567890"))
        assertTrue(sanitized.contains("configuredToken=<redacted:12>"))
        assertTrue(sanitized.contains("credential=<redacted:16>"))
    }

    @Test
    fun mask_secret_returns_placeholder_for_blank_values() {
        assertEquals("<empty>", NapCatLoginService.maskSecretForTests(""))
    }
}
