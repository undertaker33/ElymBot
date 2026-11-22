package com.elymbot.android.session

import android.content.Context
import com.elymbot.android.R
import com.elymbot.core.contract.CommandNames

interface CommandTextProvider {
    fun help(mainSession: Boolean, names: CommandNames): String
    fun currentMarker(): String
    fun personaEntry(number: Int, name: String, current: Boolean): String
    fun personaSwitched(name: String): String
    fun checkpointEntry(number: Int, current: Boolean): String
    fun checkpointMarked(number: Int): String
    fun checkpointRestored(number: Int): String
    fun noCheckpoints(): String
    fun noMarkBoundary(): String
    fun invalidCommand(helpName: String): String
}

class AndroidCommandTextProvider(context: Context) : CommandTextProvider {
    private val resources = context.applicationContext.resources

    override fun help(mainSession: Boolean, names: CommandNames): String = resources.getString(
        if (mainSession) R.string.elym_command_help_main else R.string.elym_command_help_session,
        *if (mainSession) {
            arrayOf(names.help, names.persona, names.checkpoint, names.reset, names.mark)
        } else {
            arrayOf(names.help, names.checkpoint, names.reset, names.mark)
        },
    )

    override fun currentMarker(): String = resources.getString(R.string.elym_command_current_marker)

    override fun personaEntry(number: Int, name: String, current: Boolean): String = resources.getString(
        R.string.elym_command_persona_entry,
        number,
        name,
        if (current) currentMarker() else "",
    )

    override fun personaSwitched(name: String): String = resources.getString(R.string.elym_command_persona_switched, name)

    override fun checkpointEntry(number: Int, current: Boolean): String = resources.getString(
        R.string.elym_command_checkpoint_entry,
        number,
        if (current) currentMarker() else "",
    )

    override fun checkpointMarked(number: Int): String = resources.getString(R.string.elym_command_checkpoint_marked, number)

    override fun checkpointRestored(number: Int): String = resources.getString(R.string.elym_command_checkpoint_restored, number)

    override fun noCheckpoints(): String = resources.getString(R.string.elym_command_checkpoint_empty)

    override fun noMarkBoundary(): String = resources.getString(R.string.elym_command_no_mark_boundary)

    override fun invalidCommand(helpName: String): String = resources.getString(R.string.elym_command_invalid, helpName)
}
