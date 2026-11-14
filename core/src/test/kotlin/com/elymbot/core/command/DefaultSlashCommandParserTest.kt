package com.elymbot.core.command

import com.elymbot.core.contract.CommandAction
import com.elymbot.core.contract.CommandNames
import com.elymbot.core.contract.CommandParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSlashCommandParserTest {
    private val parser = DefaultSlashCommandParser()

    @Test
    fun parsesFiveBehaviorsAndKeepsArgumentsSeparatedByWhitespace() {
        val names = CommandNames()

        assertEquals(CommandParseResult.Action(CommandAction.Help), parser.parse("/help", names))
        assertEquals(CommandParseResult.Action(CommandAction.Persona(null)), parser.parse("/persona", names))
        assertEquals(CommandParseResult.Action(CommandAction.Persona(3)), parser.parse("/persona 3", names))
        assertEquals(CommandParseResult.Action(CommandAction.Checkpoint(4)), parser.parse("/checkpoint   4  ", names))
        assertEquals(CommandParseResult.Action(CommandAction.Reset), parser.parse("/reset", names))
        assertEquals(CommandParseResult.Action(CommandAction.Mark), parser.parse("/mark", names))
        assertEquals(CommandParseResult.NotCommand, parser.parse("ordinary message", names))
    }

    @Test
    fun parsesRenamedCommandsAndReportsMalformedArgumentsLocally() {
        val renamed = CommandNames(help = "commands", persona = "switch", checkpoint = "back", reset = "clear", mark = "save")

        assertEquals(CommandParseResult.Action(CommandAction.Help), parser.parse("/commands", renamed))
        assertEquals(CommandParseResult.Action(CommandAction.Persona(2)), parser.parse("/switch 2", renamed))
        assertEquals(CommandParseResult.Action(CommandAction.Reset), parser.parse("/clear", renamed))
        assertEquals(CommandParseResult.Action(CommandAction.Mark), parser.parse("/save", renamed))
        assertTrue(parser.parse("/switch zero", renamed) is CommandParseResult.Invalid)
        assertTrue(parser.parse("/back 0", renamed) is CommandParseResult.Invalid)
        assertTrue(parser.parse("/commands extra", renamed) is CommandParseResult.Invalid)
        assertTrue(parser.parse("/clear anything", renamed) is CommandParseResult.Invalid)
        assertEquals(CommandParseResult.NotCommand, parser.parse("/not-configured", renamed))
    }
}
