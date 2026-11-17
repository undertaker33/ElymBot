package com.elymbot.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
    var makeGlobal by remember(persona.id) { mutableStateOf(false) }
    val tags = remember(personaTags, persona.tags) { (personaTags + persona.tags).distinct() }
    var cropAvatar by remember { mutableStateOf<Boolean?>(null) }
    val image = rememberBitmap(edited.cover)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) edited = edited.copy(cover = it, coverPosition = ImagePosition(), avatarPosition = ImagePosition())
    }
    val creating = persona.id.isEmpty()
    Sheet(stringResource(if (creating) R.string.elym_create_persona else R.string.elym_edit_persona), dismiss) {
        Field(stringResource(R.string.elym_name), edited.name, { edited = edited.copy(name = it.take(24)) })
        Field(stringResource(R.string.elym_description), edited.description, { edited = edited.copy(description = it.take(40)) })
        if (tags.isNotEmpty()) {
            Text(stringResource(R.string.elym_tags), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Group {
                tags.forEach { tag ->
                    val selected = tag in edited.tags
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = selected, role = Role.Checkbox) { checked ->
                                val selection = if (checked) edited.tags + tag else edited.tags - tag
                                edited = edited.copy(tags = selection)
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(tag, color = Ink, fontSize = 14.sp)
                    }
                }
            }
        }
        Field(stringResource(R.string.elym_prompt), edited.prompt, { edited = edited.copy(prompt = it.take(1200)) }, multiline = true)
        Text(stringResource(R.string.elym_cover), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Action(
            stringResource(if (edited.cover == null) R.string.elym_pick_image else R.string.elym_replace_image),
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        PositionPreview(stringResource(R.string.elym_cover_position), image, edited.coverPosition, false) { cropAvatar = false }
        PositionPreview(stringResource(R.string.elym_avatar_position), image, edited.avatarPosition, true) { cropAvatar = true }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.elym_add_favorite), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Checkbox(edited.favorite, { edited = edited.copy(favorite = it) }, modifier = Modifier.semanticsLabel(stringResource(R.string.elym_add_favorite)))
        }
        if (!creating) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = isGlobal || makeGlobal, enabled = !isGlobal, role = Role.Checkbox) { makeGlobal = it },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.elym_set_global_persona), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Checkbox(checked = isGlobal || makeGlobal, onCheckedChange = null, enabled = !isGlobal)
            }
        }
        HorizontalDivider(color = Line)
        Action(
            stringResource(if (creating) R.string.elym_create else R.string.elym_save),
            { save(edited.copy(name = edited.name.trim()), makeGlobal && !isGlobal) },
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
