package com.elymbot.android.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.elymbot.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal val Paper = Color(0xFFF7F0E6)
internal val PaperTop = Color(0xFFFCF7EF)
internal val SurfaceColor = Color(0xFFFFFBF5)
internal val Ink = Color(0xFF35251D)
internal val Muted = Color(0xFF735D50)
internal val Accent = Color(0xFF7B4733)
internal val AccentInk = Color(0xFFFFF8F2)
internal val Soft = Color(0xFFF1E0D1)
internal val Line = Color(0xFFE5D7C8)
internal val Danger = Color(0xFFA52B32)
internal val GalleryFont = FontFamily(Font(R.font.gallery_song, FontWeight.Bold))

@Composable
internal fun Glyph(id: Int, modifier: Modifier = Modifier, color: Color = Ink) {
    Icon(painterResource(id), null, modifier.size(22.dp), tint = color)
}

@Composable
internal fun RoundAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    outlined: Boolean = false,
    enabled: Boolean = true,
    roundedSquare: Boolean = false,
) {
    val shape = if (roundedSquare) RoundedCornerShape(16.dp) else CircleShape
    Box(
        Modifier.size(48.dp).clip(shape)
            .then(if (primary) Modifier.background(Accent) else Modifier)
            .then(if (outlined) Modifier.background(SurfaceColor).border(1.dp, Line, shape) else Modifier)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semanticsLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, color = when {
            !enabled -> if (primary) AccentInk.copy(alpha = 0.5f) else Muted.copy(alpha = 0.5f)
            primary -> AccentInk
            outlined -> Accent
            else -> Ink
        })
    }
}

@Composable
internal fun Action(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(13.dp)
    val background = when {
        danger -> Danger
        primary -> Accent
        else -> Soft
    }
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape).background(background.copy(alpha = if (enabled) 1f else 0.55f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary || danger) AccentInk else Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(SurfaceColor).border(1.dp, Line, shape), content = content)
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp))
        Group(content)
    }
}

@Composable
internal fun ListRow(
    title: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    selected: Boolean = false,
    trailing: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 63.dp).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(Soft, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = Accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) Badge(badge, selected)
        if (trailing) Icon(painterResource(R.drawable.elym_chevron), null, Modifier.size(15.dp), tint = Muted)
    }
}

@Composable
internal fun Badge(label: String, success: Boolean = false) {
    Text(
        label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        color = if (success) Color(0xFF356445) else Accent,
        modifier = Modifier.background(if (success) Color(0xFFE9EFE2) else Soft, CircleShape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
internal fun EmptyMessage(label: String, icon: Int, content: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = Accent)
        Text(label, color = Muted, fontSize = 13.sp)
        content?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = SurfaceColor,
        contentColor = Ink, scrimColor = Color(0x6B261A18),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(34.dp, 4.dp).background(Line, CircleShape))
        },
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).imePadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundAction(R.drawable.elym_close, stringResource(R.string.elym_close), onDismiss)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String = "",
    multiline: Boolean = false,
    password: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Input(value, onChange, hint, Modifier.fillMaxWidth(), multiline, password, label)
    }
}

@Composable
internal fun Input(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    password: Boolean = false,
    label: String = hint,
    compact: Boolean = false,
    send: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (compact) 17.dp else 11.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = !multiline, maxLines = if (compact) 5 else Int.MAX_VALUE,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 21.sp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(Accent),
        keyboardOptions = if (send != null) KeyboardOptions(imeAction = ImeAction.Send) else KeyboardOptions.Default,
        keyboardActions = if (send != null) KeyboardActions(onSend = { send() }) else KeyboardActions.Default,
        modifier = modifier.semanticsLabel(label),
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = if (multiline && !compact) 75.dp else 48.dp)
                    .background(PaperTop, shape).border(1.dp, Line, shape).padding(11.dp),
                contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp)
                field()
            }
        },
    )
}

@Composable
internal fun PersonaImage(persona: PersonaCard, modifier: Modifier, avatar: Boolean = false) {
    val custom = rememberBitmap(persona.cover)
    if (custom == null) {
        Box(
            modifier.background(Brush.verticalGradient(listOf(Soft, Accent))),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(R.drawable.elym_person, color = AccentInk)
        }
        return
    }
    val position = if (avatar) persona.avatarPosition else persona.coverPosition
    Canvas(modifier.background(Soft)) {
        val sourceWidth = custom.width
        val sourceHeight = custom.height
        val scale = maxOf(size.width / sourceWidth, size.height / sourceHeight)
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        drawImage(
            custom, srcSize = IntSize(sourceWidth, sourceHeight),
            dstOffset = IntOffset(((size.width - width) * position.x).roundToInt(), ((size.height - height) * position.y).roundToInt()),
            dstSize = IntSize(width, height),
        )
    }
}

@Composable
internal fun Photo(uri: Uri, modifier: Modifier = Modifier) {
    val bitmap = rememberBitmap(uri)
    if (bitmap != null) Image(bitmap, stringResource(R.string.elym_attachment), modifier, contentScale = ContentScale.Fit)
}

@Composable
private fun rememberBitmap(uri: Uri?): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri, context) {
        value = null
        value = withContext(Dispatchers.IO) {
            uri?.let {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
                    val options = BitmapFactory.Options().apply {
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 2048) {
                            inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                        }
                    }
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                    }
                } catch (_: java.io.IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
        }
    }
    return bitmap
}

internal fun issueLabel(issue: UiIssue): Int = when (issue) {
    UiIssue.MainModelMissing -> R.string.elym_main_missing
    UiIssue.VisionModelMissing -> R.string.elym_vision_missing
    UiIssue.VisionCapabilityUnknown -> R.string.elym_vision_unknown
    UiIssue.Authentication -> R.string.elym_auth_error
    UiIssue.Connection -> R.string.elym_connection_error
    UiIssue.ServiceResponse -> R.string.elym_service_response_error
    UiIssue.ServiceRejected -> R.string.elym_service_rejected
    UiIssue.ServiceProtocol -> R.string.elym_service_protocol_error
    UiIssue.TimedOut -> R.string.elym_timed_out
    UiIssue.Busy -> R.string.elym_busy
    UiIssue.OwnedSessions -> R.string.elym_owned_sessions
    UiIssue.InvalidInput -> R.string.elym_invalid_input
    UiIssue.Failed -> R.string.elym_failed
}

internal fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
