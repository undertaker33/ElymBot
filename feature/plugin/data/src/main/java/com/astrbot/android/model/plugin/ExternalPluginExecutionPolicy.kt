package com.astrbot.android.model.plugin

object PluginTriggerContracts {
    val onlineHostTriggers = setOf(
        PluginTriggerSource.OnPluginEntryClick,
        PluginTriggerSource.OnCommand,
        PluginTriggerSource.BeforeSendMessage,
        PluginTriggerSource.AfterModelResponse,
    )

    val residualCompatOnlyTriggers = setOf(
        PluginTriggerSource.OnMessageReceived,
        PluginTriggerSource.OnSchedule,
        PluginTriggerSource.OnConversationEnter,
    )

    fun isOnlineHostTrigger(trigger: PluginTriggerSource): Boolean = trigger in onlineHostTriggers

    fun isResidualCompatOnlyTrigger(trigger: PluginTriggerSource): Boolean = trigger in residualCompatOnlyTriggers
}

