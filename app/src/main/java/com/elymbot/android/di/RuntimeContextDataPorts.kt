
package com.elymbot.android.di

import com.elymbot.android.core.runtime.context.RuntimeContextDataPort
import com.elymbot.android.core.runtime.context.RuntimeConfigSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConversationSessionSnapshot
import com.elymbot.android.core.runtime.context.RuntimePersonaSnapshot
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.context.RuntimeResourceCenterCompatibilitySnapshot
import com.elymbot.android.di.runtime.context.toResourceConfigSnapshot
import com.elymbot.android.di.runtime.context.toRuntimeConfigSnapshot
import com.elymbot.android.di.runtime.context.toRuntimeConversationSessionSnapshot
import com.elymbot.android.di.runtime.context.toRuntimePersonaSnapshot
import com.elymbot.android.di.runtime.context.toRuntimeProviderSnapshot
import com.elymbot.android.di.runtime.context.toRuntimeResourceCenterCompatibilitySnapshot
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.resource.domain.ResourceCenterPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ProductionRuntimeContextDataPort @Inject constructor(
    private val configRepository: ConfigRepositoryPort,
    private val providerRepository: ProviderRepositoryPort,
    private val personaRepository: PersonaRepositoryPort,
    private val conversationRepository: ConversationRepositoryPort,
    private val resourceCenter: ResourceCenterPort,
) : RuntimeContextDataPort {
    override fun resolveConfig(configProfileId: String): RuntimeConfigSnapshot =
        configRepository.resolve(configProfileId).toRuntimeConfigSnapshot()

    override fun listProviders(): List<RuntimeProviderSnapshot> =
        providerRepository.snapshotProfiles().map { it.toRuntimeProviderSnapshot() }

    override fun findEnabledPersona(personaId: String): RuntimePersonaSnapshot? {
        return personaRepository.snapshotProfiles()
            .firstOrNull { it.id == personaId && it.enabled }
            ?.toRuntimePersonaSnapshot()
    }

    override fun session(sessionId: String): RuntimeConversationSessionSnapshot =
        conversationRepository.session(sessionId).toRuntimeConversationSessionSnapshot()

    override fun compatibilitySnapshotForConfig(
        config: RuntimeConfigSnapshot,
    ): RuntimeResourceCenterCompatibilitySnapshot {
        return resourceCenter.compatibilitySnapshotForConfig(config.toResourceConfigSnapshot())
            .toRuntimeResourceCenterCompatibilitySnapshot()
    }
}

