package com.elymbot.android.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal val SlashCommandTrigger = ComposerTrigger('/', leadingOnly = true)

internal class ComposerTrigger(private val marker: Char, private val leadingOnly: Boolean) {
    fun match(value: TextFieldValue): ComposerTriggerMatch? {
        val caret = value.selection.end
        if (!value.selection.collapsed || caret == 0) return null
        val start = value.text.take(caret).indexOfLast(Char::isWhitespace) + 1
        if (start == caret || value.text[start] != marker) return null
        if (leadingOnly && value.text.take(start).any { !it.isWhitespace() }) return null
        val end = start + value.text.drop(start).takeWhile { !it.isWhitespace() }.length
        return ComposerTriggerMatch(start, end, value.text.substring(start + 1, caret))
    }

    fun prompt(text: String, names: Collection<String>): TextFieldValue {
        val start = text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val token = text.drop(start).takeWhile { !it.isWhitespace() }
        val replace = token.startsWith(marker) && names.any { it.startsWith(token.drop(1), ignoreCase = true) }
        val prefix = text.take(start) + marker
        val suffix = when {
            replace -> text.drop(start + token.length)
            token.isEmpty() -> text.drop(start)
            else -> " " + text.drop(start)
        }
        return TextFieldValue(prefix + suffix, TextRange(prefix.length))
    }
}

internal data class ComposerTriggerMatch(val start: Int, val end: Int, val query: String) {
    fun replace(text: String, replacement: String, appendSpace: Boolean): TextFieldValue {
        val prefix = text.take(start) + replacement
        val suffix = text.drop(end).ifEmpty { if (appendSpace) " " else "" }
        val cursor = prefix.length + if (suffix.isEmpty()) 0 else 1
        return TextFieldValue(prefix + suffix, TextRange(cursor))
    }
}
