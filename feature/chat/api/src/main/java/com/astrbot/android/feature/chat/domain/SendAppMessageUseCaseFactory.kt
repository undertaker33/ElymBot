package com.astrbot.android.feature.chat.domain

import com.astrbot.android.feature.conversation.domain.ConversationRepositoryPort
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
