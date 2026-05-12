package com.astrbot.android.core.runtime.context

object SystemPromptBuilder {

    fun build(ctx: ResolvedRuntimeContext): String? = PromptAssembler.assemble(ctx)

    fun buildForAppChat(
        personaPrompt: String?,
        realWorldTimeAwarenessEnabled: Boolean,
        skills: List<RuntimeLegacySkillSnapshot> = emptyList(),
    ): String? = PromptAssembler.assembleForAppChat(personaPrompt, realWorldTimeAwarenessEnabled, skills)
}
