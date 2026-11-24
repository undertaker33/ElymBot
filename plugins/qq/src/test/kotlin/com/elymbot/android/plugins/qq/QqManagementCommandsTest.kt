package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.MessageRoute
import com.elymbot.core.contract.PlatformDeliveryFailure
import com.elymbot.core.contract.PlatformDeliveryResult
import com.elymbot.core.contract.PlatformInboundEnvelope
import com.elymbot.core.contract.PlatformInboundSink
import com.elymbot.core.contract.PlatformInboundResult
import com.elymbot.core.contract.QqGroupMember
import com.elymbot.core.contract.QqPlatformCommandSettings
import com.elymbot.core.contract.QqPlatformCommandSettingsUpdateResult
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqManagementCommandsTest {
    @Test
    fun aliasCommandPersistsAdminAclOnceWhenReplyReceiptIsUnknown() = runBlocking {
        val harness = harness(
            settings = settings(commandAliases = mapOf("grant" to "op")),
            receipt = PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.CONNECTION_LOST),
        )
        try {
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }
            val payload = privatePayload("evt-1", "/grant 3333")

            val result = harness.adapter.accept(payload, harness.store.settings.value.settings, sink)
            val duplicate = harness.adapter.accept(payload, harness.store.settings.value.settings, sink)
            val persisted = QqPlatformSettingsStore(harness.directory)

            assertEquals(
                QqOneBotIngressResult.CommandHandled(
                    settingsChanged = true,
                    retryable = false,
                    replyResult = PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.CONNECTION_LOST),
                ),
                result,
            )
            assertEquals(QqOneBotIngressResult.Duplicate, duplicate)
            assertTrue("3333" in persisted.settings.value.settings.adminUserIds)
            assertEquals(1, harness.replies.size)
            assertEquals("private:2000", harness.replies.single().target.conversationId)
            assertEquals("1000", harness.replies.single().target.accountId)
            assertEquals(0, hostCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun whitelistCommandsPreserveLegacyNumericAndGroupMemberScopes() = runBlocking {
        val harness = harness(settings = settings())
        try {
            val sink = PlatformInboundSink { PlatformInboundResult.Accepted }

            harness.adapter.accept(groupPayload("evt-1", "/wl 4000_3000"), harness.settings(), sink)
            assertTrue(QqGroupMember("4000", "3000") in harness.settings().allowedGroupMembers)
            assertFalse("4000" in harness.settings().allowedUserIds)
            assertFalse("4000" in harness.settings().allowedPrivateUserIds)
            assertTrue(
                QqInboundPolicy().evaluate(
                    parseEvent(groupPayload("acl-group-member", "hello", senderId = "4000", mentionSelf = true)),
                    harness.settings(),
                ) is QqInboundDecision.Trigger,
            )

            harness.adapter.accept(groupPayload("evt-2", "/wl 5000"), harness.settings(), sink)
            assertTrue("5000" in harness.settings().allowedUserIds)
            assertTrue("5000" in harness.settings().allowedGroupIds)
            assertTrue(
                QqInboundPolicy().evaluate(
                    parseEvent(privatePayload("acl-user", "hello", senderId = "5000")),
                    harness.settings(),
                ) is QqInboundDecision.Trigger,
            )
            assertTrue(
                QqInboundPolicy().evaluate(
                    parseEvent(groupPayload("acl-group", "hello", senderId = "7000", groupId = "5000", mentionSelf = true)),
                    harness.settings(),
                ) is QqInboundDecision.Trigger,
            )

            harness.adapter.accept(groupPayload("evt-3", "/dwl 5000"), harness.settings(), sink)
            assertFalse("5000" in harness.settings().allowedUserIds)
            assertFalse("5000" in harness.settings().allowedGroupIds)
            assertTrue("2000" in harness.settings().allowedPrivateUserIds)
            assertTrue(QqGroupMember("4000", "3000") in harness.settings().allowedGroupMembers)
            assertEquals(
                QqInboundDecision.Dropped(QqInboundDropReason.NOT_ALLOWED),
                QqInboundPolicy().evaluate(
                    parseEvent(privatePayload("acl-user-removed", "hello", senderId = "5000")),
                    harness.settings(),
                ),
            )
            assertEquals(
                QqInboundDecision.Dropped(QqInboundDropReason.NOT_ALLOWED),
                QqInboundPolicy().evaluate(
                    parseEvent(groupPayload("acl-group-removed", "hello", senderId = "7000", groupId = "5000", mentionSelf = true)),
                    harness.settings(),
                ),
            )
            assertTrue(
                QqInboundPolicy().evaluate(
                    parseEvent(privatePayload("acl-owner-kept", "hello")),
                    harness.settings(),
                ) is QqInboundDecision.Trigger,
            )

            harness.adapter.accept(groupPayload("evt-4", "/dwl 4000_3000"), harness.settings(), sink)
            assertFalse(QqGroupMember("4000", "3000") in harness.settings().allowedGroupMembers)
            assertEquals(
                QqInboundDecision.Dropped(QqInboundDropReason.NOT_ALLOWED),
                QqInboundPolicy().evaluate(
                    parseEvent(groupPayload("acl-member-removed", "hello", senderId = "4000", mentionSelf = true)),
                    harness.settings(),
                ),
            )
            assertEquals("group:3000", harness.replies.first().target.conversationId)
        } finally {
            harness.close()
        }
    }

    @Test
    fun managementCommandsRequireExistingAdminAndUseConfiguredDenialReply() = runBlocking {
        val harness = harness(
            settings = settings(
                adminUserIds = setOf("9000"),
            ),
            commandSettings = QqPlatformCommandSettings(replyWhenPermissionDenied = true),
        )
        try {
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }

            val result = harness.adapter.accept(
                privatePayload("evt-1", "/op 3000"),
                harness.settings(),
                sink,
            )

            assertTrue(result is QqOneBotIngressResult.CommandHandled)
            assertEquals("Slash commands are restricted to administrators.", harness.replies.single().text)
            assertEquals(setOf("9000"), harness.settings().adminUserIds)
            assertEquals(0, hostCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun permissionDenialIsSilentByDefault() = runBlocking {
        val harness = harness(settings = settings(adminUserIds = setOf("9000")))
        try {
            val result = harness.adapter.accept(
                privatePayload("evt-1", "/op 3000"),
                harness.settings(),
                PlatformInboundSink { PlatformInboundResult.Accepted },
            )

            assertTrue(result is QqOneBotIngressResult.CommandHandled)
            assertTrue(harness.replies.isEmpty())
            assertEquals(setOf("9000"), harness.settings().adminUserIds)
        } finally {
            harness.close()
        }
    }

    @Test
    fun whitelistAndPrivateWakeDenialsUseConfiguredNoticeOnOriginalRoute() = runBlocking {
        val harness = harness(
            settings = settings(privateChatRequiresWakeWord = true),
            commandSettings = QqPlatformCommandSettings(replyWhenPermissionDenied = true),
        )
        try {
            val sink = PlatformInboundSink { PlatformInboundResult.Accepted }

            val whitelistDenied = harness.adapter.accept(
                privatePayload("whitelist-denied", "hello", senderId = "3000"),
                harness.settings(),
                sink,
            )
            val wakeWordDenied = harness.adapter.accept(
                privatePayload("wake-word-denied", "hello"),
                harness.settings(),
                sink,
            )

            assertEquals(QqOneBotIngressResult.Dropped(QqInboundDropReason.NOT_ALLOWED), whitelistDenied)
            assertEquals(QqOneBotIngressResult.Dropped(QqInboundDropReason.WAKE_WORD_REQUIRED), wakeWordDenied)
            assertEquals(
                listOf(
                    SentReply(
                        MessageRoute("qq", "private:3000", accountId = "1000", actorId = "3000"),
                        "Permission denied by whitelist.",
                    ),
                    SentReply(
                        MessageRoute("qq", "private:2000", accountId = "1000", actorId = "2000"),
                        "Wake word required.",
                    ),
                ),
                harness.replies,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun policyPermissionDenialsStaySilentByDefault() = runBlocking {
        val harness = harness(settings = settings(privateChatRequiresWakeWord = true))
        try {
            val sink = PlatformInboundSink { PlatformInboundResult.Accepted }

            val whitelistDenied = harness.adapter.accept(
                privatePayload("silent-whitelist-denied", "hello", senderId = "3000"),
                harness.settings(),
                sink,
            )
            val wakeWordDenied = harness.adapter.accept(
                privatePayload("silent-wake-word-denied", "hello"),
                harness.settings(),
                sink,
            )

            assertEquals(QqOneBotIngressResult.Dropped(QqInboundDropReason.NOT_ALLOWED), whitelistDenied)
            assertEquals(QqOneBotIngressResult.Dropped(QqInboundDropReason.WAKE_WORD_REQUIRED), wakeWordDenied)
            assertTrue(harness.replies.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun keywordBlockAlwaysNotifiesButUntriggeredPublicContextStaysSilent() = runBlocking {
        val harness = harness(
            settings = settings(
                keywordDetectionEnabled = true,
                keywordPatterns = listOf("blocked"),
                publicGroupHistoryGroupIds = setOf("3000"),
            ),
            receipt = PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.CONNECTION_LOST),
        )
        try {
            val forwarded = mutableListOf<PlatformInboundEnvelope>()
            val sink = PlatformInboundSink { envelope ->
                forwarded += envelope
                PlatformInboundResult.Accepted
            }

            val keywordPayload = privatePayload("keyword-blocked", "blocked")
            val blocked = harness.adapter.accept(keywordPayload, harness.settings(), sink)
            val duplicate = harness.adapter.accept(keywordPayload, harness.settings(), sink)
            val contextOnly = harness.adapter.accept(
                groupPayload("public-context", "blocked"),
                harness.settings(),
                sink,
            )

            assertEquals(QqOneBotIngressResult.Dropped(QqInboundDropReason.KEYWORD_BLOCKED), blocked)
            assertEquals(QqOneBotIngressResult.Duplicate, duplicate)
            assertEquals(
                listOf(SentReply(
                    MessageRoute("qq", "private:2000", accountId = "1000", actorId = "2000"),
                    "你的消息或者大模型的响应中包含不适当的内容，已被屏蔽。",
                )),
                harness.replies,
            )
            assertTrue(contextOnly is QqOneBotIngressResult.Forwarded)
            assertEquals(1, forwarded.size)
            assertEquals(com.elymbot.core.contract.PlatformInboundProcessing.CONTEXT_ONLY, forwarded.single().processing)
        } finally {
            harness.close()
        }
    }

    @Test
    fun rateLimitDropUsesConfiguredPermissionNoticeOnOriginalRoute() = runBlocking {
        var now = 0L
        val rateLimit = QqPlatformRateLimitSettings(
            windowMillis = 100,
            maxMessages = 1,
            strategy = QqPlatformRateLimitStrategy.DROP,
            maxStashedMessages = 0,
            maxStashAgeMillis = 1_000,
        )
        val harness = harness(
            settings = settings(privateRateLimit = rateLimit),
            commandSettings = QqPlatformCommandSettings(replyWhenPermissionDenied = true),
            receipt = PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.CONNECTION_LOST),
            rateLimiter = QqRateLimiter(nowMillis = { now }),
        )
        try {
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }

            val first = harness.adapter.accept(privatePayload("rate-first", "hello"), harness.settings(), sink)
            val rateLimitedPayload = privatePayload("rate-limited", "hello")
            val limited = harness.adapter.accept(rateLimitedPayload, harness.settings(), sink)
            val duplicate = harness.adapter.accept(rateLimitedPayload, harness.settings(), sink)

            assertTrue(first is QqOneBotIngressResult.Forwarded)
            assertEquals(
                QqOneBotIngressResult.RateLimited(QqRateLimitDisposition.DROPPED, emptyList()),
                limited,
            )
            assertEquals(QqOneBotIngressResult.Duplicate, duplicate)
            assertEquals(1, hostCalls)
            assertEquals(
                listOf(SentReply(
                    MessageRoute("qq", "private:2000", accountId = "1000", actorId = "2000"),
                    "Rate limit exceeded.",
                )),
                harness.replies,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun managementMutationIsRejectedForStaleAccountBeforeLocalAclChange() = runBlocking {
        val harness = harness(settings = settings())
        try {
            val adapter = QqOneBotInboundAdapter(
                commandHandler = QqManagementCommandHandler(
                    settingsStore = harness.store,
                    operationMutex = Mutex(),
                    isCurrentAccount = { false },
                ),
                commandReplySender = { _, text ->
                    harness.replies += SentReply(MessageRoute("qq", "private:2000", accountId = "1000"), text)
                    PlatformDeliveryResult.Delivered()
                },
            )

            val result = adapter.accept(
                privatePayload("stale-account", "/dwl 2000"),
                harness.settings(),
                PlatformInboundSink { PlatformInboundResult.Accepted },
            )

            assertEquals(
                QqOneBotIngressResult.CommandHandled(
                    settingsChanged = false,
                    retryable = false,
                    replyResult = null,
                ),
                result,
            )
            assertEquals(setOf("2000"), harness.settings().allowedUserIds)
            assertTrue(harness.replies.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun stashedManagementEventsRereadAclAfterEarlierReplayMutation() = runBlocking {
        var now = 0L
        val harness = harness(settings = settings(allowedPrivateUserIds = emptySet()))
        try {
            val rateLimit = QqPlatformRateLimitSettings(
                windowMillis = 100,
                maxMessages = 2,
                strategy = com.elymbot.core.contract.QqPlatformRateLimitStrategy.STASH,
                maxStashedMessages = 4,
                maxStashAgeMillis = 1_000,
            )
            val adapter = QqOneBotInboundAdapter(
                rateLimiter = QqRateLimiter(nowMillis = { now }),
                commandHandler = QqManagementCommandHandler(harness.store),
                currentSettingsProvider = { harness.settings() },
                commandReplySender = { event, text ->
                    harness.replies += SentReply(requireNotNull(event.message.target), text)
                    PlatformDeliveryResult.Delivered()
                },
            )
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }
            val initial = harness.settings().copy(
                privateRateLimit = rateLimit,
                adminPrivateBypassWhitelistEnabled = false,
                adminGroupBypassWhitelistEnabled = false,
            )
            harness.update(initial)

            adapter.accept(privatePayload("seed-1", "hello"), harness.settings(), sink)
            now = 1
            adapter.accept(privatePayload("seed-2", "hello"), harness.settings(), sink)
            now = 2
            assertTrue(adapter.accept(privatePayload("dwl", "/dwl 2000"), harness.settings(), sink)
                is QqOneBotIngressResult.RateLimited)
            now = 3
            assertTrue(adapter.accept(privatePayload("op", "/op 3333"), harness.settings(), sink)
                is QqOneBotIngressResult.RateLimited)

            now = 101
            adapter.releaseReady(
                sourceKey = "1000:private:2000",
                configuration = harness.settings(),
                sink = sink,
            )

            assertEquals(setOf("2000"), harness.settings().adminUserIds)
            assertTrue(harness.settings().allowedUserIds.isEmpty())
            assertEquals(2, hostCalls)
            assertEquals(1, harness.replies.size)
            assertEquals("Removed 2000 from the whitelist.", harness.replies.single().text)
        } finally {
            harness.close()
        }
    }

    @Test
    fun currentAllowedEventIsReevaluatedAfterEarlierReplayChangesAcl() = runBlocking {
        var now = 0L
        val harness = harness(settings = settings(allowedPrivateUserIds = emptySet()))
        try {
            val rateLimit = QqPlatformRateLimitSettings(
                windowMillis = 100,
                maxMessages = 2,
                strategy = com.elymbot.core.contract.QqPlatformRateLimitStrategy.STASH,
                maxStashedMessages = 4,
                maxStashAgeMillis = 1_000,
            )
            val adapter = QqOneBotInboundAdapter(
                rateLimiter = QqRateLimiter(nowMillis = { now }),
                commandHandler = QqManagementCommandHandler(harness.store),
                currentSettingsProvider = { harness.settings() },
                commandReplySender = { event, text ->
                    harness.replies += SentReply(requireNotNull(event.message.target), text)
                    PlatformDeliveryResult.Delivered()
                },
            )
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }
            harness.update(
                harness.settings().copy(
                    privateRateLimit = rateLimit,
                    adminPrivateBypassWhitelistEnabled = false,
                ),
            )

            adapter.accept(privatePayload("seed-1", "hello"), harness.settings(), sink)
            now = 1
            adapter.accept(privatePayload("seed-2", "hello"), harness.settings(), sink)
            now = 2
            assertTrue(adapter.accept(privatePayload("dwl", "/dwl 2000"), harness.settings(), sink)
                is QqOneBotIngressResult.RateLimited)

            now = 101
            val current = adapter.accept(privatePayload("op", "/op 3333"), harness.settings(), sink)

            assertEquals(QqOneBotIngressResult.Dropped(QqInboundDropReason.NOT_ALLOWED), current)
            assertTrue(harness.settings().allowedUserIds.isEmpty())
            assertEquals(setOf("2000"), harness.settings().adminUserIds)
            assertEquals(2, hostCalls)
            assertEquals(listOf("Removed 2000 from the whitelist."), harness.replies.map(SentReply::text))
        } finally {
            harness.close()
        }
    }

    @Test
    fun legacyGlobalGateBlocksHostCommandsOnlyWhenEnabled() = runBlocking {
        val harness = harness(
            settings = settings(
                adminUserIds = setOf("9000"),
            ),
            commandSettings = QqPlatformCommandSettings(
                pluginCommandsAdminOnlyEnabled = true,
                replyWhenPermissionDenied = true,
            ),
        )
        try {
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }

            val denied = harness.adapter.accept(privatePayload("evt-1", "/help"), harness.settings(), sink)
            assertTrue(denied is QqOneBotIngressResult.CommandHandled)
            assertEquals(0, hostCalls)
            assertEquals("Slash commands are restricted to administrators.", harness.replies.single().text)

            harness.updateCommandSettings(QqPlatformCommandSettings())
            val forwarded = harness.adapter.accept(privatePayload("evt-2", "/help"), harness.settings(), sink)
            assertTrue(forwarded is QqOneBotIngressResult.Forwarded)
            assertEquals(1, hostCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun invalidManagementArgumentsAreConsumedWithoutChangingSettings() = runBlocking {
        val harness = harness(settings = settings())
        try {
            val before = harness.settings()
            var hostCalls = 0
            val sink = PlatformInboundSink {
                hostCalls += 1
                PlatformInboundResult.Accepted
            }

            val result = harness.adapter.accept(
                groupPayload("evt-1", "/wl 2000_3000_4000"),
                before,
                sink,
            )

            assertTrue(result is QqOneBotIngressResult.CommandHandled)
            assertFalse((result as QqOneBotIngressResult.CommandHandled).settingsChanged)
            assertEquals(before, harness.settings())
            assertEquals("Usage: /wl <UMO>", harness.replies.single().text)
            assertEquals(0, hostCalls)
        } finally {
            harness.close()
        }
    }

    private suspend fun harness(
        settings: QqPlatformSettings,
        commandSettings: QqPlatformCommandSettings = QqPlatformCommandSettings(),
        receipt: PlatformDeliveryResult = PlatformDeliveryResult.Delivered(),
        rateLimiter: QqRateLimiter<PendingQqInbound> = QqRateLimiter(nowMillis = { System.currentTimeMillis() }),
    ): Harness {
        val directory = Files.createTempDirectory("qq-management-test").toFile()
        val store = QqPlatformSettingsStore(directory)
        check(store.updateSettings(0, settings) is QqPlatformSettingsUpdateResult.Saved)
        if (commandSettings != QqPlatformCommandSettings()) {
            check(store.updateCommandSettings(0, commandSettings) is QqPlatformCommandSettingsUpdateResult.Saved)
        }
        val replies = mutableListOf<SentReply>()
        val adapter = QqOneBotInboundAdapter(
            rateLimiter = rateLimiter,
            commandHandler = QqManagementCommandHandler(store),
            currentSettingsProvider = { store.settings.value.settings },
            currentCommandSettingsProvider = { store.commandSettings.value.settings },
            commandReplySender = { event, text ->
                replies += SentReply(requireNotNull(event.message.target), text)
                receipt
            },
        )
        return Harness(directory, store, adapter, replies)
    }

    private fun settings(
        adminUserIds: Set<String> = setOf("2000"),
        allowedPrivateUserIds: Set<String> = setOf("2000"),
        commandAliases: Map<String, String> = emptyMap(),
        privateChatRequiresWakeWord: Boolean = false,
        keywordDetectionEnabled: Boolean = false,
        keywordPatterns: List<String> = emptyList(),
        publicGroupHistoryGroupIds: Set<String> = emptySet(),
        privateRateLimit: QqPlatformRateLimitSettings? = null,
    ) = QqPlatformSettings(
        whitelistEnabled = true,
        allowedUserIds = setOf("2000"),
        allowedPrivateUserIds = allowedPrivateUserIds,
        adminUserIds = adminUserIds,
        privateChatRequiresWakeWord = privateChatRequiresWakeWord,
        keywordDetectionEnabled = keywordDetectionEnabled,
        keywordPatterns = keywordPatterns,
        publicGroupHistoryGroupIds = publicGroupHistoryGroupIds,
        commandAliases = commandAliases,
        privateRateLimit = privateRateLimit,
    )

    private fun privatePayload(messageId: String, text: String, senderId: String = "2000") =
        """{"post_type":"message","message_type":"private","self_id":"1000","user_id":"$senderId","message_id":"$messageId","message":[{"type":"text","data":{"text":"$text"}}]}"""

    private fun groupPayload(
        messageId: String,
        text: String,
        senderId: String = "2000",
        groupId: String = "3000",
        mentionSelf: Boolean = false,
    ): String {
        val segments = buildString {
            append("[{\"type\":\"text\",\"data\":{\"text\":\"$text\"}}")
            if (mentionSelf) append(", {\"type\":\"at\",\"data\":{\"qq\":\"1000\"}}")
            append(']')
        }
        return """{"post_type":"message","message_type":"group","self_id":"1000","user_id":"$senderId","group_id":"$groupId","message_id":"$messageId","message":$segments}"""
    }

    private fun parseEvent(payload: String): OneBotInboundEvent =
        (OneBotMessageParser().parse(payload) as OneBotPayloadParseResult.Message).event

    private data class SentReply(val target: MessageRoute, val text: String)

    private data class Harness(
        val directory: java.io.File,
        val store: QqPlatformSettingsStore,
        val adapter: QqOneBotInboundAdapter,
        val replies: MutableList<SentReply>,
    ) : AutoCloseable {
        fun settings(): QqPlatformSettings = store.settings.value.settings

        fun commandSettings(): QqPlatformCommandSettings = store.commandSettings.value.settings

        suspend fun update(next: QqPlatformSettings) {
            val result = store.updateSettings(store.settings.value.revision, next)
            check(result is QqPlatformSettingsUpdateResult.Saved)
        }

        suspend fun updateCommandSettings(next: QqPlatformCommandSettings) {
            val result = store.updateCommandSettings(store.commandSettings.value.revision, next)
            check(result is QqPlatformCommandSettingsUpdateResult.Saved)
        }

        override fun close() {
            directory.deleteRecursively()
        }
    }
}
