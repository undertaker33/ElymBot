package com.elymbot.android.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R

internal object PrimaryMotion {
    const val FullMillis = 440
    const val FadeMillis = 300
    val Movement = CubicBezierEasing(0.20f, 0.54f, 0.30f, 1f)
    val Fade = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
}

private enum class PrimaryLayer { Header, Tags, Underlay, Content }

private const val PagePaperGradientEnd = 600f

@Composable
internal fun pageBackgroundBrush() = Brush.verticalGradient(listOf(PaperTop, Paper), endY = PagePaperGradientEnd)

@Composable
internal fun PrimaryPage(
    title: Int,
    current: Boolean,
    footerClearance: Dp = 0.dp,
    back: (() -> Unit)? = null,
    hint: Int = R.string.elym_search,
    section: Any? = null,
    canScrollBackward: () -> Boolean = { false },
    isScrollInProgress: () -> Boolean = { false },
    action: (@Composable () -> Unit)? = null,
    tags: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (query: String?, clearance: PaddingValues) -> Unit,
) {
    var scrollFraction by rememberSaveable(section) { mutableFloatStateOf(0f) }
    var dragging by remember(section) { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val tagScroll = if (tags != null) rememberScrollState() else null
    val closeSearch = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        searching = false
        draft = TextFieldValue()
    }
    LaunchedEffect(current) {
        if (!current && searching) {
            searching = false
            draft = TextFieldValue()
        }
    }
    PredictiveBackHandler(enabled = current && searching) { progress ->
        progress.collect {}
        closeSearch()
    }

    val collapseDistance = with(LocalDensity.current) { (if (tags != null) 104.dp else 70.dp).toPx() }
    val foldTarget = if (searching) 1f else scrollFraction
    val morph by animateFloatAsState(
        targetValue = foldTarget,
        animationSpec = if (dragging && !searching) snap() else tween(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement),
        label = "Primary search position",
    )
    val visibleProgress by rememberUpdatedState(morph)
    val canReveal by rememberUpdatedState(canScrollBackward)
    val observing by rememberUpdatedState(current && !searching)
    fun settle() {
        if (dragging) {
            scrollFraction = if (canReveal() && scrollFraction >= 0.5f) 1f else 0f
            dragging = false
        }
    }
    val scroll = remember(section, collapseDistance) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!observing || source != NestedScrollSource.UserInput || consumed.y == 0f) return Offset.Zero
                val start = if (dragging) scrollFraction else visibleProgress
                dragging = true
                scrollFraction = if (canReveal()) (start - consumed.y / collapseDistance).coerceIn(0f, 1f) else 0f
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                settle()
                return Velocity.Zero
            }
        }
    }
    val canScrollBack = canScrollBackward()
    val scrollInProgress = isScrollInProgress()
    LaunchedEffect(scrollInProgress, canScrollBack, searching) {
        if (!scrollInProgress || searching) settle()
        if (!canScrollBack && !searching) scrollFraction = 0f
    }

    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val chromeConstraints = constraints.copy(minHeight = 0)
        val header = subcompose(PrimaryLayer.Header) {
            PrimaryPageHeader(
                title, back, hint, morph, searching, draft, { draft = it }, action, focusRequester,
                onSearch = { searching = true }, onClose = closeSearch, onKeyboardSearch = { keyboard?.hide() },
                onFocusSearch = { if (current) { focusRequester.requestFocus(); keyboard?.show() } },
            )
        }.single().measure(chromeConstraints)
        val tagAlpha = (1f - morph * 1.8f).coerceAtLeast(0f)
        val tagStrip = if (tags != null && tagScroll != null) {
            subcompose(PrimaryLayer.Tags) {
                Box(Modifier.fillMaxWidth().height(TagStripHeight)) {
                    if (tagAlpha > 0f) {
                        Box(Modifier.graphicsLayer { alpha = tagAlpha; translationY = -20.dp.toPx() * morph }) {
                            TagStrip(tagScroll, tags)
                        }
                    }
                }
            }.single().measure(chromeConstraints)
        } else null
        val expandedChromeHeight = (header.height + (tagStrip?.height ?: 0)).toDp()
        val underlay = subcompose(PrimaryLayer.Underlay) {
            val background = pageBackgroundBrush()
            val paperTop = PaperTop
            val paper = Paper
            Box(
                Modifier.fillMaxWidth().height(expandedChromeHeight)
                    .graphicsLayer { alpha = (1f - morph * 1.8f).coerceAtLeast(0f) }
                    .drawWithCache {
                        val edgeHeight = minOf(10.dp.toPx(), size.height)
                        val edgeTop = size.height - edgeHeight
                        val edgeColor = lerp(paperTop, paper, (edgeTop / PagePaperGradientEnd).coerceIn(0f, 1f))
                        val fade = Brush.verticalGradient(listOf(edgeColor, edgeColor.copy(alpha = 0f)), edgeTop, size.height)
                        onDrawBehind {
                            drawRect(background, size = Size(size.width, edgeTop))
                            drawRect(fade, topLeft = Offset(0f, edgeTop), size = Size(size.width, edgeHeight))
                        }
                    }
                    .then(if (tagAlpha > 0f) Modifier.pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                    } else Modifier),
            )
        }.single().measure(chromeConstraints)
        // Reserve the same browsing endpoints through the whole gesture so moving
        // chrome never changes the scrollable range. Query results own their offset.
        val results = draft.text.trim().takeIf { searching }
        val tagClearance = if (results == null || tagAlpha > 0f) tagStrip?.height ?: 0 else 0
        val topClearance = (header.height + tagClearance).toDp()
        val body = subcompose(PrimaryLayer.Content) {
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(scroll)) {
                content(results, PaddingValues(top = topClearance, bottom = footerClearance))
            }
        }.single().measure(constraints)
        layout(body.width, body.height) {
            body.placeRelative(0, 0)
            underlay.placeRelative(0, 0)
            tagStrip?.placeRelative(0, header.height)
            header.placeRelative(0, 0)
        }
    }
}

