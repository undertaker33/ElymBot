package com.elymbot.android.feature.chat.runtime

import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.llm.LlmInvocationResult
import com.elymbot.android.core.runtime.llm.LlmToolDefinition
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

interface AppChatRuntimeBindings {
    val bots: StateFlow<List<BotProfile>>
    val providers: StateFlow<List<ProviderProfile>>
    val runtimeContextResolverPort: RuntimeContextResolverPort

    fun session(sessionId: String): ConversationSession

    fun resolveConfig(profileId: String): ConfigProfile

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    suspend fun sendConfiguredChatWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
    ): LlmInvocationResult

    suspend fun sendConfiguredChatStreamWithTools(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
        tools: List<LlmToolDefinition>,
        onDelta: suspend (String) -> Unit,
        onToolCallDelta: suspend (index: Int, name: String, argumentsFragment: String) -> Unit,
    ): LlmInvocationResult

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment

    fun log(message: String)
}
