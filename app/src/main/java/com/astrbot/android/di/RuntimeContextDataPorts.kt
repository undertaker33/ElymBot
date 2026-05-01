
package com.astrbot.android.di

import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.core.runtime.context.RuntimeConfigSnapshot
import com.astrbot.android.core.runtime.context.RuntimeConversationSessionSnapshot
import com.astrbot.android.core.runtime.context.RuntimePersonaSnapshot
import com.astrbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.astrbot.android.core.runtime.context.RuntimeResourceCenterCompatibilitySnapshot
import com.astrbot.android.di.runtime.context.toResourceConfigSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeConfigSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeConversationSessionSnapshot
import com.astrbot.android.di.runtime.context.toRuntimePersonaSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeProviderSnapshot
import com.astrbot.android.di.runtime.context.toRuntimeResourceCenterCompatibilitySnapshot
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.resource.domain.ResourceCenterPort
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
