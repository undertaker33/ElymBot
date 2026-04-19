package com.astrbot.android.core.common.profile

/**
 * Task10 Phase3 – Task C: Provider/Persona delete reference guard.
 *
 * These guards allow the application bootstrap layer to register reference-checkers
 * without creating cross-feature imports inside the individual feature repositories.
 *
 * Default behavior (no checker registered): references are assumed NOT to exist,
 * so deletes are allowed. Production wiring happens in AppBootstrapper.
 */

class PersonaInUseException(val personaId: String) :
    IllegalStateException("Persona '$personaId' is still referenced by at least one bot and cannot be deleted.")

class ProviderInUseException(val providerId: String) :
    IllegalStateException("Provider '$providerId' is still referenced by at least one config or bot and cannot be deleted.")

object PersonaReferenceGuard {
    @Volatile
    private var checker: ((personaId: String) -> Boolean) = { false }

    /**
     * Register the checker that determines whether a persona is in use.
     * Called by AppBootstrapper during bootstrap.
     */
    fun register(check: (personaId: String) -> Boolean) {
        checker = check
    }

    fun isInUse(personaId: String): Boolean = checker(personaId)

    /**
     * Throws [PersonaInUseException] if the persona is currently referenced.
     */
    fun requireNotInUse(personaId: String) {
        if (isInUse(personaId)) throw PersonaInUseException(personaId)
    }
}

object ProviderReferenceGuard {
    @Volatile
    private var checker: ((providerId: String) -> Boolean) = { false }

    /**
     * Register the checker that determines whether a provider is in use.
     * Called by AppBootstrapper during bootstrap.
     */
    fun register(check: (providerId: String) -> Boolean) {
        checker = check
    }

    fun isInUse(providerId: String): Boolean = checker(providerId)

    /**
     * Throws [ProviderInUseException] if the provider is currently referenced.
     */
    fun requireNotInUse(providerId: String) {
        if (isInUse(providerId)) throw ProviderInUseException(providerId)
    }
}
