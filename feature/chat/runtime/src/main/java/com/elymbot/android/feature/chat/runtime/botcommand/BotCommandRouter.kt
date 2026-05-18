package com.elymbot.android.feature.chat.runtime.botcommand

object BotCommandRouter {
    private val supportedCommands = setOf(
        "help",
        "sid",
        "agent",
        "stop",
        "start",
        "switch",
        "new",
        "del",
        "rename",
        "provider",
        "model",
        "llm",
        "op",
        "deop",
        "wl",
        "dwl",
        "ls",
        "reset",
        "stt",
        "tts",
        "persona",
    )

    fun supports(commandName: String): Boolean {
        return commandName.lowercase() in supportedCommands
    }

    fun handle(
        input: String,
        context: BotCommandContext,
    ): BotCommandResult {
        val command = BotCommandParser.parse(input) ?: return BotCommandResult.unhandled()
        return when (command.name) {
            "help" -> handled(BotCommandResources.help(context.languageTag, context.strings))
            "sid" -> handled(BotCommandResources.sid(context))
            "agent" -> handled(
                BotCommandResources.agentSummary(
                    bots = context.availableBots,
                    currentBot = context.bot,
                    languageTag = context.languageTag,
                    strings = context.strings,
                ),
            )
            "stop" -> handleStop(command, context)
            "start" -> handleStart(command, context)
            "switch" -> handleSwitch(command, context)
            "new" -> handleNew(context)
            "del" -> handleDelete(context)
            "rename" -> handleRename(command, context)
            "provider" -> handleProvider(command, context)
            "model" -> handleModel(command, context)
            "llm" -> handleLlm(context)
            "op" -> handledConfigArgument(command.rawArguments, context) { uid, config ->
                context.updateConfig(config.copy(adminUids = (config.adminUids + uid).distinct()))
                BotCommandResources.adminGranted(uid, context.languageTag, context.strings)
            }
            "deop" -> handledConfigArgument(command.rawArguments, context) { uid, config ->
                context.updateConfig(config.copy(adminUids = config.adminUids.filterNot { it == uid }))
                BotCommandResources.adminRevoked(uid, context.languageTag, context.strings)
            }
            "wl" -> handledConfigArgument(command.rawArguments, context) { umo, config ->
                context.updateConfig(config.copy(whitelistEntries = (config.whitelistEntries + umo).distinct()))
                BotCommandResources.whitelistAdded(umo, context.languageTag, context.strings)
            }
            "dwl" -> handledConfigArgument(command.rawArguments, context) { umo, config ->
                context.updateConfig(config.copy(whitelistEntries = config.whitelistEntries.filterNot { it == umo }))
                BotCommandResources.whitelistRemoved(umo, context.languageTag, context.strings)
            }
            "ls" -> handled(
                BotCommandSessionListing.render(
                    sessions = context.sessions,
                    personas = context.availablePersonas,
                    currentSessionId = context.sessionId,
                    page = command.arguments.firstOrNull()?.toIntOrNull() ?: 1,
                    languageTag = context.languageTag,
                ),
            )

            "reset" -> {
                context.replaceMessages(emptyList())
                handled(BotCommandResources.reset(context.languageTag, context.strings))
            }

            "stt" -> {
                val next = !context.session.sessionSttEnabled
                context.updateSessionServiceFlags(next, null)
                handled(BotCommandResources.sttToggled(next, context.languageTag, context.strings))
            }

            "tts" -> {
                val next = !context.session.sessionTtsEnabled
                context.updateSessionServiceFlags(null, next)
                handled(BotCommandResources.ttsToggled(next, context.languageTag, context.strings))
            }

            "persona" -> handlePersona(command, context)
            else -> handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        }
    }

    private fun handlePersona(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        if (command.arguments.isEmpty()) {
            return handled(BotCommandResources.personaHelp(context.languageTag, context.strings))
        }

        if (command.arguments.first().equals("list", ignoreCase = true)) {
            return handled(
                BotCommandResources.personaList(
                    personas = context.availablePersonas.filter { it.enabled },
                    currentPersonaId = context.currentPersona?.id,
                    languageTag = context.languageTag,
                    strings = context.strings,
                ),
            )
        }

        if (command.arguments.first().equals("view", ignoreCase = true)) {
            val targetName = command.rawArguments.substringAfter("view", "").trim()
            val persona = findPersonaByName(targetName, context)
                ?: return handled(BotCommandResources.personaNotFound(context.languageTag, context.strings))
            return handled(BotCommandResources.personaView(persona, context.languageTag, context.strings))
        }

        val targetName = command.rawArguments
        val targetPersona = findPersonaByName(targetName, context)
            ?: return handled(BotCommandResources.personaNotFound(context.languageTag, context.strings))
        context.updateSessionBindings(
            context.activeProviderId.ifBlank { context.session.providerId },
            targetPersona.id,
            context.bot.id,
        )
        return handled(BotCommandResources.personaSwitched(targetPersona.name, context.languageTag, context.strings))
    }

    private fun findPersonaByName(
        targetName: String,
        context: BotCommandContext,
    ) = context.availablePersonas.firstOrNull {
        it.enabled && it.name.equals(targetName.trim(), ignoreCase = true)
    }

