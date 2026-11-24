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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
internal fun MessageBubble(
    message: ChatMessage,
    persona: String,
    modifier: Modifier = Modifier,
    onRetryDelivery: ((String) -> Unit)?,
) {
    val shape = if (message.assistant) RoundedCornerShape(6.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 6.dp, 16.dp, 16.dp)
    Column(
        modifier.fillMaxWidth(),
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
                WaitingDots(Modifier.semanticsLabel(stringResource(R.string.elym_waiting)))
            }
        }
        val status = when (message.status) {
            MessageStatus.Stopped -> R.string.elym_stopped
            MessageStatus.Interrupted -> R.string.elym_interrupted
            else -> null
        }
        if (status != null) Text(stringResource(status), Modifier.padding(horizontal = 4.dp, vertical = 5.dp), color = Muted, fontSize = 11.sp)
        message.deliveryTargets.forEach { target ->
            if (target.channelId != null || target.status != ChatDeliveryStatus.Delivered) {
                ReplyDeliveryStatusLine(
                    target.channelId, target.conversationId, target.status,
                    onRetry = if (target.status == ChatDeliveryStatus.Failed && onRetryDelivery != null) {
                        { onRetryDelivery(target.id) }
                    } else null,
                )
            }
        }
    }
}

@Composable
internal fun ReplyDeliveryStatusLine(
    channelId: String?,
    conversationId: String?,
    status: ChatDeliveryStatus,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val target = when {
        channelId == null -> stringResource(R.string.elym_reply_target_app)
        conversationId == null -> channelId
        else -> stringResource(R.string.elym_reply_target_conversation, channelId, conversationId)
    }
    val statusLabel = stringResource(when (status) {
        ChatDeliveryStatus.Pending -> R.string.elym_reply_delivery_pending
        ChatDeliveryStatus.Sending -> R.string.elym_reply_delivery_sending
        ChatDeliveryStatus.Delivered -> R.string.elym_reply_delivery_delivered
        ChatDeliveryStatus.Failed -> R.string.elym_reply_delivery_failed
        ChatDeliveryStatus.Unknown -> R.string.elym_reply_delivery_unknown
        ChatDeliveryStatus.Cancelled -> R.string.elym_reply_delivery_cancelled
        ChatDeliveryStatus.Stale -> R.string.elym_reply_delivery_stale
    })
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.elym_reply_target_status, target, statusLabel),
            Modifier.weight(1f, fill = false),
            color = if (status == ChatDeliveryStatus.Failed) Danger else Muted,
            fontSize = 12.sp,
        )
        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.semanticsLabel(stringResource(R.string.elym_reply_retry_target, target)),
            ) { Text(stringResource(R.string.elym_retry)) }
        }
    }
}

@Composable
internal fun ReplyProgress(toolProgressActive: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        WaitingDots()
        Text(
            stringResource(if (toolProgressActive) R.string.elym_tool_progress else R.string.elym_generating),
            color = Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WaitingDots(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "Waiting dots")
    Row(
        modifier,
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
