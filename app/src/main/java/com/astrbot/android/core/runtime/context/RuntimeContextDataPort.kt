package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.chat.ConversationSession

interface RuntimeContextDataPort {
    fun resolveConfig(configProfileId: String): ConfigProfile
    fun listProviders(): List<ProviderProfile>
    fun findEnabledPersona(personaId: String): PersonaProfile?
    fun session(sessionId: String): ConversationSession
    fun compatibilitySnapshotForConfig(config: ConfigProfile): ResourceCenterCompatibilitySnapshot
}
