package com.elymbot.android.feature.chat.domain

import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import javax.inject.Inject

class SendAppMessageUseCaseFactory @Inject constructor(
    private val conversations: ConversationRepositoryPort,
) {
    fun create(runtime: AppChatRuntimePort): SendAppMessageUseCase {
        return SendAppMessageUseCase(
            conversations = conversations,
            runtime = runtime,
        )
    }
}
