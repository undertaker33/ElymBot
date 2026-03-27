package com.astrbot.android.runtime.qq

object QqWakeWordPolicy {
    fun matches(
        text: String,
        wakeWords: List<String>,
        adminOnlyEnabled: Boolean,
        isAdmin: Boolean,
    ): Boolean {
        if (adminOnlyEnabled && !isAdmin) return false
        val normalizedText = text.lowercase()
        return wakeWords
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any { wakeWord -> normalizedText.contains(wakeWord) }
    }
}
