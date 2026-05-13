package com.astrbot.android.feature.persona.data

import com.astrbot.android.feature.persona.domain.defaultPersonaEnabledTools
import com.astrbot.android.feature.persona.domain.model.PersonaProfile
import com.astrbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
import kotlinx.coroutines.flow.StateFlow

object FeaturePersonaRepository {
    @Volatile
    private var delegate: FeaturePersonaRepositoryStore? = null

    internal fun installDelegate(store: FeaturePersonaRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeaturePersonaRepositoryStore {
        return checkNotNull(delegate) {
            "FeaturePersonaRepository test facade was accessed before FeaturePersonaRepositoryStore was installed."
        }
    }

    val personas: StateFlow<List<PersonaProfile>>
        get() = repository().personas

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) = repository().add(name, tag, systemPrompt, enabledTools, defaultProviderId, maxContextMessages)

    fun update(profile: PersonaProfile) = repository().update(profile)

    fun toggleEnabled(id: String) = repository().toggleEnabled(id)

    fun delete(id: String) = repository().delete(id)

    fun snapshotProfiles(): List<PersonaProfile> = repository().snapshotProfiles()

    fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? =
        repository().snapshotToolEnablement(personaId)

    fun restoreProfiles(profiles: List<PersonaProfile>) = repository().restoreProfiles(profiles)

    fun defaultEnabledTools(): Set<String> = defaultPersonaEnabledTools()
}
