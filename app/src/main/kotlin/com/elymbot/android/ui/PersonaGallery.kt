package com.elymbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R

@Composable
internal fun GalleryPage(
    model: ElymBotViewModel,
    personas: List<PersonaCard>,
    tags: List<String>,
    activeId: String?,
    unread: Boolean,
    onAdd: () -> Unit,
    onOpen: (PersonaCard) -> Unit,
) {
    val allLabel = stringResource(R.string.elym_all)
    val favoritesLabel = stringResource(R.string.elym_favorites)
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var customTag by rememberSaveable { mutableStateOf<String?>(null) }
    var addingTag by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val tag = customTag?.takeIf { it in tags }
    val searchText = query.trim()
    val visible = personas.filter {
        (!favoritesOnly || it.favorite) && (tag == null || tag in it.tags) &&
            (listOf(it.name, it.description, it.prompt) + it.tags).any { value -> value.contains(searchText, ignoreCase = true) }
    }

    Column {
        Header(stringResource(R.string.elym_persona), serif = true) {
            RoundAction(R.drawable.elym_search, stringResource(R.string.elym_search), { searching = !searching; if (!searching) query = "" }, outlined = true)
            RoundAction(R.drawable.elym_plus, stringResource(R.string.elym_create_persona), onAdd, primary = true)
        }
        if (searching) Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Input(query, { query = it }, stringResource(R.string.elym_search_hint), Modifier.weight(1f))
            RoundAction(R.drawable.elym_close, stringResource(R.string.elym_close_search), { searching = false; query = "" })
        }
        TagStrip {
            TagChip(allLabel, !favoritesOnly && tag == null) { favoritesOnly = false; customTag = null }
            TagChip(favoritesLabel, favoritesOnly) { favoritesOnly = true; customTag = null }
            tags.forEach { name ->
                key(name) {
                    val label = if (name.equals(allLabel, ignoreCase = true) || name.equals(favoritesLabel, ignoreCase = true)) {
                        stringResource(R.string.elym_tag_with_prefix, name)
                    } else name
                    TagChip(label, name == tag) { favoritesOnly = false; customTag = name }
                }
            }
            TagChip(stringResource(R.string.elym_add_tag)) { addingTag = true }
        }
        if (visible.isEmpty()) {
            EmptyMessage(stringResource(R.string.elym_no_personas), R.drawable.elym_search) {
                Action(stringResource(R.string.elym_clear_filter), { favoritesOnly = false; customTag = null; query = "" })
            }
        } else LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            itemsIndexed(visible, key = { _, item -> item.id }, span = { index, _ -> GridItemSpan(if (index == 0) maxLineSpan else 1) }) { index, item ->
                GalleryCard(item, index == 0, item.id == activeId, unread && item.id == activeId) { onOpen(item) }
            }
        }
    }
    if (addingTag) {
        AddPersonaTagSheet(model, allLabel, favoritesLabel, { addingTag = false }) { name ->
            favoritesOnly = false
            customTag = name
            query = ""
            addingTag = false
        }
    }
}

@Composable
private fun AddPersonaTagSheet(
    model: ElymBotViewModel,
    allLabel: String,
    favoritesLabel: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val trimmed = name.trim()
    val reserved = trimmed.equals(allLabel, ignoreCase = true) || trimmed.equals(favoritesLabel, ignoreCase = true)
    val hasSeparator = '\u001f' in trimmed
    Sheet(stringResource(R.string.elym_add_tag), { if (!saving) onDismiss() }) {
        Field(stringResource(R.string.elym_tag_name), name, { name = it })
        if (reserved) {
            Text(stringResource(R.string.elym_tag_reserved), color = Danger, fontSize = 13.sp)
        } else if (hasSeparator) {
            Text(stringResource(R.string.elym_tag_separator), color = Danger, fontSize = 13.sp)
        }
        Action(stringResource(R.string.elym_save), {
            saving = true
            model.perform {
                try {
                    model.service.createPersonaTag(trimmed)
                    onSaved(trimmed)
                } finally {
                    saving = false
                }
            }
        }, primary = true, enabled = trimmed.isNotEmpty() && !reserved && !hasSeparator && !saving)
    }
}

@Composable
private fun GalleryCard(persona: PersonaCard, featured: Boolean, active: Boolean, unread: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (featured) 1.79f else 1.06f).shadow(2.dp, shape)
            .clip(shape).background(Accent).clickable(onClick = onClick),
    ) {
        PersonaImage(persona, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.29f to Color.Transparent, 0.65f to Color(0x80150E13), 1f to Color(0xE8150E13))))
        if (active) Row(
            Modifier.align(Alignment.TopStart).padding(11.dp).background(Color(0xFFFAE8C7), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.elym_check), null, Modifier.size(13.dp), tint = Color(0xFF512F23))
            Text(stringResource(R.string.elym_active), color = Color(0xFF512F23), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        if (unread) Box(Modifier.align(Alignment.TopEnd).padding(11.dp)) { Badge(stringResource(R.string.elym_unread)) }
        Column(Modifier.align(Alignment.BottomStart).padding(if (featured) 16.dp else 12.dp)) {
            Text(persona.name, color = Color(0xFFFFFBF3), fontSize = if (featured) 32.sp else 23.sp, fontFamily = GalleryFont, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (persona.description.isNotBlank()) Text(persona.description, Modifier.padding(top = 3.dp), color = Color(0xFFFFEFE5), fontSize = if (featured) 12.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
