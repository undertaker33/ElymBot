package com.elymbot.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val DockHeight = 66.dp
private val DockVerticalMargin = 10.dp
internal val FloatingNavigationClearance = DockHeight + DockVerticalMargin * 2

internal data class DockDestination(val label: Int, val icon: Int)

@Composable
internal fun FloatingNavigationDock(
    destinations: List<DockDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = destinations.size
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val select by rememberUpdatedState(onSelect)
    val currentIndex by rememberUpdatedState(selectedIndex)
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var stretch by remember { mutableFloatStateOf(0f) }
    val dragging = dragPosition != null
    val tabIndex = if (rtl) count - 1 - selectedIndex else selectedIndex
    val restingCenter = (tabIndex + 0.5f) / count
    val center = animateFloatAsState(
        dragPosition ?: restingCenter,
        if (dragging) snap() else tween(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement),
        label = "Navigation lens position",
    )
    val lift = animateFloatAsState(
        if (dragging) 1f else 0f,
        tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Movement),
        label = "Navigation lens lift",
    )
    val shape = RoundedCornerShape(28.dp)
    val lensFill = Soft
    val lensRim = ElymColors.floatingBorder
    val accent = Accent
    Row(
        modifier.padding(horizontal = 12.dp, vertical = DockVerticalMargin)
            .fillMaxWidth().height(DockHeight).shadow(8.dp, shape).clip(shape)
            .background(SurfaceColor.copy(alpha = 0.86f)).border(1.dp, lensRim, shape)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .pointerInput(count, rtl) {
                val tracker = VelocityTracker()
                fun positionAt(x: Float): Float = (x / size.width).coerceIn(0.5f / count, 1f - 0.5f / count)
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        tracker.resetTracking()
                        dragPosition = positionAt(start.x)
                        stretch = 0f
                    },
                    onHorizontalDrag = { change, _ ->
                        dragPosition = positionAt(change.position.x)
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val slotSpeed = abs(tracker.calculateVelocity().x) / (size.width.toFloat() / count)
                        stretch += ((slotSpeed / 25f).coerceAtMost(0.14f) - stretch) * 0.4f
                    },
                    onDragCancel = { dragPosition = null },
                    onDragEnd = {
                        dragPosition?.let { position ->
                            val visualIndex = (position * count).toInt().coerceIn(0, count - 1)
                            val logicalIndex = if (rtl) count - 1 - visualIndex else visualIndex
                            if (logicalIndex != currentIndex) select(logicalIndex)
                        }
                        dragPosition = null
                    },
                )
            }
            .selectableGroup()
            .drawWithCache {
                val tint = Paint().apply { colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn) }
                val clip = Path()
                val tabWidth = size.width / count
                val spacing = 8.dp.toPx()
                val lensHeight = (size.height - 8.dp.toPx()).coerceAtLeast(0f)
                val rimWidth = 1.dp.toPx()
                onDrawWithContent {
                    val lifted = lift.value
                    val deformation = stretch * lifted
                    val width = ((tabWidth - spacing) * (1 + deformation) + 10.dp.toPx() * lifted)
                        .coerceIn(0f, size.width)
                    val height = (lensHeight + 7.dp.toPx() * lifted - lensHeight * deformation * 0.35f)
                        .coerceIn(0f, size.height)
                    val middle = (center.value * size.width).coerceIn(width / 2, size.width - width / 2)
                    val topLeft = Offset(middle - width / 2, (size.height - height) / 2)
                    val bounds = Rect(topLeft, Size(width, height))
                    val radius = CornerRadius(height * 0.48f)
                    drawRoundRect(
                        color = lensFill.copy(alpha = 0.74f - lifted * 0.20f),
                        topLeft = topLeft, size = bounds.size, cornerRadius = radius,
                    )
                    if (lifted > 0f) {
                        drawRoundRect(
                            color = lensRim.copy(alpha = lifted * 0.82f),
                            topLeft = topLeft, size = bounds.size, cornerRadius = radius,
                            style = Stroke(rimWidth),
                        )
                    }
                    drawContent()
                    // Tint only the parts of the existing icon and label pixels inside the lens.
                    clip.rewind()
                    clip.addRoundRect(RoundRect(bounds, radius))
                    clipPath(clip) {
                        drawContext.canvas.withSaveLayer(bounds, tint) { this@onDrawWithContent.drawContent() }
                    }
                }
            },
    ) {
        destinations.forEachIndexed { index, destination ->
            Column(
                Modifier.weight(1f).fillMaxHeight().selectable(
                    selected = selectedIndex == index,
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                ) { if (index != selectedIndex) onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Glyph(destination.icon, color = Ink)
                Text(
                    stringResource(destination.label), color = Ink, fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
