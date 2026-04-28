package com.astrbot.android.core.common.profile

class PersonaInUseException(val personaId: String) :
    IllegalStateException("Persona '$personaId' is still referenced by at least one bot and cannot be deleted.")

class ProviderInUseException(val providerId: String) :
    IllegalStateException("Provider '$providerId' is still referenced by at least one config or bot and cannot be deleted.")

fun interface PersonaReferenceChecker {
    fun isInUse(personaId: String): Boolean
}

fun interface ProviderReferenceChecker {
    fun isInUse(providerId: String): Boolean
}
