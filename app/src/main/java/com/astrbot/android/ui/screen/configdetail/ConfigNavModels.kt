package com.astrbot.android.ui.screen

import androidx.annotation.StringRes
import com.astrbot.android.R

internal enum class ConfigSection(@StringRes val titleRes: Int) {
    ModelSettings(R.string.config_section_model_settings),
    KnowledgeBase(R.string.config_section_knowledge_base),
    ContextStrategy(R.string.config_section_context_strategy),
    Search(R.string.config_section_search),
    Automation(R.string.config_section_automation),
    Advanced(R.string.config_section_advanced),
    Admin(R.string.config_section_admin),
    Session(R.string.config_section_session),
    Wake(R.string.config_section_wake),
    Reply(R.string.config_section_reply),
    Whitelist(R.string.config_section_whitelist),
    IgnorePermission(R.string.config_section_ignore_permission),
    RateLimit(R.string.config_section_rate_limit),
    Keyword(R.string.config_section_keyword),
}

internal data class ConfigNavGroup(
    @StringRes val titleRes: Int,
    val children: List<ConfigSection>,
)
