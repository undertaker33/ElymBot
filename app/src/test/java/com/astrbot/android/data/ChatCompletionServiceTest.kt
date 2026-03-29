package com.astrbot.android.data

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionServiceTest {
    @Test
    fun openai_stream_chunk_with_null_content_produces_empty_delta() {
        val chunk = """
            {"choices":[{"delta":{"content":null}}]}
        """.trimIndent()

        assertEquals("", ChatCompletionService.extractOpenAiStyleStreamingContentForTests(chunk))
    }

    @Test
    fun openai_stream_chunk_with_role_only_produces_empty_delta() {
        val chunk = """
            {"choices":[{"delta":{"role":"assistant"}}]}
        """.trimIndent()

        assertEquals("", ChatCompletionService.extractOpenAiStyleStreamingContentForTests(chunk))
    }

    @Test
    fun openai_stream_chunk_with_text_content_keeps_text_delta() {
        val chunk = """
            {"choices":[{"delta":{"content":"hello"}}]}
        """.trimIndent()

        assertEquals("hello", ChatCompletionService.extractOpenAiStyleStreamingContentForTests(chunk))
    }

    @Test
    fun image_route_requires_explicit_caption_provider_selection() {
        val plan = ChatCompletionService.resolveImageHandlingPlanForTests(
            provider = plainChatProvider(),
            messages = imageMessages(),
            config = ConfigProfile(
                imageCaptionTextEnabled = true,
                defaultVisionProviderId = "",
            ),
            availableProviders = listOf(multimodalCaptionProvider()),
        )

        assertEquals(ChatCompletionService.ImageHandlingMode.STRIP_ATTACHMENTS, plan.mode)
        assertEquals(ChatCompletionService.ImageHandlingReason.CAPTION_PROVIDER_NOT_SELECTED, plan.reason)
    }

    @Test
    fun image_route_uses_selected_caption_provider_when_chat_model_cannot_read_images() {
        val plan = ChatCompletionService.resolveImageHandlingPlanForTests(
            provider = plainChatProvider(),
            messages = imageMessages(),
            config = ConfigProfile(
                imageCaptionTextEnabled = true,
                defaultVisionProviderId = "vision-1",
            ),
            availableProviders = listOf(multimodalCaptionProvider()),
        )

        assertEquals(ChatCompletionService.ImageHandlingMode.CAPTION_TEXT, plan.mode)
        assertEquals(ChatCompletionService.ImageHandlingReason.CAPTION_PROVIDER_SELECTED, plan.reason)
        assertEquals("vision-1", plan.captionProvider?.id)
    }

    @Test
    fun image_route_prefers_direct_multimodal_chat_when_chat_model_supports_images() {
        val plan = ChatCompletionService.resolveImageHandlingPlanForTests(
            provider = multimodalChatProvider(),
            messages = imageMessages(),
            config = ConfigProfile(
                imageCaptionTextEnabled = true,
                defaultVisionProviderId = "vision-1",
            ),
            availableProviders = listOf(multimodalCaptionProvider()),
        )

        assertEquals(ChatCompletionService.ImageHandlingMode.DIRECT_MULTIMODAL, plan.mode)
        assertEquals(ChatCompletionService.ImageHandlingReason.CHAT_PROVIDER_SUPPORTS_IMAGES, plan.reason)
        assertEquals(null, plan.captionProvider)
    }

    private fun plainChatProvider(): ProviderProfile {
        return ProviderProfile(
            id = "chat-1",
            name = "Plain Chat",
            baseUrl = "https://example.com",
            model = "text-only",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.UNSUPPORTED,
            multimodalProbeSupport = FeatureSupportState.UNSUPPORTED,
        )
    }

    private fun multimodalChatProvider(): ProviderProfile {
        return ProviderProfile(
            id = "chat-mm",
            name = "Multimodal Chat",
            baseUrl = "https://example.com",
            model = "gpt-4o",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
            multimodalProbeSupport = FeatureSupportState.SUPPORTED,
        )
    }

    private fun multimodalCaptionProvider(): ProviderProfile {
        return ProviderProfile(
            id = "vision-1",
            name = "Vision Model",
            baseUrl = "https://example.com",
            model = "vision-model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
            multimodalProbeSupport = FeatureSupportState.SUPPORTED,
        )
    }

    private fun imageMessages(): List<ConversationMessage> {
        return listOf(
            ConversationMessage(
                id = "m1",
                role = "user",
                content = "look",
                timestamp = 1L,
                attachments = listOf(
                    ConversationAttachment(
                        id = "a1",
                        type = "image",
                        mimeType = "image/jpeg",
                        fileName = "photo.jpg",
                        base64Data = "abc",
                    ),
                ),
            ),
        )
    }
}
