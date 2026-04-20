package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.model.PersonaProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaRepository: PersonaRepositoryPort,
) : ViewModel() {
    val personas: StateFlow<List<PersonaProfile>> = personaRepository.personas

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            personaRepository.add(
                PersonaProfile(
                    id = "",
                    name = name,
                    tag = tag,
                    systemPrompt = systemPrompt,
                    enabledTools = enabledTools,
                    defaultProviderId = defaultProviderId,
                    maxContextMessages = maxContextMessages,
                ),
            )
        }
    }

    fun update(profile: PersonaProfile) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            personaRepository.update(profile)
        }
    }

    fun toggleEnabled(id: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            personaRepository.toggleEnabled(id)
        }
    }

    fun delete(id: String): Result<Unit> {
        return runCatching {
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                personaRepository.delete(id)
            }
        }
    }
}
