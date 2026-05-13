package com.astrbot.android

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

object AppStrings {
    fun get(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any,
    ): String {
        return if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs)
        }
    }

    fun getForLanguageTag(
        context: Context,
        languageTag: String,
        @StringRes resId: Int,
        vararg formatArgs: Any,
    ): String {
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

class AppStringResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun get(@StringRes resId: Int, vararg formatArgs: Any): String {
        return AppStrings.get(context, resId, *formatArgs)
    }

    fun getForLanguageTag(
        languageTag: String,
        @StringRes resId: Int,
        vararg formatArgs: Any,
    ): String {
        return AppStrings.getForLanguageTag(context, languageTag, resId, *formatArgs)
    }
}
