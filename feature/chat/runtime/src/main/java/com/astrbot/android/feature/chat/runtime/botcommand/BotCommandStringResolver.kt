package com.astrbot.android.feature.chat.runtime.botcommand

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

fun interface BotCommandStringResolver {
    fun get(languageTag: String, @StringRes resId: Int, vararg formatArgs: Any): String

    companion object {
        val fallback = BotCommandStringResolver { _, _, _ -> "" }
    }
}

class AndroidBotCommandStringResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : BotCommandStringResolver {
    override fun get(languageTag: String, @StringRes resId: Int, vararg formatArgs: Any): String {
        val locale = Locale.forLanguageTag(languageTag.ifBlank { Locale.getDefault().toLanguageTag() })
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = context.createConfigurationContext(configuration)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }
}
