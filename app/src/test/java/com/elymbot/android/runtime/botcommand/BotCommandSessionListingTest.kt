package com.elymbot.android.feature.chat.runtime.botcommand

import com.elymbot.android.model.PersonaProfile
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.model.chat.MessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCommandSessionListingTest {

    @Test
    fun `session listing shows current conversation type and page hint`() {
        val personas = listOf(
            PersonaProfile(id = "default", name = "Default Assistant", systemPrompt = "default", enabledTools = emptySet()),
        )
        val sessions = (1..12).map { index ->
            ConversationSession(
                id = "session-$index",
                title = "\u4f1a\u8bdd$index",
                botId = "qqbot",
                personaId = "default",
                providerId = "provider-1",
                platformId = "app",
                messageType = MessageType.OtherMessage,
                originSessionId = "session-$index",
                maxContextMessages = 12,
                messages = emptyList(),
            )
        }

        val listing = BotCommandSessionListing.render(
            sessions = sessions,
            personas = personas,
            currentSessionId = "session-12",
            page = 2,
            languageTag = "zh",
        )

        assertTrue(listing.contains("\u4f1a\u8bdd\u5217\u8868"))
        assertTrue(listing.contains("\u4f1a\u8bdd12 (session-12)"))
        assertTrue(listing.contains("\u5f53\u524d\u5bf9\u8bdd\uff1a\u4f1a\u8bdd12 (session-12)"))
        assertTrue(listing.contains("\u4f1a\u8bdd\u7c7b\u578b\uff1a\u5e94\u7528\u5185\u804a\u5929"))
        assertTrue(listing.contains("/ls 2"))
        assertFalse(listing.contains("\u4f1a\u8bdd1 (session-1)"))
    }
}
