package com.astrbot.android.data

import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object PersonaRepository {
    private val _personas = MutableStateFlow(
        listOf(
            PersonaProfile(
                id = "default",
                name = "Default Assistant",
                tag = "Default",
                systemPrompt = "You are a concise, reliable QQ assistant.",
                enabledTools = setOf("web_search", "tts"),
                maxContextMessages = 12,
            ),
        ),
    )

    val personas: StateFlow<List<PersonaProfile>> = _personas.asStateFlow()

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        val persona = PersonaProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            tag = tag.trim(),
            systemPrompt = systemPrompt,
            enabledTools = enabledTools,
            defaultProviderId = defaultProviderId,
            maxContextMessages = maxContextMessages,
        )
        _personas.value = _personas.value + persona
        RuntimeLogRepository.append(
            "Persona added: ${persona.name}, defaultProvider=${persona.defaultProviderId.ifBlank { "none" }}",
        )
    }

    fun update(profile: PersonaProfile) {
        _personas.value = _personas.value.map { current ->
            if (current.id == profile.id) profile else current
        }
        RuntimeLogRepository.append("Persona updated: ${profile.name}")
    }

    fun toggleEnabled(id: String) {
        _personas.value = _personas.value.map { item ->
            if (item.id == id) {
                val updated = item.copy(enabled = !item.enabled)
                RuntimeLogRepository.append("Persona toggled: ${updated.name} enabled=${updated.enabled}")
                updated
            } else {
                item
            }
        }
    }

    fun delete(id: String) {
        val removed = _personas.value.firstOrNull { it.id == id }
        _personas.value = _personas.value.filterNot { it.id == id }
        if (removed != null) {
            RuntimeLogRepository.append("Persona deleted: ${removed.name}")
        }
    }
}
