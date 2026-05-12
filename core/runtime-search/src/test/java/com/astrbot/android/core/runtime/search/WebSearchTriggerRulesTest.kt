package com.astrbot.android.core.runtime.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTriggerRulesTest {

    @Test
    fun classifies_news_weather_realtime_and_none() {
        assertEquals(WebSearchTriggerIntent.NEWS, WebSearchTriggerRules.evaluate("latest news").intent)
        assertEquals(WebSearchTriggerIntent.WEATHER, WebSearchTriggerRules.evaluate("weather forecast").intent)
        assertEquals(WebSearchTriggerIntent.REALTIME, WebSearchTriggerRules.evaluate("current Kotlin release").intent)
        assertEquals(WebSearchTriggerIntent.NONE, WebSearchTriggerRules.evaluate("write a haiku").intent)
    }

    @Test
    fun only_injects_prompt_when_search_intent_is_detected() {
        assertTrue(WebSearchTriggerRules.evaluate("today").shouldInjectSearchPrompt)
        assertFalse(WebSearchTriggerRules.evaluate("static question").shouldInjectSearchPrompt)
    }
}
