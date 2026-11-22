package com.elymbot.android.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R

@Composable
internal fun LocalChatReceipt(text: String) {
    val multiline = '\n' in text
    val shape = if (multiline) RoundedCornerShape(12.dp) else CircleShape
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = if (multiline) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier,
            shape = shape,
            color = SurfaceColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        ) {
            SelectionContainer {
                Text(
                    text,
                    Modifier.padding(horizontal = 12.dp, vertical = if (multiline) 10.dp else 6.dp),
                    color = if (multiline) Ink else Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = if (multiline) TextAlign.Start else TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun MessageBubble(message: ChatMessage, persona: String) {
    val shape = if (message.assistant) RoundedCornerShape(6.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 6.dp, 16.dp, 16.dp)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.assistant) Alignment.Start else Alignment.End,
    ) {
        if (message.assistant) Text(message.author ?: persona, Modifier.padding(start = 4.dp, bottom = 4.dp), color = Muted, fontSize = 11.sp)
        Column(
            Modifier.widthIn(max = 345.dp).background(if (message.assistant) SurfaceColor else Soft, shape)
                .then(if (message.assistant) Modifier.border(1.dp, Line, shape) else Modifier)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            message.image?.let { Photo(it, Modifier.widthIn(max = 210.dp).heightIn(max = 175.dp).clip(RoundedCornerShape(8.dp))) }
            if (message.text.isNotEmpty()) Text(message.text, color = Ink, fontSize = 15.sp, lineHeight = 24.sp)
            else if (message.status == MessageStatus.Waiting) {
                WaitingDots()
            }
        }
        val status = when (message.status) {
            MessageStatus.Stopped -> R.string.elym_stopped
            MessageStatus.Interrupted -> R.string.elym_interrupted
            else -> null
        }
        if (status != null) Text(stringResource(status), Modifier.padding(horizontal = 4.dp, vertical = 5.dp), color = Muted, fontSize = 11.sp)
    }
}

@Composable
internal fun WaitingDots() {
    val pulse = rememberInfiniteTransition(label = "Waiting dots")
    Row(
        Modifier.semanticsLabel(stringResource(R.string.elym_waiting)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            val opacity = pulse.animateFloat(
                initialValue = 0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "Waiting dot ${index + 1}",
            )
            Box(Modifier.size(5.dp).graphicsLayer { alpha = opacity.value }.background(Accent, CircleShape))
        }
    }
}
