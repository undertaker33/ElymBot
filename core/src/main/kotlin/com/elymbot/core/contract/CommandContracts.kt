package com.elymbot.core.contract

data class CommandNames(
    val help: String = "help",
    val persona: String = "persona",
    val checkpoint: String = "checkpoint",
    val reset: String = "reset",
    val mark: String = "mark",
)

sealed interface CommandAction {
    data object Help : CommandAction

    data class Persona(val index: Int?) : CommandAction

    data class Checkpoint(val number: Int?) : CommandAction

    data object Reset : CommandAction

    data object Mark : CommandAction
}

sealed interface CommandParseResult {
    data object NotCommand : CommandParseResult

    data class Action(val action: CommandAction) : CommandParseResult

    data class Invalid(
        val action: CommandAction,
        val input: String,
    ) : CommandParseResult
}

/** Parses renamed slash commands into app-owned actions; it does not execute them. */
fun interface SlashCommandParser {
    fun parse(input: String, names: CommandNames): CommandParseResult
}
