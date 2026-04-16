package com.astrbot.android.data

import com.astrbot.android.model.PersonaProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaRepositoryTest {
    @Test
    fun restoreProfiles_backfills_default_persona_with_web_search_when_tools_are_empty() {
        val original = PersonaRepository.snapshotProfiles()

        try {
            PersonaRepository.restoreProfiles(
                listOf(
                    PersonaProfile(
                        id = "default",
                        name = "Default Assistant",
                        systemPrompt = "You are a concise, reliable QQ assistant.",
                        enabledTools = emptySet(),
                    ),
                ),
            )

            val restored = PersonaRepository.snapshotProfiles().single { it.id == "default" }
            assertEquals(setOf("web_search"), restored.enabledTools)
        } finally {
            PersonaRepository.restoreProfiles(original)
        }
    }
}
