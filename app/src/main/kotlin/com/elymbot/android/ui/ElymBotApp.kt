package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.R
import com.elymbot.android.data.AppearanceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class ElymBotViewModel(val service: ElymBotUiService) : ViewModel() {
    var issue by mutableStateOf<UiIssue?>(null)
        private set

    fun clearIssue() { issue = null }

    fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: UiServiceException) {
                issue = failure.issue
            } catch (_: Exception) {
                issue = UiIssue.Failed
            }
        }
    }
}

private enum class Page(val title: Int, val icon: Int) {
    Gallery(R.string.elym_persona, R.drawable.elym_person),
    Models(R.string.elym_config, R.drawable.elym_sliders),
    Platform(R.string.elym_platform, R.drawable.elym_platform),
    Plugins(R.string.elym_plugins, R.drawable.elym_plugin),
    Settings(R.string.elym_settings, R.drawable.elym_settings),
    Chat(R.string.elym_chat, R.drawable.elym_chat),
    Sessions(R.string.elym_sessions, R.drawable.elym_chat),
}

private data class Destination(
    val page: Page = Page.Gallery,
    val mainId: String? = null,
    val ownerId: String? = null,
    val sessionId: String? = null,
    val title: String = "",
) {
    val depth: Int
        get() = when (page) {
            Page.Chat -> if (sessionId == null) 1 else 3
            Page.Sessions -> 2
            Page.Models -> if (mainId == null) 0 else if (sessionId == null) 2 else 4
            else -> 0
        }
    val displayedChatId: String? get() = if (page == Page.Chat) sessionId ?: mainId else null
    val key: String get() = "${page.name}:$mainId:$ownerId:$sessionId"

    fun back(): Destination = when (page) {
        Page.Models -> if (depth == 0) Destination() else copy(page = Page.Chat)
        Page.Chat -> if (sessionId == null) Destination() else copy(page = Page.Sessions, sessionId = null, title = "")
        Page.Sessions -> copy(page = Page.Chat, ownerId = null)
        else -> Destination()
    }

    companion object {
        val Saver = listSaver<Destination, String>(
            save = { listOf(it.page.name, it.mainId.orEmpty(), it.ownerId.orEmpty(), it.sessionId.orEmpty(), it.title) },
            restore = { (page, main, owner, session, title) ->
                Destination(Page.valueOf(page), main.ifEmpty { null }, owner.ifEmpty { null }, session.ifEmpty { null }, title)
            },
        )
    }
}

private val FadeEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val SlideEasing = CubicBezierEasing(0.20f, 0.54f, 0.30f, 1f)

