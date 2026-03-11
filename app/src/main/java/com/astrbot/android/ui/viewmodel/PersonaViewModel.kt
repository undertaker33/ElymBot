package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.model.PersonaProfile
import kotlinx.coroutines.flow.StateFlow

class PersonaViewModel : ViewModel() {
    val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        PersonaRepository.add(name, tag, systemPrompt, enabledTools, defaultProviderId, maxContextMessages)
    }

    fun update(profile: PersonaProfile) {
        PersonaRepository.update(profile)
    }

    fun toggleEnabled(id: String) {
        PersonaRepository.toggleEnabled(id)
    }

    fun delete(id: String) {
        PersonaRepository.delete(id)
    }
}
