package com.astrbot.android.core.runtime.context

enum class RuntimeWebSearchPromptIntent {
    NONE,
    NEWS,
    WEATHER,
    REALTIME,
}

interface RuntimeWebSearchPromptStringProvider {
    fun guidanceFor(intent: RuntimeWebSearchPromptIntent): String?
}

object RuntimeWebSearchPromptGuidance {
    fun forMessage(
        text: String,
        strings: RuntimeWebSearchPromptStringProvider,
    ): String? {
        return strings.guidanceFor(RuntimeWebSearchPromptIntentResolver.evaluate(text))
    }
}

private object RuntimeWebSearchPromptIntentResolver {
    private val newsKeywords = listOf(
        "\u65b0\u95fb",
        "\u4eca\u65e5\u65b0\u95fb",
        "\u4eca\u5929\u65b0\u95fb",
        "\u6700\u65b0\u6d88\u606f",
        "\u5feb\u8baf",
        "\u5934\u6761",
        "\u65f6\u4e8b",
        "\u70ed\u641c",
        "news",
        "headline",
        "headlines",
        "breaking news",
        "latest news",
        "news update",
    )
    private val weatherKeywords = listOf(
        "\u5929\u6c14",
        "\u5929\u6c14\u9884\u62a5",
        "\u6c14\u6e29",
        "\u6e29\u5ea6",
        "\u4e0b\u96e8",
        "\u964d\u96e8",
        "\u6e7f\u5ea6",
        "\u98ce\u529b",
        "\u7a7a\u6c14\u8d28\u91cf",
        "weather",
        "forecast",
        "temperature",
        "humidity",
        "wind",
        "aqi",
    )
    private val realtimeKeywords = listOf(
        "\u4eca\u5929",
        "\u4eca\u65e5",
        "\u73b0\u5728",
        "\u6700\u65b0",
        "\u521a\u521a",
        "\u67e5\u4e00\u4e0b",
        "\u641c\u7d22",
        "\u641c\u4e00\u4e0b",
        "\u5b9e\u65f6",
        "\u5f53\u524d",
        "today",
        "now",
        "latest",
        "search",
        "current",
    )

    fun evaluate(text: String): RuntimeWebSearchPromptIntent {
        val normalized = text.trim()
        if (normalized.isBlank()) return RuntimeWebSearchPromptIntent.NONE
        return when {
            firstMatch(normalized, newsKeywords) -> RuntimeWebSearchPromptIntent.NEWS
            firstMatch(normalized, weatherKeywords) -> RuntimeWebSearchPromptIntent.WEATHER
            firstMatch(normalized, realtimeKeywords) -> RuntimeWebSearchPromptIntent.REALTIME
            else -> RuntimeWebSearchPromptIntent.NONE
        }
    }

    private fun firstMatch(text: String, keywords: List<String>): Boolean {
        val lower = text.lowercase()
        return keywords.any { keyword ->
            if (keyword.any { it.code > 127 }) {
                text.contains(keyword)
            } else {
                Regex("""\b${Regex.escape(keyword.lowercase())}\b""").containsMatchIn(lower)
            }
        }
    }
}
