package com.elymbot.android.feature.qq.runtime

internal object QqSlashCommandPermissionPolicy {
    const val ADMIN_ONLY_NOTICE = "Slash commands are restricted to administrators."

    fun canTrigger(
        adminOnlyEnabled: Boolean,
        isAdmin: Boolean,
    ): Boolean {
        return !adminOnlyEnabled || isAdmin
    }
}
