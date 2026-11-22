package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elymbot.android.R
import com.elymbot.android.data.AppearanceMode
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    About(R.string.elym_about, R.drawable.elym_settings),
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
        get() {
            val sessionsDepth = if (mainId == null) 1 else 2
            return when (page) {
                Page.Chat -> if (sessionId == null) 1 else sessionsDepth + 1
                Page.Sessions -> sessionsDepth
                Page.About -> 1
                else -> 0
            }
        }
    val displayedChatId: String? get() = if (page == Page.Chat) sessionId ?: mainId else null
    val key: String get() = "${page.name}:$mainId:$ownerId:$sessionId"

    fun back(): Destination = when (page) {
        Page.Chat -> if (sessionId == null) Destination() else copy(page = Page.Sessions, sessionId = null, title = "")
        Page.Sessions -> if (mainId == null) Destination() else copy(page = Page.Chat, ownerId = null)
        Page.About -> Destination(Page.Settings)
        else -> Destination()
    }

    companion object {
        val Saver = listSaver<Destination, String>(
            save = { listOf(it.page.name, it.mainId.orEmpty(), it.ownerId.orEmpty(), it.sessionId.orEmpty(), it.title) },
            restore = { (page, main, owner, session, title) ->
                val restoredPage = Page.valueOf(page)
                // Earlier versions opened configuration over the current chat.
                val target = if (restoredPage == Page.Models && (main.isNotEmpty() || session.isNotEmpty())) Page.Chat else restoredPage
                Destination(target, main.ifEmpty { null }, owner.ifEmpty { null }, session.ifEmpty { null }, title)
            },
        )
    }
}

private val RootPages = listOf(Page.Gallery, Page.Models, Page.Platform, Page.Plugins, Page.Settings)
private val RootDestinations = RootPages.map { DockDestination(it.title, it.icon) }

@OptIn(ExperimentalLayoutApi::class)
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
    val main by service.mainSession.collectAsStateWithLifecycle(null)
    val config by service.models.collectAsStateWithLifecycle(ModelConfiguration())
    val commands by service.commandNames.collectAsStateWithLifecycle(emptyMap())
    val stateHolder = rememberSaveableStateHolder()
    var destination by rememberSaveable(stateSaver = Destination.Saver) { mutableStateOf(Destination()) }
    val focusManager = LocalFocusManager.current
    var personaForm by remember { mutableStateOf<PersonaCard?>(null) }
    var deletePersona by remember { mutableStateOf<PersonaCard?>(null) }
    val active = personas.firstOrNull { it.id == main?.personaId }
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
        val pageBackground = pageBackgroundBrush()
        val imeVisible = WindowInsets.isImeVisible
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
                    val offset = tween<IntOffset>(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement)
                    val fade = tween<Float>(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade)
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
                    transitionSpec = { tween(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement) },
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
                val footerClearance = if (screen.depth == 0 && !imeVisible) {
                    FloatingNavigationClearance
                } else 0.dp
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
                        Page.Gallery -> stateHolder.SaveableStateProvider(screen.key) { GalleryPage(
                            model, personas, personaTags, main?.personaId, main?.unread == true, current, footerClearance,
                            { if (current) personaForm = PersonaCard() },
                        ) { selected ->
                            val target = main
                            if (current && target != null) {
                                if (selected.id == target.personaId) navigate(Destination(Page.Chat, mainId = target.id))
                                else navigate(Destination(Page.Sessions, ownerId = selected.id))
                            }
                        } }
                        Page.Models -> stateHolder.SaveableStateProvider(screen.key) {
                            ConfigurationPage(model, config, current, footerClearance)
                        }
                        Page.Settings -> stateHolder.SaveableStateProvider(screen.key) {
                            SettingsPage(model, commands, current, appearanceMode, onAppearanceModeChange, footerClearance,
                                onAbout = { if (current) navigate(Destination(Page.About)) })
                        }
                        Page.About -> AboutPage(backFromScreen)
                        Page.Platform, Page.Plugins -> stateHolder.SaveableStateProvider(screen.key) {
                            PrimaryPage(screen.page.title, current, footerClearance) { query, clearance ->
                                Box(Modifier.padding(clearance)) {
                                    EmptyMessage(
                                        stringResource(if (!query.isNullOrEmpty()) R.string.elym_no_search_results
                                            else if (screen.page == Page.Platform) R.string.elym_no_platforms
                                            else R.string.elym_no_plugins),
                                        screen.page.icon,
                                    )
                                }
                            }
                        }
                        Page.Sessions -> if (persona != null) SessionsPage(
                            model, persona, backFromScreen, current, managePersona = screen.mainId == null,
                            onEditPersona = { if (current) personaForm = persona },
                            onDeletePersona = { if (current) deletePersona = persona },
                        ) { id, title ->
                            if (destination.key == screen.key) navigate(screen.copy(page = Page.Chat, sessionId = id, title = title))
                        }
                        Page.Chat -> screen.displayedChatId?.let { id ->
                            if (persona != null) stateHolder.SaveableStateProvider(id) {
                                ChatPage(
                                    model, persona, id, screen.title, screen.sessionId == null, personas, backFromScreen, current,
                                    onSessions = { ownerId -> if (current) navigate(screen.copy(page = Page.Sessions, ownerId = ownerId)) },
                                    onEdit = { if (current) personaForm = persona },
                                    onDelete = { if (current) deletePersona = persona },
                                    onRename = { if (destination.key == screen.key) destination = screen.copy(title = it) },
                                )
                            }
                        }
                    }
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = scrim.value }.background(Color.Black))
                }
            }
            var selectedRoot by remember {
                mutableStateOf(destination.page.takeIf { destination.depth == 0 } ?: when (destination.page) {
                    Page.About -> Page.Settings
                    else -> Page.Gallery
                })
            }
            SideEffect { if (destination.depth == 0) selectedRoot = destination.page }
            AnimatedVisibility(
                visible = destination.depth == 0 && !imeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement)) { it } +
                    fadeIn(tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade)),
                exit = slideOutVertically(tween(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement)) { it } +
                    fadeOut(tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade)),
                label = "Root navigation",
            ) {
                val selected = if (destination.depth == 0) destination.page else selectedRoot
                FloatingNavigationDock(RootDestinations, RootPages.indexOf(selected), onSelect = { index ->
                    if (destination.depth == 0) navigate(Destination(RootPages[index]))
                })
            }

        }
        personaForm?.let { existing ->
            PersonaForm(existing, personaTags, existing.id == main?.personaId, { personaForm = null }) { edited, makeGlobal ->
                model.perform {
                    service.savePersona(edited, makeGlobal, createMissingTags = false)
                    personaForm = null
                }
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
