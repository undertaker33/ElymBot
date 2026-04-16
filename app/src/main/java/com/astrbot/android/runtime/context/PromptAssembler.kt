package com.astrbot.android.runtime.context

import com.astrbot.android.model.SkillEntry
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Assembles the final system prompt from a [ResolvedRuntimeContext].
 *
 * This is the **single authoritative source** for all system prompt content.
 * Neither ChatViewModel nor OneBotBridgeServer should build prompts directly.
 *
 * Assembly ordering (stable contract):
 *   1. Persona system prompt — role identity
 *   2. Prompt Skill blocks — ordered by priority desc, then sortIndex
 *   3. Platform channel hint — QQ group / QQ private / none for App
 *   4. Runtime facts — real-world time awareness
 *
 * Prompt Skills with blank content are silently skipped.
 * Skills are subject to a total length budget; excess skills are trimmed
 * with a log note (future: observable metric).
 */
object PromptAssembler {

    private const val DEFAULT_SKILL_BUDGET_CHARS = 8000

    /**
     * Build the final system prompt for a full pipeline request.
     */
    fun assemble(ctx: ResolvedRuntimeContext): String? {
        val parts = mutableListOf<String>()

        // 1. Persona system prompt
        ctx.persona?.systemPrompt?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)

        // 2. Prompt Skill blocks
        assembleSkillBlocks(ctx.skills)?.let(parts::add)

        // 3. Platform channel hint
        channelHint(ctx)?.let(parts::add)

        // 4. Runtime facts
        if (ctx.realWorldTimeAwarenessEnabled) {
            val now = ZonedDateTime.now()
            parts += "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        }

        return parts.joinToString("\n\n").ifBlank { null }
    }

    /**
     * Convenience for App chat direct provider path (Track B) where a full
     * [ResolvedRuntimeContext] is not available. Will be removed once Track B
     * also goes through the orchestrator.
     */
    fun assembleForAppChat(
        personaPrompt: String?,
        realWorldTimeAwarenessEnabled: Boolean,
        skills: List<SkillEntry> = emptyList(),
    ): String? {
        val parts = mutableListOf<String>()
        personaPrompt?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        assembleSkillBlocks(skills)?.let(parts::add)
        if (realWorldTimeAwarenessEnabled) {
            val now = ZonedDateTime.now()
            parts += "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }

    /**
     * Assemble active Prompt Skill blocks, ordered by descending priority
     * then original list order. Skills with blank content are skipped.
     * The combined length is capped at [DEFAULT_SKILL_BUDGET_CHARS].
     */
    internal fun assembleSkillBlocks(skills: List<SkillEntry>): String? {
        val activeSkills = skills
            .filter { it.active && it.content.isNotBlank() }
            .sortedByDescending { it.priority }
        if (activeSkills.isEmpty()) return null

        val builder = StringBuilder()
        var budget = DEFAULT_SKILL_BUDGET_CHARS
        for (skill in activeSkills) {
            val block = skill.content.trim()
            if (block.length > budget) break
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(block)
            budget -= block.length
        }
        return builder.toString().ifBlank { null }
    }

    private fun channelHint(ctx: ResolvedRuntimeContext): String? {
        if (ctx.ingressEvent.platform != RuntimePlatform.QQ_ONEBOT) return null
        return when (ctx.ingressEvent.messageType) {
            com.astrbot.android.model.chat.MessageType.GroupMessage ->
                "You are replying inside a QQ group chat. Keep the answer concise and natural, and focus on the latest message."
            else ->
                "You are replying inside a QQ private chat. Keep the answer concise and natural."
        }
    }
}
