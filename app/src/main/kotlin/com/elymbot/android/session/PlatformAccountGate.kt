package com.elymbot.android.session

/** Decision for comparing a captured QQ route to the account currently reported by its handle. */
internal enum class PlatformAccountGate {
    MATCH,
    WAITING_FOR_ACCOUNT,
    ACCOUNT_CHANGED,
}

internal fun platformAccountGate(activeAccountId: String?, expectedAccountId: String): PlatformAccountGate {
    val activeAccount = activeAccountId?.trim().orEmpty()
    if (activeAccount.isEmpty()) return PlatformAccountGate.WAITING_FOR_ACCOUNT
    return if (activeAccount == expectedAccountId) PlatformAccountGate.MATCH else PlatformAccountGate.ACCOUNT_CHANGED
}
