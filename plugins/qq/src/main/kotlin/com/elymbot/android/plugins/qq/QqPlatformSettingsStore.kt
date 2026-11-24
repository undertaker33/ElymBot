package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.QqGroupMember
import com.elymbot.core.contract.QqPlatformRateLimitSettings
import com.elymbot.core.contract.QqPlatformRateLimitStrategy
import com.elymbot.core.contract.QqPlatformSettings
import com.elymbot.core.contract.QqPlatformSettingsSnapshot
import com.elymbot.core.contract.QqPlatformSettingsUpdateResult
import com.elymbot.core.contract.QqPlatformSettingsRejection
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Persists the QQ-specific policy only inside this plugin installation's private data directory. */
internal class QqPlatformSettingsStore(dataDirectory: File) {
    private val settingsFile = File(dataDirectory.canonicalFile, SETTINGS_FILE_NAME).canonicalFile
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(loadSnapshot())

    val settings: StateFlow<QqPlatformSettingsSnapshot> = mutableSettings.asStateFlow()

    init {
        require(settingsFile.parentFile == dataDirectory.canonicalFile)
    }

    suspend fun updateSettings(
        expectedRevision: Long,
        settings: QqPlatformSettings,
    ): QqPlatformSettingsUpdateResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = mutableSettings.value
            if (expectedRevision != current.revision) {
                return@withLock QqPlatformSettingsUpdateResult.Conflict(current)
            }
            if (!isValid(settings)) {
                return@withLock QqPlatformSettingsUpdateResult.Rejected(QqPlatformSettingsRejection.INVALID_SETTINGS)
            }

