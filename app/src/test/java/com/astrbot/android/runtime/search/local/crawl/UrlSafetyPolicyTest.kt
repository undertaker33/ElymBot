package com.astrbot.android.runtime.search.local.crawl

import com.astrbot.android.core.runtime.search.local.crawl.UrlSafetyPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyPolicyTest {
    private val policy = UrlSafetyPolicy()

    @Test
    fun allowsPublicHttpAndHttpsUrls() {
        assertTrue(policy.isAllowed("https://example.com/news?id=1").allowed)
        assertTrue(policy.isAllowed("http://www.example.org/article").allowed)
    }

    @Test
    fun rejectsNonHttpSchemesAndLocalTargets() {
        val blocked = listOf(
            "file:///sdcard/private.txt",
            "content://com.example/private",
            "intent://scan/#Intent;scheme=zxing;end",
            "ftp://example.com/file",
            "http://localhost:8080",
            "http://127.0.0.1/admin",
            "http://10.2.3.4/",
            "http://172.16.0.2/",
            "http://172.31.255.255/",
            "http://192.168.1.5/",
            "http://169.254.10.20/",
            "http://[::1]/",
            "http://[fc00::1]/",
            "http://[fe80::1]/",
        )

        blocked.forEach { url ->
            assertFalse("Expected blocked URL: $url", policy.isAllowed(url).allowed)
        }
    }
}
