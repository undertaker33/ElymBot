package com.astrbot.android.core.runtime.context

interface RuntimeContextDataPort {
    fun resolveConfig(configProfileId: String): RuntimeConfigSnapshot
    fun listProviders(): List<RuntimeProviderSnapshot>
    fun findEnabledPersona(personaId: String): RuntimePersonaSnapshot?
    fun session(sessionId: String): RuntimeConversationSessionSnapshot
    fun compatibilitySnapshotForConfig(config: RuntimeConfigSnapshot): RuntimeResourceCenterCompatibilitySnapshot
}