    private fun handleSwitch(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        val index = command.arguments.firstOrNull()?.toIntOrNull()
            ?: return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        val selectSession = context.selectSession
            ?: return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        val targetSession = context.sessions.getOrNull(index - 1)
            ?: return handled(BotCommandResources.sessionIndexNotFound(index, context.languageTag, context.strings))
        selectSession(targetSession.id)
        return handled(BotCommandResources.sessionSwitched(targetSession.id, context.languageTag, context.strings))
    }

    private fun handleStop(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        val target = if (command.rawArguments.isBlank()) {
            context.bot
        } else {
            findBotByName(command.rawArguments, context)
                ?: return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        }
        context.updateBot(target.copy(autoReplyEnabled = false))
        return handled(BotCommandResources.agentStopped(target.displayName, context.languageTag, context.strings))
    }

    private fun handleStart(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        val target = findBotByName(command.rawArguments, context)
            ?: return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        context.updateBot(target.copy(autoReplyEnabled = true))
        return handled(BotCommandResources.agentStarted(target.displayName, context.languageTag, context.strings))
    }

    private fun handleNew(context: BotCommandContext): BotCommandResult {
        val createSession = context.createSession
            ?: return handled(BotCommandResources.unsupportedCommand("new", context.languageTag, context.strings))
        val created = createSession()
        return handled(BotCommandResources.sessionCreated(created.id, context.languageTag, context.strings))
    }

    private fun handleDelete(context: BotCommandContext): BotCommandResult {
        val deleteSession = context.deleteSession
            ?: return handled(BotCommandResources.unsupportedCommand("del", context.languageTag, context.strings))
        deleteSession(context.sessionId)
        return handled(BotCommandResources.sessionDeleted(context.languageTag, context.strings))
    }

    private fun handleRename(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        val title = command.rawArguments.trim()
        if (title.isBlank()) {
            return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        }
        val renameSession = context.renameSession
            ?: return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        renameSession(context.sessionId, title)
        return handled(BotCommandResources.sessionRenamed(title, context.languageTag, context.strings))
    }

    private fun handleProvider(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        val currentProvider = currentProvider(context)
        if (command.rawArguments.isBlank()) {
            return handled(
                BotCommandResources.providerSummary(
                    currentProvider = currentProvider,
                    providers = context.availableProviders,
                    languageTag = context.languageTag,
                    strings = context.strings,
                ),
            )
        }
        val argument = command.rawArguments.trim()
        val target = context.availableProviders.firstOrNull { provider ->
            provider.enabled &&
                (provider.id.equals(argument, ignoreCase = true) ||
                    provider.name.equals(argument, ignoreCase = true))
        } ?: return handled(BotCommandResources.providerNotFound(argument, context.languageTag, context.strings))

        context.updateConfig(context.config.copy(defaultChatProviderId = target.id))
        context.updateBot(context.bot.copy(defaultProviderId = target.id))
        context.updateSessionBindings(
            target.id,
            context.currentPersona?.id ?: context.session.personaId,
            context.bot.id,
        )
        return handled(BotCommandResources.providerSwitched(target.name, context.languageTag, context.strings))
    }

    private fun handleModel(
        command: ParsedBotCommand,
        context: BotCommandContext,
    ): BotCommandResult {
        val currentProvider = currentProvider(context)
            ?: return handled(BotCommandResources.noActiveProvider(context.languageTag, context.strings))
        val model = command.rawArguments.trim()
        if (model.isBlank()) {
            return handled(BotCommandResources.unsupportedCommand(command.name, context.languageTag, context.strings))
        }
        context.updateProvider(currentProvider.copy(model = model))
        return handled(BotCommandResources.modelUpdated(currentProvider.name, model, context.languageTag, context.strings))
    }

    private fun handleLlm(context: BotCommandContext): BotCommandResult {
        val next = !context.bot.autoReplyEnabled
        context.updateBot(context.bot.copy(autoReplyEnabled = next))
        return handled(BotCommandResources.llmToggled(next, context.languageTag, context.strings))
    }

    private fun currentProvider(context: BotCommandContext) = context.availableProviders.firstOrNull { provider ->
        provider.id == context.activeProviderId ||
            (context.activeProviderId.isBlank() && provider.id == context.session.providerId)
    }

    private fun findBotByName(
        targetName: String,
        context: BotCommandContext,
    ) = context.availableBots.firstOrNull {
        it.displayName.equals(targetName.trim(), ignoreCase = true)
    }

    private fun handled(replyText: String): BotCommandResult {
        return BotCommandResult(
            handled = true,
            replyText = replyText,
            stopModelDispatch = true,
        )
    }

    private fun handledArgument(
        rawArguments: String,
        replyForArgument: (String) -> String,
    ): BotCommandResult {
        val argument = rawArguments.trim()
        return if (argument.isBlank()) {
            BotCommandResult.unhandled()
        } else {
            handled(replyForArgument(argument))
        }
    }

    private fun handledConfigArgument(
        rawArguments: String,
        context: BotCommandContext,
        replyForArgument: (String, com.elymbot.android.feature.config.domain.model.ConfigProfile) -> String,
    ): BotCommandResult {
        val argument = rawArguments.trim()
        return if (argument.isBlank()) {
            BotCommandResult.unhandled()
        } else {
            handled(replyForArgument(argument, context.config))
        }
    }
}


