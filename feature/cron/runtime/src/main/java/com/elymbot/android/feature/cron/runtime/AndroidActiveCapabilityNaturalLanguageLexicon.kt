package com.elymbot.android.feature.cron.runtime

import android.content.Context
import com.elymbot.android.feature.cron.domain.ActiveCapabilityNaturalLanguageLexicon

fun activeCapabilityNaturalLanguageLexiconFromResources(
    context: Context,
): ActiveCapabilityNaturalLanguageLexicon {
    val resources = context.resources
    return ActiveCapabilityNaturalLanguageLexicon(
        tomorrowTokens = resources.getStringArray(R.array.active_capability_nl_tomorrow_tokens).toList(),
        dayAfterTomorrowTokens = resources.getStringArray(R.array.active_capability_nl_day_after_tomorrow_tokens).toList(),
        tonightTokens = resources.getStringArray(R.array.active_capability_nl_tonight_tokens).toList(),
        morningTokens = resources.getStringArray(R.array.active_capability_nl_morning_tokens).toList(),
        noonTokens = resources.getStringArray(R.array.active_capability_nl_noon_tokens).toList(),
        afternoonTokens = resources.getStringArray(R.array.active_capability_nl_afternoon_tokens).toList(),
        eveningTokens = resources.getStringArray(R.array.active_capability_nl_evening_tokens).toList(),
        halfHourTokens = resources.getStringArray(R.array.active_capability_nl_half_hour_tokens).toList(),
        oneAndHalfHourTokens = resources.getStringArray(R.array.active_capability_nl_one_and_half_hour_tokens).toList(),
    )
}