            val next = QqPlatformSettingsSnapshot(
                revision = current.revision + 1,
                settings = settings.frozenCopy(),
            )
            try {
                writeSnapshot(next)
            } catch (_: Exception) {
                return@withLock QqPlatformSettingsUpdateResult.Rejected(QqPlatformSettingsRejection.STORE_UNAVAILABLE)
            }
            mutableSettings.value = next
            QqPlatformSettingsUpdateResult.Saved(next)
        }
    }

    private fun loadSnapshot(): QqPlatformSettingsSnapshot {
        if (!settingsFile.isFile || Files.isSymbolicLink(settingsFile.toPath())) {
            return QqPlatformSettingsSnapshot(0, DEFAULT_SETTINGS.frozenCopy())
        }
        return runCatching {
            require(settingsFile.length() <= MAX_SETTINGS_BYTES)
            val root = JSON.parseToJsonElement(settingsFile.readText(Charsets.UTF_8)).jsonObject
            require(root.long("schemaVersion") == SCHEMA_VERSION.toLong())
            val revision = root.long("revision")
            require(revision >= 0)
            val settings = root.obj("settings").toSettings().frozenCopy()
            require(isValid(settings))
            QqPlatformSettingsSnapshot(revision, settings)
        }.getOrElse {
            QqPlatformSettingsSnapshot(0, DEFAULT_SETTINGS.frozenCopy())
        }
    }

    private fun writeSnapshot(snapshot: QqPlatformSettingsSnapshot) {
        require(!Files.isSymbolicLink(settingsFile.toPath()))
        val parent = settingsFile.parentFile ?: throw IllegalStateException("QQ settings directory is unavailable")
        if (!parent.isDirectory) throw IllegalStateException("QQ settings directory is unavailable")
        val temporary = File(parent, ".$SETTINGS_FILE_NAME-${UUID.randomUUID()}$TEMP_FILE_SUFFIX")
        try {
            val bytes = snapshot.toJson().toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_SETTINGS_BYTES) throw IllegalStateException("QQ settings exceed the size limit")
            temporary.outputStream().buffered().use { output ->
                output.write(bytes)
                output.flush()
            }
            Files.move(
                temporary.toPath(),
                settingsFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun QqPlatformSettingsSnapshot.toJson() = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put("revision", revision)
        put("settings", settings.toJson())
    }

    private fun QqPlatformSettings.toJson() = buildJsonObject {
        put("ignoreSelfMessageEnabled", ignoreSelfMessageEnabled)
        put("ignoreAtAllEventEnabled", ignoreAtAllEventEnabled)
        put("whitelistEnabled", whitelistEnabled)
        putStringSet("allowedUserIds", allowedUserIds)
        putStringSet("allowedPrivateUserIds", allowedPrivateUserIds)
        putStringSet("allowedGroupIds", allowedGroupIds)
        put("allowedGroupMembers", buildJsonArray {
            allowedGroupMembers.sortedWith(compareBy(QqGroupMember::groupId, QqGroupMember::userId)).forEach { member ->
                add(buildJsonObject {
                    put("userId", member.userId)
                    put("groupId", member.groupId)
                })
            }
        })
        putStringSet("adminUserIds", adminUserIds)
        put("adminGroupBypassWhitelistEnabled", adminGroupBypassWhitelistEnabled)
        put("adminPrivateBypassWhitelistEnabled", adminPrivateBypassWhitelistEnabled)
        put("privateChatRequiresWakeWord", privateChatRequiresWakeWord)
        put("replyOnAtOnlyEnabled", replyOnAtOnlyEnabled)
        put("wakeWordsAdminOnlyEnabled", wakeWordsAdminOnlyEnabled)
        putStringList("wakeWords", wakeWords)
        putStringList("triggerWords", triggerWords)
        put("keywordDetectionEnabled", keywordDetectionEnabled)
        putStringList("keywordPatterns", keywordPatterns)
        putStringSet("publicGroupHistoryGroupIds", publicGroupHistoryGroupIds)
        put("commandAliases", buildJsonObject {
            commandAliases.toSortedMap().forEach { (alias, command) -> put(alias, command) }
        })
        put("privateRateLimit", privateRateLimit?.toJson() ?: JsonNull)
        put("groupRateLimit", groupRateLimit?.toJson() ?: JsonNull)
    }

    private fun QqPlatformRateLimitSettings.toJson() = buildJsonObject {
        put("windowMillis", windowMillis)
        put("maxMessages", maxMessages)
        put("strategy", strategy.name)
        put("maxStashedMessages", maxStashedMessages)
        put("maxStashAgeMillis", maxStashAgeMillis)
    }

    private fun JsonObject.toSettings() = QqPlatformSettings(
        ignoreSelfMessageEnabled = boolean("ignoreSelfMessageEnabled", DEFAULT_SETTINGS.ignoreSelfMessageEnabled),
        ignoreAtAllEventEnabled = boolean("ignoreAtAllEventEnabled", DEFAULT_SETTINGS.ignoreAtAllEventEnabled),
        whitelistEnabled = boolean("whitelistEnabled", DEFAULT_SETTINGS.whitelistEnabled),
        allowedUserIds = stringSet("allowedUserIds"),
        allowedPrivateUserIds = stringSet("allowedPrivateUserIds"),
        allowedGroupIds = stringSet("allowedGroupIds"),
        allowedGroupMembers = array("allowedGroupMembers").mapNotNull { element ->
            val member = element as? JsonObject ?: return@mapNotNull null
            QqGroupMember(
                userId = member.string("userId"),
                groupId = member.string("groupId"),
            )
        }.toSet(),
        adminUserIds = stringSet("adminUserIds"),
        adminGroupBypassWhitelistEnabled = boolean(
            "adminGroupBypassWhitelistEnabled",
            DEFAULT_SETTINGS.adminGroupBypassWhitelistEnabled,
        ),
        adminPrivateBypassWhitelistEnabled = boolean(
            "adminPrivateBypassWhitelistEnabled",
            DEFAULT_SETTINGS.adminPrivateBypassWhitelistEnabled,
        ),
        privateChatRequiresWakeWord = boolean(
            "privateChatRequiresWakeWord",
            DEFAULT_SETTINGS.privateChatRequiresWakeWord,
        ),
        replyOnAtOnlyEnabled = boolean("replyOnAtOnlyEnabled", DEFAULT_SETTINGS.replyOnAtOnlyEnabled),
        wakeWordsAdminOnlyEnabled = boolean("wakeWordsAdminOnlyEnabled", DEFAULT_SETTINGS.wakeWordsAdminOnlyEnabled),
        wakeWords = stringList("wakeWords"),
        triggerWords = stringList("triggerWords"),
        keywordDetectionEnabled = boolean("keywordDetectionEnabled", DEFAULT_SETTINGS.keywordDetectionEnabled),
        keywordPatterns = stringList("keywordPatterns"),
        publicGroupHistoryGroupIds = stringSet("publicGroupHistoryGroupIds"),
        commandAliases = objOrEmpty("commandAliases").mapNotNull { (key, value) ->
            value.stringOrNull()?.let { key to it }
        }.toMap(),
        privateRateLimit = rateLimit("privateRateLimit"),
        groupRateLimit = rateLimit("groupRateLimit"),
    )

    private fun JsonObject.rateLimit(key: String): QqPlatformRateLimitSettings? {
        val element = this[key] ?: return null
        if (element == JsonNull) return null
        val value = element as? JsonObject ?: throw IllegalArgumentException("Invalid QQ rate limit")
        return QqPlatformRateLimitSettings(
            windowMillis = value.long("windowMillis"),
            maxMessages = value.long("maxMessages").toInt(),
            strategy = QqPlatformRateLimitStrategy.valueOf(value.string("strategy")),
            maxStashedMessages = value.long("maxStashedMessages").toInt(),
            maxStashAgeMillis = value.long("maxStashAgeMillis"),
        )
    }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("Invalid QQ settings object")

    private fun JsonObject.objOrEmpty(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String =
        this[key]?.stringOrNull() ?: throw IllegalArgumentException("Invalid QQ settings string")

    private fun JsonObject.long(key: String): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Invalid QQ settings number")

    private fun JsonObject.boolean(key: String, default: Boolean): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObject.stringSet(key: String): Set<String> = array(key).mapNotNull { it.stringOrNull() }.toSet()

    private fun JsonObject.stringList(key: String): List<String> = array(key).mapNotNull { it.stringOrNull() }

    private fun JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun isValid(settings: QqPlatformSettings): Boolean {
        if (settings.allowedUserIds.size > MAX_ALLOW_ENTRIES ||
            settings.allowedPrivateUserIds.size > MAX_ALLOW_ENTRIES ||
            settings.allowedGroupIds.size > MAX_ALLOW_ENTRIES ||
            settings.allowedGroupMembers.size > MAX_ALLOW_ENTRIES ||
            settings.adminUserIds.size > MAX_ALLOW_ENTRIES ||
            settings.publicGroupHistoryGroupIds.size > MAX_ALLOW_ENTRIES
        ) return false
        if ((settings.allowedUserIds + settings.allowedPrivateUserIds + settings.allowedGroupIds +
                settings.adminUserIds + settings.publicGroupHistoryGroupIds)
                .any { !QQ_ID_PATTERN.matches(it) }
        ) return false
        if (settings.allowedGroupMembers.any { !QQ_ID_PATTERN.matches(it.userId) || !QQ_ID_PATTERN.matches(it.groupId) }) {
            return false
        }
        if (settings.wakeWords.size > MAX_WORDS || settings.triggerWords.size > MAX_WORDS ||
            settings.keywordPatterns.size > MAX_WORDS || settings.commandAliases.size > MAX_WORDS
        ) return false
        if ((settings.wakeWords + settings.triggerWords).any { it.isBlank() || it.length > MAX_TEXT_LENGTH }) return false
        if (settings.keywordPatterns.any { pattern ->
                pattern.isBlank() || pattern.length > MAX_TEXT_LENGTH || runCatching { Regex(pattern) }.isFailure
            }
        ) return false
        if (settings.commandAliases.any { (alias, command) ->
                alias.isBlank() || alias.length > MAX_ALIAS_LENGTH || alias.any(Char::isWhitespace) ||
                    command.isBlank() || command.length > MAX_ALIAS_LENGTH || command.any(Char::isWhitespace)
            }
        ) return false
        return isValid(settings.privateRateLimit) && isValid(settings.groupRateLimit)
    }

    private fun isValid(rule: QqPlatformRateLimitSettings?): Boolean = rule == null ||
        (rule.windowMillis in 1..MAX_RATE_WINDOW_MILLIS &&
            rule.maxMessages in 1..MAX_RATE_MESSAGES &&
            rule.maxStashedMessages in 0..MAX_STASHED_MESSAGES &&
            rule.maxStashAgeMillis in 1..MAX_STASH_AGE_MILLIS &&
            (rule.strategy != QqPlatformRateLimitStrategy.STASH || rule.maxStashedMessages > 0))

    private fun QqPlatformSettings.frozenCopy() = copy(
        allowedUserIds = Collections.unmodifiableSet(LinkedHashSet(allowedUserIds)),
        allowedPrivateUserIds = Collections.unmodifiableSet(LinkedHashSet(allowedPrivateUserIds)),
        allowedGroupIds = Collections.unmodifiableSet(LinkedHashSet(allowedGroupIds)),
        allowedGroupMembers = Collections.unmodifiableSet(LinkedHashSet(allowedGroupMembers)),
        adminUserIds = Collections.unmodifiableSet(LinkedHashSet(adminUserIds)),
        wakeWords = Collections.unmodifiableList(wakeWords.toList()),
        triggerWords = Collections.unmodifiableList(triggerWords.toList()),
        keywordPatterns = Collections.unmodifiableList(keywordPatterns.toList()),
        publicGroupHistoryGroupIds = Collections.unmodifiableSet(LinkedHashSet(publicGroupHistoryGroupIds)),
        commandAliases = Collections.unmodifiableMap(LinkedHashMap(commandAliases)),
    )

    private companion object {
        const val SETTINGS_FILE_NAME = "qq-settings.json"
        const val TEMP_FILE_SUFFIX = ".tmp"
        const val SCHEMA_VERSION = 1
        const val MAX_SETTINGS_BYTES = 512 * 1024L
        const val MAX_ALLOW_ENTRIES = 10_000
        const val MAX_WORDS = 1_000
        const val MAX_TEXT_LENGTH = 256
        const val MAX_ALIAS_LENGTH = 64
        const val MAX_RATE_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1_000
        const val MAX_RATE_MESSAGES = 100_000
        const val MAX_STASHED_MESSAGES = 1_024
        const val MAX_STASH_AGE_MILLIS = 7L * 24 * 60 * 60 * 1_000
        val QQ_ID_PATTERN = Regex("[0-9]{1,20}")
        val DEFAULT_SETTINGS = QqPlatformSettings()
        val JSON = Json
    }
}

private fun JsonObjectBuilder.putStringSet(key: String, values: Set<String>) {
    put(key, buildJsonArray { values.sorted().forEach { add(JsonPrimitive(it)) } })
}

private fun JsonObjectBuilder.putStringList(key: String, values: List<String>) {
    put(key, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}
