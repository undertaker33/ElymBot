package com.elymbot.android.ui.common

import com.elymbot.android.core.common.profile.ProfileCatalogKind

import com.elymbot.android.core.common.profile.LastProfileDeletionBlockedException

import android.content.Context
import android.widget.Toast
import com.elymbot.android.core.ui.R

fun showProfileDeletionFailureToast(
    context: Context,
    throwable: Throwable,
) {
    val messageResId = when ((throwable as? LastProfileDeletionBlockedException)?.kind) {
        ProfileCatalogKind.BOT -> R.string.bot_delete_last_blocked
        ProfileCatalogKind.PERSONA -> R.string.persona_delete_last_blocked
        null -> return
    }
    Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
}