@Composable
internal fun ElymBotApp(
    service: ElymBotUiService,
    appearanceMode: AppearanceMode,
    darkTheme: Boolean,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
) {
    val model: ElymBotViewModel = viewModel { ElymBotViewModel(service) }
    val personas by service.personas.collectAsStateWithLifecycle(emptyList())
    val personaTags by service.personaTags.collectAsStateWithLifecycle(emptyList())
    val activeId by service.activePersonaId.collectAsStateWithLifecycle(null)
    val main by service.mainSession.collectAsStateWithLifecycle(null)
    val config by service.models.collectAsStateWithLifecycle(ModelConfiguration())
    val commands by service.commandNames.collectAsStateWithLifecycle(emptyMap())
    val stateHolder = rememberSaveableStateHolder()
    var destination by rememberSaveable(stateSaver = Destination.Saver) { mutableStateOf(Destination()) }
    val focusManager = LocalFocusManager.current
    var personaForm by remember { mutableStateOf<PersonaCard?>(null) }
    var deletePersona by remember { mutableStateOf<PersonaCard?>(null) }
    val active = personas.firstOrNull { it.id == activeId }
    fun navigate(next: Destination) {
        if (destination.key != next.key) focusManager.clearFocus(force = true)
        destination = next
    }
    val back = { navigate(destination.back()) }
    BackHandler(destination.page != Page.Gallery, back)
    val visibleChatId = destination.displayedChatId
    LifecycleStartEffect(visibleChatId) {
        model.perform { service.setViewedConversation(visibleChatId) }
        onStopOrDispose { model.perform { service.setViewedConversation(null) } }
    }

    ElymBotTheme(darkTheme) {
        val pageBackground = Brush.verticalGradient(listOf(PaperTop, Paper, Paper), endY = 1200f)
        Box(
            Modifier.fillMaxSize()
                .background(pageBackground)
                .safeDrawingPadding(),
        ) {
            val navigationTransition = updateTransition(destination, label = "Page navigation")
            navigationTransition.AnimatedContent(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentKey = { it.key },
                transitionSpec = {
                    val offset = tween<IntOffset>(300, easing = SlideEasing)
                    val fade = tween<Float>(220, easing = FadeEasing)
                    when {
                        initialState.depth < targetState.depth ->
                            slideInHorizontally(offset) { it } togetherWith slideOutHorizontally(offset) { -(it * 0.3f).roundToInt() }
                        initialState.depth > targetState.depth ->
                            slideInHorizontally(offset) { -(it * 0.3f).roundToInt() } togetherWith slideOutHorizontally(offset) { it }
                        else -> fadeIn(fade) togetherWith fadeOut(fade)
                    }.using(null).apply { targetContentZIndex = targetState.depth.toFloat() }
                },
            ) { screen ->
                val current = screen.key == destination.key
                val scrim = transition.animateFloat(
                    transitionSpec = { tween(300, easing = SlideEasing) },
                    label = "Covered page scrim",
                ) { state ->
                    when (state) {
                        EnterExitState.Visible -> 0f
                        EnterExitState.PreEnter -> if (screen.depth < navigationTransition.currentState.depth) 0.10f else 0f
                        EnterExitState.PostExit -> if (screen.depth < navigationTransition.targetState.depth) 0.10f else 0f
                    }
                }
                val backFromScreen = { if (destination.key == screen.key) back() }
                val persona = if (screen.sessionId == null && screen.page == Page.Chat) active
                    else personas.firstOrNull { it.id == screen.ownerId }
                Box(
                    Modifier.fillMaxSize().background(pageBackground).then(
                        if (current) Modifier else Modifier.semantics { hideFromAccessibility() }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                    ),
                ) {
                    when (screen.page) {
                        Page.Gallery -> stateHolder.SaveableStateProvider(Page.Gallery.name) { GalleryPage(
                            model, personas, personaTags, activeId, main?.unread == true, { if (current) personaForm = PersonaCard() },
                        ) { selected ->
                            val target = main
                            if (current && target != null) model.perform {
                                if (!target.running && selected.id != activeId) service.activatePersona(selected.id)
                                if (destination.key == screen.key) navigate(Destination(Page.Chat, mainId = target.id))
                            }
                        } }
                        Page.Models -> ModelsPage(model, config, backFromScreen.takeIf { screen.depth > 0 }, current)
                        Page.Settings -> SettingsPage(model, commands, current, appearanceMode, onAppearanceModeChange)
                        Page.Sessions -> if (persona != null) SessionsPage(model, persona, backFromScreen, current) { id, title ->
                            if (destination.key == screen.key) navigate(screen.copy(page = Page.Chat, sessionId = id, title = title))
                        }
                        Page.Chat -> screen.displayedChatId?.let { id ->
                            if (persona != null) stateHolder.SaveableStateProvider(id) {
                                ChatPage(
                                    model, persona, id, screen.title, screen.sessionId == null, personas, backFromScreen, current,
                                    onSessions = { ownerId -> if (current) navigate(screen.copy(page = Page.Sessions, ownerId = ownerId)) },
                                    onEdit = { if (current) personaForm = persona },
                                    onDelete = { if (current) deletePersona = persona },
                                    onModelSettings = { if (current) navigate(screen.copy(page = Page.Models)) },
                                    onRename = { if (destination.key == screen.key) destination = screen.copy(title = it) },
                                )
                            }
                        }
                        else -> Unit
                    }
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = scrim.value }.background(Color.Black))
                }
            }
            var selectedRoot by remember { mutableStateOf(destination.page.takeIf { destination.depth == 0 } ?: Page.Gallery) }
            SideEffect { if (destination.depth == 0) selectedRoot = destination.page }
            AnimatedVisibility(
                visible = destination.depth == 0,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(300, easing = SlideEasing)) { it } + fadeIn(tween(300, easing = SlideEasing)),
                exit = slideOutVertically(tween(300, easing = SlideEasing)) { it } + fadeOut(tween(300, easing = SlideEasing)),
                label = "Root navigation",
            ) {
                Navigation(selectedRoot, Modifier) { page ->
                    if (destination.depth == 0) navigate(Destination(page))
                }
            }

        }
        personaForm?.let { existing ->
            PersonaForm(existing, personaTags, { personaForm = null }) { edited ->
                model.perform { service.savePersona(edited); personaForm = null }
            }
        }
        deletePersona?.let { target ->
            val sessions by remember(target.id) { service.sessions(target.id) }.collectAsStateWithLifecycle(emptyList())
            val hasSessions = sessions.isNotEmpty()
            val dismiss = { deletePersona = null }
            Sheet(stringResource(if (hasSessions) R.string.elym_owned_sessions else R.string.elym_delete_persona), dismiss) {
                Text(if (hasSessions) stringResource(R.string.elym_persona_has_sessions) else stringResource(R.string.elym_confirm_persona, target.name))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSessions) Action(stringResource(R.string.elym_view_sessions), { dismiss(); navigate(destination.copy(page = Page.Sessions, ownerId = target.id)) }, primary = true)
                    else Action(stringResource(R.string.elym_delete_persona), {
                        model.perform { service.deletePersona(target.id); dismiss(); navigate(Destination()) }
                    }, danger = true)
                    Action(stringResource(R.string.elym_cancel), dismiss)
                }
            }
        }
        model.issue?.let { issue ->
            AlertDialog(
                onDismissRequest = model::clearIssue,
                text = { Text(stringResource(issueLabel(issue))) },
                confirmButton = { TextButton(model::clearIssue) { Text(stringResource(R.string.elym_close)) } },
            )
        }
    }
}

