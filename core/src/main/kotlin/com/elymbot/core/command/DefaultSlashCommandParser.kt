package com.elymbot.core.command

import com.elymbot.core.contract.CommandAction
import com.elymbot.core.contract.CommandNames
import com.elymbot.core.contract.CommandParseResult
import com.elymbot.core.contract.SlashCommandParser

class DefaultSlashCommandParser : SlashCommandParser {
    override fun parse(input: String, names: CommandNames): CommandParseResult {
        if (!input.startsWith('/')) return CommandParseResult.NotCommand
        val commandBody = input.drop(1)
        val separator = commandBody.indexOfFirst(Char::isWhitespace)
        val name = if (separator < 0) commandBody else commandBody.substring(0, separator)
        val arguments = if (separator < 0) "" else commandBody.substring(separator).trim()
        val action = when (name) {
            names.help -> CommandAction.Help
            names.persona -> CommandAction.Persona(parseOptionalPositiveInt(arguments))
            names.checkpoint -> CommandAction.Checkpoint(parseOptionalPositiveInt(arguments))
            names.reset -> CommandAction.Reset
            names.mark -> CommandAction.Mark
            else -> return CommandParseResult.NotCommand
        }
        val hasArgument = arguments.isNotEmpty()
        val valid = when (action) {
            CommandAction.Help, CommandAction.Reset, CommandAction.Mark -> !hasArgument
            is CommandAction.Persona -> !hasArgument || action.index != null
            is CommandAction.Checkpoint -> !hasArgument || action.number != null
        }
        return if (valid) {
            CommandParseResult.Action(action)
        } else {
            CommandParseResult.Invalid(action, arguments)
        }
    }

    private fun parseOptionalPositiveInt(value: String): Int? {
        if (value.isEmpty()) return null
        val number = value.toIntOrNull() ?: return null
        return number.takeIf { it > 0 }
    }
}
