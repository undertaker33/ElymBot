package com.elymbot.android.core.runtime.session

internal object ConversationSessionLockManager : SessionLockCoordinator {
    private val coordinator = DefaultSessionLockCoordinator()

    override suspend fun <T> withLock(
        sessionId: String,
        block: suspend () -> T,
    ): T = coordinator.withLock(sessionId, block)
}