@Composable
private fun PrimaryPageHeader(
    title: Int,
    back: (() -> Unit)?,
    hint: Int,
    morph: Float,
    searching: Boolean,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    action: (@Composable () -> Unit)?,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onKeyboardSearch: () -> Unit,
    onFocusSearch: () -> Unit,
) {
    val editing by animateFloatAsState(
        if (searching) 1f else 0f,
        tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade),
        label = "Page search input",
    )
    val titleAlpha = (1f - morph * 2.4f).coerceAtLeast(0f)
    val titleActionAlpha = (1f - morph * 7f).coerceAtLeast(0f)
    val searchLabel = stringResource(R.string.elym_search)
    val placeholder = stringResource(hint)
    val titleInset = if (back == null) 18.dp else 5.dp
    val buttonInset = if (action == null) 18.dp else 70.dp
    val headerHeight = with(LocalDensity.current) { maxOf(FloatingComposerSingleLineHeight + 12.dp, 42.sp.toDp()) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(headerHeight).clipToBounds()) {
        val rightInset = lerp(buttonInset, FloatingComposerHorizontalMargin, morph)
        val barWidth = lerp(48.dp, maxWidth - FloatingComposerHorizontalMargin * 2, morph)
        if (titleAlpha > 0f) {
            Row(
                Modifier.align(Alignment.CenterStart).fillMaxWidth()
                    .graphicsLayer { alpha = titleAlpha; translationY = -14.dp.toPx() * morph }
                    .padding(start = titleInset, end = rightInset + barWidth + 4.dp)
                    .then(if (searching) Modifier.semantics { hideFromAccessibility() } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back != null) RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), back)
                Text(
                    stringResource(title), Modifier.weight(1f).semantics { heading() }, color = Ink,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (action != null && titleActionAlpha > 0f) {
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
                        .graphicsLayer { alpha = titleActionAlpha; translationY = -14.dp.toPx() * morph }
                        .then(if (searching) Modifier.semantics { hideFromAccessibility() } else Modifier),
                ) { action() }
            }
        }
        val shape = RoundedCornerShape(24.dp)
        val frame = Modifier.align(Alignment.CenterEnd)
            .padding(end = rightInset)
            .width(barWidth)
            .height(lerp(48.dp, FloatingComposerSingleLineHeight, morph))
            .shadow(lerp(0.dp, 5.dp, morph), shape).clip(shape)
            .background(lerp(SurfaceColor, PaperTop, morph).copy(alpha = 0.98f - 0.08f * morph))
            .border(1.dp, lerp(Line, Accent, editing * 0.55f), shape)
        Row(
            frame.then(if (searching) Modifier else Modifier.clickable(role = Role.Button, onClick = onSearch).semanticsLabel(searchLabel)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.elym_search, color = Accent)
            }
            if (searching) {
                LaunchedEffect(Unit) { onFocusSearch() }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester)
                        .semanticsLabel(placeholder).graphicsLayer { alpha = editing },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onKeyboardSearch() }),
                    decorationBox = { input ->
                        Box {
                            if (draft.text.isEmpty()) {
                                Text(placeholder, color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            input()
                        }
                    },
                )
                Box(Modifier.graphicsLayer { alpha = editing }) {
                    RoundAction(R.drawable.elym_close, stringResource(R.string.elym_close_search), onClose)
                }
            } else if (morph > 0f) {
                Text(
                    searchLabel, Modifier.weight(1f).graphicsLayer { alpha = morph * (1f - editing) },
                    color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

internal fun String?.matchesPageSearch(vararg fields: String): Boolean =
    this == null || fields.any { it.contains(this, ignoreCase = true) }
