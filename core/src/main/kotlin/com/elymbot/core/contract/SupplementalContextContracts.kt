package com.elymbot.core.contract

/** Bounded, App-authorized context kept outside the durable per-conversation history. */
data class AgentSupplementalContext(
    val sourceLabel: String,
    val messages: List<AgentSupplementalContextMessage>,
)

data class AgentSupplementalContextMessage(
    val authorId: String,
    val text: String,
    val createdAtEpochMillis: Long,
)