@Composable
internal fun Header(
    title: String,
    onBack: (() -> Unit)? = null,
    serif: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 65.dp)
            .padding(start = if (onBack != null) 5.dp else 18.dp, end = 18.dp, top = 6.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), onBack)
        Text(
            title, Modifier.weight(1f), color = Ink, fontSize = if (serif) 40.sp else 26.sp,
            fontWeight = FontWeight.Bold, fontFamily = if (serif) GalleryFont else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
private fun Navigation(selected: Page, modifier: Modifier, onSelect: (Page) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(66.dp)
            .shadow(8.dp, shape).clip(shape).background(SurfaceColor.copy(alpha = 0.93f))
            .border(1.dp, ElymColors.floatingBorder, shape).padding(horizontal = 6.dp, vertical = 5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        listOf(Page.Gallery, Page.Models, Page.Platform, Page.Plugins, Page.Settings).forEach { destination ->
            val enabled = destination != Page.Platform && destination != Page.Plugins
            val current = selected == destination
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier.weight(1f).fillMaxHeight().selectable(
                    selected = current, enabled = enabled, role = Role.Tab,
                    interactionSource = interaction, indication = null,
                ) { onSelect(destination) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.size(45.dp, 27.dp).clip(CircleShape)
                        .background(if (current) Soft else Color.Transparent)
                        .indication(interaction, ripple(bounded = true, color = Accent)),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(destination.icon, color = if (current) Accent else Ink.copy(alpha = if (enabled) 1f else 0.45f))
                }
                Text(
                    stringResource(destination.title), color = Ink.copy(alpha = if (enabled) 1f else 0.45f),
                    fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun GalleryPage(
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GalleryTagChip(allLabel, !favoritesOnly && tag == null) { favoritesOnly = false; customTag = null }
            GalleryTagChip(favoritesLabel, favoritesOnly) { favoritesOnly = true; customTag = null }
            tags.forEach { name ->
                key(name) {
                    val label = if (name.equals(allLabel, ignoreCase = true) || name.equals(favoritesLabel, ignoreCase = true)) {
                        stringResource(R.string.elym_tag_with_prefix, name)
                    } else name
                    GalleryTagChip(label, name == tag) { favoritesOnly = false; customTag = name }
                }
            }
            GalleryTagChip(stringResource(R.string.elym_add_tag)) { addingTag = true }
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
private fun GalleryTagChip(label: String, selected: Boolean? = null, onClick: () -> Unit) {
    val chosen = selected == true
    val textSize = 13.sp
    val horizontalPadding = 13.dp
    val minimumLabelWidth = with(LocalDensity.current) { (textSize.roundToPx() * 2).toDp() }
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (chosen) Accent else SurfaceColor, tween(150, easing = FadeEasing), label = "Filter fill")
    val stroke by animateColorAsState(if (chosen) Accent else Line, tween(150, easing = FadeEasing), label = "Filter outline")
    val textColor by animateColorAsState(if (chosen) AccentInk else Ink, tween(150, easing = FadeEasing), label = "Filter text")
    val action = if (selected == null) {
        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
    } else {
        Modifier.selectable(selected, interaction, null, role = Role.Tab, onClick = onClick)
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
