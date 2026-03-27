package com.astrbot.android.runtime.qq

class QqKeywordDetector(patterns: List<String>) {
    private val regexList = patterns.mapNotNull { pattern ->
        runCatching { Regex(pattern) }.getOrNull()
    }

    fun matches(text: String): Boolean {
        return regexList.any { regex -> regex.containsMatchIn(text) }
    }
}
