package com.astrbot.android.core.runtime.session

/**
 * Compatibility facade for legacy tests and deferred call sites. Production
 * runtime paths should inject [SessionLockCoordinator].
 */
object ConversationSessionLockManager : SessionLockCoordinator {
    private val coordinator = DefaultSessionLockCoordinator()

    override suspend fun <T> withLock(
        sessionId: String,
        block: suspend () -> T,
    ): T = coordinator.withLock(sessionId, block)
}
