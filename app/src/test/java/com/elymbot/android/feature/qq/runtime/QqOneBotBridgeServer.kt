package com.elymbot.android.feature.qq.runtime

import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.model.chat.ConversationAttachment

internal object QqOneBotBridgeServer : BaseQqOneBotBridgeRuntime() {
    @Volatile
    private var appChatPluginRuntimeOverrideForTests: AppChatLlmPipelineRuntime? = null

    @Volatile
    private var runtimeDependencies: QqOneBotRuntimeDependencies? = null

    @Volatile
    private var runtimeGraphFactoryOverrideForTests: QqRuntimeGraphFactory? = null

    @Volatile
    private var replySenderOverrideForTests: ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? =
        null

    internal fun setAppChatPluginRuntimeOverrideForTests(runtime: AppChatLlmPipelineRuntime?) {
        appChatPluginRuntimeOverrideForTests = runtime
    }

    internal fun setReplySenderOverrideForTests(
        sender: ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
    ) {
        replySenderOverrideForTests = sender
    }

    override fun requireRuntimeDependencies(): QqOneBotRuntimeDependencies {
        return runtimeDependencies
            ?: error("QqOneBotBridgeServer test facade requires runtime dependencies.")
    }

    override fun runtimeGraphFactory(): QqRuntimeGraphFactory {
        return runtimeGraphFactoryOverrideForTests
            ?: error("QqOneBotBridgeServer test facade requires a runtime graph factory.")
    }

    override fun currentAppChatPluginRuntime(): AppChatLlmPipelineRuntime =
        appChatPluginRuntimeOverrideForTests ?: requireRuntimeDependencies().appChatPluginRuntime

    override fun currentReplySenderOverride():
        ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? = replySenderOverrideForTests
}
