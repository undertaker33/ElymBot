package com.elymbot.android.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R

private const val MaxDraftLength = 1800

private enum class ChatLayer { Composer, Timeline }

@Composable
internal fun FloatingComposerLayout(
    modifier: Modifier,
    timeline: @Composable (bottomPadding: Dp) -> Unit,
    composer: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val footer = subcompose(ChatLayer.Composer, composer).single().measure(constraints.copy(minHeight = 0))
        val list = subcompose(ChatLayer.Timeline) { timeline(footer.height.toDp()) }.single().measure(constraints)
        layout(list.width, list.height) {
            list.placeRelative(0, 0)
            footer.placeRelative(0, list.height - footer.height)
        }
    }
}

@Composable
internal fun ChatComposer(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    commandNames: Map<ChatCommand, String>,
    main: Boolean,
    enabled: Boolean,
    image: String?,
    onRemoveImage: () -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
    sendAction: @Composable () -> Unit,
) {
    var expanded by remember(enabled) { mutableStateOf(false) }
    var composerHasFocus by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(17.dp)
    val hint = stringResource(R.string.elym_type_message)
    val availableCommands = commandNames.filterKeys { main || it != ChatCommand.Persona }
    val match = SlashCommandTrigger.match(draft)
    val candidates = if (enabled && composerHasFocus && !dismissed && !expanded && match != null) {
        availableCommands.filterValues { it.startsWith(match.query, ignoreCase = true) }
    } else {
        emptyMap()
    }
    val commandDraft = SlashCommandTrigger.prompt(draft.text, availableCommands.values)
    BackHandler(candidates.isNotEmpty()) { dismissed = true }
    Column(
        Modifier.fillMaxWidth().onFocusChanged { composerHasFocus = it.hasFocus }.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (image != null || candidates.isNotEmpty()) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (image != null) {
                    Row(
                        Modifier.padding(horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Photo(Uri.parse(image), Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        Text(stringResource(R.string.elym_attachment), Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                        RoundAction(R.drawable.elym_close, stringResource(R.string.elym_remove_image), onRemoveImage)
                    }
                }
                if (match != null && candidates.isNotEmpty()) {
                    Surface(shape = shape, color = SurfaceColor, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            candidates.forEach { (command, name) ->
                                val literal = "/$name"
                                val takesArgument = command == ChatCommand.Persona || command == ChatCommand.Checkpoint
                                val selectedDraft = match.replace(draft.text, literal, takesArgument)
                                DropdownMenuItem(
                                    text = { Text(literal, fontSize = 14.sp) },
                                    enabled = selectedDraft.text.length <= MaxDraftLength,
                                    onClick = {
                                        dismissed = true
                                        onDraftChange(selectedDraft)
                                        focusRequester.requestFocus()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                Modifier.weight(1f).background(PaperTop, shape).border(1.dp, Line, shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    RoundAction(
                        R.drawable.elym_plus, stringResource(R.string.elym_composer_add), { expanded = true }, enabled = enabled,
                    )
                    DropdownMenu(expanded, { expanded = false }, containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp)) {
                        ChatMenuItem(R.string.elym_add_image, R.drawable.elym_photo) {
                            expanded = false
                            dismissed = true
                            onPickImage()
                        }
                        ChatMenuItem(
                            R.string.elym_composer_commands, R.drawable.elym_chat,
                            enabled = commandDraft.text.length <= MaxDraftLength,
                        ) {
                            expanded = false
                            dismissed = false
                            onDraftChange(commandDraft)
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    }
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { value ->
                        if (value.text != draft.text || value.selection != draft.selection) dismissed = false
                        if (value.text.length <= MaxDraftLength) {
                            onDraftChange(value)
                        } else {
                            onDraftChange(TextFieldValue(value.text.take(MaxDraftLength), value.selection))
                        }
                    },
                    maxLines = 5,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        dismissed = true
                        onSend()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).semanticsLabel(hint),
                    decorationBox = { field ->
                        Box(Modifier.fillMaxWidth().padding(top = 11.dp, end = 11.dp, bottom = 11.dp)) {
                            if (draft.text.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp)
                            field()
                        }
                    },
                )
            }
            sendAction()
        }
    }
}
