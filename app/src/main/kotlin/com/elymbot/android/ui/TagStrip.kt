package com.elymbot.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R

internal val TagStripHeight = 61.dp

@Composable
internal fun TagStrip(scrollState: ScrollState, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(TagStripHeight).horizontalScroll(scrollState).selectableGroup()
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun TagChip(
    label: String,
    selected: Boolean? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val chosen = selected == true
    val textSize = 13.sp
    val horizontalPadding = 13.dp
    val minimumLabelWidth = with(LocalDensity.current) { (textSize.roundToPx() * 2).toDp() }
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (chosen) Accent else SurfaceColor, tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade), label = "Filter fill")
    val stroke by animateColorAsState(if (chosen) Accent else Line, tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade), label = "Filter outline")
    val textColor by animateColorAsState(if (chosen) AccentInk else Ink, tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade), label = "Filter text")
    val action = when {
        selected == null -> Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
        onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick,
            onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
        ).semantics { this.selected = chosen }
        else -> Modifier.selectable(selected, interaction, null, role = Role.Tab, onClick = onClick)
    }
    Box(
        Modifier.height(48.dp).widthIn(min = 48.dp, max = 220.dp).then(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(32.dp).clip(CircleShape).background(fill).border(1.dp, stroke, CircleShape)
                .indication(interaction, ripple(bounded = true, color = Ink))
                .padding(horizontal = horizontalPadding).widthIn(min = minimumLabelWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (selected == null) {
                Glyph(R.drawable.elym_plus, color = Accent)
            } else {
                Text(label, color = textColor, fontSize = textSize, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
