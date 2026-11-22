package com.elymbot.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elymbot.android.R

@Composable
internal fun PersonaForm(
    persona: PersonaCard,
    personaTags: List<String>,
    isGlobal: Boolean,
    dismiss: () -> Unit,
    save: (PersonaCard, Boolean) -> Unit,
) {
    var edited by remember(persona) { mutableStateOf(persona) }
    val prompt = remember(persona) { TextFieldState(persona.prompt) }
    var makeGlobal by remember(persona.id) { mutableStateOf(false) }
    val tags = remember(personaTags, persona.tags) { (personaTags + persona.tags).distinct() }
    var cropAvatar by remember { mutableStateOf<Boolean?>(null) }
    val image = rememberBitmap(edited.cover)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) edited = edited.copy(cover = it, coverPosition = ImagePosition(), avatarPosition = ImagePosition())
    }
    val creating = persona.id.isEmpty()
    Sheet(
        stringResource(if (creating) R.string.elym_create_persona else R.string.elym_edit_persona),
        dismiss,
        maxContentHeightFraction = 0.88f,
        deliberateSwipe = true,
    ) {
        PersonaFormGroup(stringResource(R.string.elym_persona_basic_information)) {
            Field(stringResource(R.string.elym_name), edited.name, { edited = edited.copy(name = it.take(24)) })
            Field(stringResource(R.string.elym_description), edited.description, { edited = edited.copy(description = it.take(40)) })
            if (tags.isNotEmpty()) {
                PersonaTagField(tags, edited.tags) { edited = edited.copy(tags = it) }
            }
        }
        val promptLabel = stringResource(R.string.elym_prompt)
        PersonaFormSection(promptLabel) {
            PersonaPromptField(prompt, promptLabel)
        }
        PersonaFormGroup(stringResource(R.string.elym_persona_images)) {
            Action(
                stringResource(if (edited.cover == null) R.string.elym_pick_image else R.string.elym_replace_image),
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            PositionPreview(stringResource(R.string.elym_cover_position), image, edited.coverPosition, false) { cropAvatar = false }
            PositionPreview(stringResource(R.string.elym_avatar_position), image, edited.avatarPosition, true) { cropAvatar = true }
        }
        PersonaFormGroup(stringResource(R.string.elym_persona_usage)) {
            PersonaSwitch(stringResource(R.string.elym_add_favorite), edited.favorite) { edited = edited.copy(favorite = it) }
            if (!creating) {
                HorizontalDivider(color = Line)
                PersonaSwitch(stringResource(R.string.elym_set_global_persona), isGlobal || makeGlobal, enabled = !isGlobal) {
                    makeGlobal = it
                }
            }
        }
        Action(
            stringResource(if (creating) R.string.elym_create else R.string.elym_save),
            { save(edited.copy(name = edited.name.trim(), prompt = prompt.text.toString()), makeGlobal && !isGlobal) },
            primary = true, enabled = edited.name.isNotBlank(),
        )
    }
    cropAvatar?.let { avatar ->
        CropDialog(image, if (avatar) edited.avatarPosition else edited.coverPosition, avatar, { cropAvatar = null }) { position ->
            edited = if (avatar) edited.copy(avatarPosition = position) else edited.copy(coverPosition = position)
            cropAvatar = null
        }
    }
}

@Composable
private fun PersonaFormGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    PersonaFormSection(title) {
        Group {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun PersonaFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp))
        content()
    }
}

@Composable
private fun PersonaPromptField(state: TextFieldState, label: String) {
    val shape = RoundedCornerShape(11.dp)
    BasicTextField(
        state = state,
        modifier = Modifier.fillMaxWidth().semanticsLabel(label),
        inputTransformation = InputTransformation { if (length > 1200) replace(1200, length, "") },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
        textStyle = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 21.sp),
        cursorBrush = SolidColor(Accent),
        decorator = { field ->
            Box(Modifier.fillMaxWidth().background(PaperTop, shape).border(1.dp, Line, shape).padding(11.dp)) {
                field()
            }
        },
    )
}

@Composable
private fun PersonaSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = PaperTop, checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun PositionPreview(label: String, image: ImageBitmap?, position: ImagePosition, avatar: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val frame = if (avatar) Modifier.size(72.dp) else Modifier.fillMaxWidth().aspectRatio(1.79f)
        PersonaImage(
            image, position,
            frame.clip(RoundedCornerShape(16.dp)).then(
                if (image == null) Modifier else Modifier.semanticsLabel(label).clickable(onClick = onClick)
            ),
        )
    }
}

@Composable
private fun CropDialog(image: ImageBitmap?, initial: ImagePosition, avatar: Boolean, dismiss: () -> Unit, confirm: (ImagePosition) -> Unit) {
    var position by remember { mutableStateOf(initial) }
    val label = stringResource(if (avatar) R.string.elym_avatar_position else R.string.elym_cover_position)
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = SurfaceColor, contentColor = Ink) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val ratio = if (avatar) 1f else 1.79f
                    val width = minOf(maxWidth, if (avatar) 240.dp else 360.dp)
                    PersonaImage(
                        image, position,
                        Modifier.width(width).aspectRatio(ratio).clip(RoundedCornerShape(16.dp))
                            .semanticsLabel(stringResource(R.string.elym_crop_image)),
                        onPositionChange = { position = it },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Action(stringResource(R.string.elym_cancel), dismiss)
                    Action(stringResource(R.string.elym_done), { confirm(position) }, primary = true)
                }
            }
        }
    }
}
