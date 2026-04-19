package com.astrbot.android.data

import com.astrbot.android.core.common.profile.PersonaInUseException
import com.astrbot.android.core.common.profile.PersonaReferenceGuard
import com.astrbot.android.model.PersonaProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    /**
     * Task10 Phase3 – Task C: deleting a persona that is still referenced must fail.
     */
    @Test
    fun delete_referenced_persona_throws_PersonaInUseException() {
        val original = PersonaRepository.snapshotProfiles()
        val inUseId = "persona-in-use"
        PersonaReferenceGuard.register { personaId -> personaId == inUseId }

        try {
            PersonaRepository.restoreProfiles(
                listOf(
                    PersonaProfile(id = inUseId, name = "In-Use Persona", systemPrompt = "", enabledTools = emptySet()),
                    PersonaProfile(id = "persona-spare", name = "Spare Persona", systemPrompt = "", enabledTools = emptySet()),
                ),
            )

            try {
                PersonaRepository.delete(inUseId)
                fail("Expected PersonaInUseException but delete succeeded")
            } catch (e: PersonaInUseException) {
                assertEquals(inUseId, e.personaId)
            }

            // Persona must still be present
            assertTrue(PersonaRepository.snapshotProfiles().any { it.id == inUseId })
        } finally {
            PersonaReferenceGuard.register { false }
            PersonaRepository.restoreProfiles(original)
        }
    }

    /**
     * Task10 Phase3 – Task C: deleting an unreferenced persona must succeed.
     */
    @Test
    fun delete_unreferenced_persona_succeeds() {
        val original = PersonaRepository.snapshotProfiles()
        PersonaReferenceGuard.register { false }  // no references

        try {
            PersonaRepository.restoreProfiles(
                listOf(
                    PersonaProfile(id = "persona-a", name = "Persona A", systemPrompt = "", enabledTools = emptySet()),
                    PersonaProfile(id = "persona-b", name = "Persona B", systemPrompt = "", enabledTools = emptySet()),
                ),
            )

            PersonaRepository.delete("persona-a")

            assertFalse(PersonaRepository.snapshotProfiles().any { it.id == "persona-a" })
        } finally {
            PersonaReferenceGuard.register { false }
            PersonaRepository.restoreProfiles(original)
        }
    }
}

