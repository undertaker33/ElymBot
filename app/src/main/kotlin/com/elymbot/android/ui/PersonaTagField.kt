package com.elymbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elymbot.android.R

private const val InlineTagLimit = 8

@Composable
internal fun PersonaTagField(tags: List<String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    Text(stringResource(R.string.elym_tags), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    if (tags.size <= InlineTagLimit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val checked = tag in selected
                FilterChip(
                    selected = checked,
                    onClick = { onChange(if (checked) selected - tag else selected + tag) },
                    label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Ink,
                        selectedContainerColor = Soft,
                        selectedLabelColor = Accent,
                    ),
                )
            }
        }
        return
    }

    var picking by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Action(stringResource(R.string.elym_choose_tags), { picking = true })
        Text(personaTagCount(selected.size), color = Muted, fontSize = 13.sp)
    }
    if (picking) {
        PersonaTagPicker(tags, selected, dismiss = { picking = false }) { updated ->
            onChange(updated)
            picking = false
        }
    }
}

@Composable
private fun PersonaTagPicker(tags: List<String>, selected: List<String>, dismiss: () -> Unit, confirm: (List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var pendingTags by remember { mutableStateOf(selected.toSet()) }
    val matchingTags = remember(tags, query) {
        val search = query.trim()
        tags.filter { it.contains(search, ignoreCase = true) }
    }
    val visibleTags = if (selectedOnly) {
        remember(matchingTags, pendingTags) { matchingTags.filter { it in pendingTags } }
    } else {
        matchingTags
    }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = SurfaceColor, contentColor = Ink) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.elym_choose_tags), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Input(query, { query = it }, stringResource(R.string.elym_search_tags), Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(personaTagCount(pendingTags.size), color = Muted, fontSize = 13.sp)
                    FilterChip(
                        selected = selectedOnly,
                        onClick = { selectedOnly = !selectedOnly },
                        label = { Text(stringResource(R.string.elym_tags_selected_only)) },
                    )
                }
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).weight(1f, fill = false)
                        .semanticsLabel(stringResource(R.string.elym_tags)),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (visibleTags.isEmpty()) {
                        item {
                            Text(stringResource(R.string.elym_no_matching_tags), Modifier.padding(8.dp), color = Muted, fontSize = 13.sp)
                        }
                    }
                    items(visibleTags, key = { it }) { tag ->
                        val checked = tag in pendingTags
                        Text(
                            tag,
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (checked) Soft else SurfaceColor)
                                .selectable(checked, role = Role.Button) {
                                    pendingTags = if (checked) pendingTags - tag else pendingTags + tag
                                }.wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (checked) Accent else Ink,
                            fontSize = 14.sp,
                            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Action(stringResource(R.string.elym_cancel), dismiss)
                    Action(stringResource(R.string.elym_done), { confirm(pendingTags.toList()) }, primary = true)
                }
            }
        }
    }
}

@Composable
private fun personaTagCount(count: Int): String = pluralStringResource(R.plurals.elym_tags_selected, count, count)
