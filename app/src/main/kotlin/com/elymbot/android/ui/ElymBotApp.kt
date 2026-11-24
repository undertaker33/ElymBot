package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elymbot.android.R
import com.elymbot.android.data.AppearanceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
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
    Qq(R.string.elym_plugin_qq, R.drawable.elym_platform),
    Plugins(R.string.elym_plugins, R.drawable.elym_plugin),
    Settings(R.string.elym_settings, R.drawable.elym_settings),
    About(R.string.elym_about, R.drawable.elym_settings),
    Faq(R.string.elym_faq, R.drawable.elym_info),
    AppSetup(R.string.elym_guide_title, R.drawable.elym_sliders),
    ScheduledTasks(R.string.elym_scheduled_tasks, R.drawable.elym_clock),
    Chat(R.string.elym_chat, R.drawable.elym_chat),
    Sessions(R.string.elym_sessions, R.drawable.elym_chat),
}

private data class Destination(
    val page: Page = Page.Gallery,
    val mainId: String? = null,
    val ownerId: String? = null,
    val sessionId: String? = null,
    val title: String = "",
    val entryOffset: Dp = 0.dp,
    val backPages: List<Page> = emptyList(),
) {
    val depth: Int
        get() {
            val sessionsDepth = if (mainId == null) 1 else 2
            return when (page) {
                Page.Chat -> if (sessionId == null) 1 else sessionsDepth + 1
                Page.Sessions -> sessionsDepth
                Page.About, Page.Faq -> 1
                Page.Qq -> 1 + backPages.size
                Page.AppSetup -> 1 + backPages.size
                Page.ScheduledTasks -> 2
                else -> 0
            }
        }
    val displayedChatId: String? get() = if (page == Page.Chat) sessionId ?: mainId else null
    val key: String get() = "${page.name}:$mainId:$ownerId:$sessionId" +
        if (backPages.isEmpty()) "" else backPages.joinToString(prefix = ":")

    fun back(): Destination = backPages.lastOrNull()?.let { Destination(it, backPages = backPages.dropLast(1)) } ?: when (page) {
        Page.Chat -> if (sessionId == null) Destination() else copy(page = Page.Sessions, sessionId = null, title = "")
        Page.Sessions -> if (mainId == null) Destination() else copy(page = Page.Chat, ownerId = null)
        Page.About, Page.Faq -> Destination(Page.Settings)
        Page.Qq -> Destination(Page.Platform)
        Page.ScheduledTasks -> Destination(Page.Chat, mainId = mainId)
        else -> Destination()
    }

    companion object {
        val Saver = listSaver<Destination, String>(
            save = { listOf(it.page.name, it.mainId.orEmpty(), it.ownerId.orEmpty(), it.sessionId.orEmpty(), it.title) + it.backPages.map(Page::name) },
            restore = { saved ->
                val (page, main, owner, session, title) = saved
                val restoredPage = Page.valueOf(page)
                // Earlier versions opened configuration over the current chat.
                val target = if (restoredPage == Page.Models && (main.isNotEmpty() || session.isNotEmpty())) Page.Chat else restoredPage
                Destination(target, main.ifEmpty { null }, owner.ifEmpty { null }, session.ifEmpty { null }, title,
                    backPages = saved.drop(5).map(Page::valueOf))
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
    val mainId = main?.id
    val tasksAvailability = remember(service, mainId) {
        mainId?.let(service::scheduledTasksAvailable) ?: flowOf(false)
    }
    val tasksAvailable by tasksAvailability.collectAsStateWithLifecycle(false)
    val appUpdate by service.appUpdate.collectAsStateWithLifecycle(AppUpdateState())
    val pluginManagement by service.pluginManagement.collectAsStateWithLifecycle(initialValue = null)
    val platformState by key(service, mainId) {
        val updates = remember { mainId?.let(service::platformConnections) ?: flowOf<PlatformConnectionsState?>(null) }
        updates.collectAsStateWithLifecycle(initialValue = null)
    }
    val qqConnection = platformState?.takeIf { it.authorized }?.connections
        ?.firstOrNull { it.pluginId == QqPluginId && it.availability != PlatformAvailability.NotInstalled }
    var dismissedUpdateId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedUpdateId by rememberSaveable { mutableStateOf<String?>(null) }
    val availableUpdate = appUpdate.availableUpdate?.takeIf { appUpdate.status == AppUpdateUiStatus.UpdateAvailable }
    val visibleUpdate = availableUpdate?.takeIf { it.id != dismissedUpdateId || it.id == requestedUpdateId }
    val config by service.models.collectAsStateWithLifecycle(ModelConfiguration())
    val appGuide by service.appGuide.collectAsStateWithLifecycle(AppGuideState(AppGuideStatus.Unknown, ProbeStatus.Unknown))
    val commands by service.commandNames.collectAsStateWithLifecycle(emptyMap())
    val stateHolder = rememberSaveableStateHolder()
    var destination by rememberSaveable(stateSaver = Destination.Saver) { mutableStateOf(Destination()) }
    var dockConfigurationCategory by rememberSaveable { mutableStateOf(ConfigurationCategory.Models) }
    var faqConfigurationCategory by rememberSaveable { mutableStateOf(ConfigurationCategory.Models) }
    var dockPluginTab by rememberSaveable { mutableStateOf(PluginTab.Discover) }
    var faqPluginTab by rememberSaveable { mutableStateOf(PluginTab.Installed) }
    val focusManager = LocalFocusManager.current
    var personaForm by remember { mutableStateOf<PersonaCard?>(null) }
    var deletePersona by remember { mutableStateOf<PersonaCard?>(null) }
    val active = personas.firstOrNull { it.id == main?.personaId }
    fun navigate(next: Destination, returning: Boolean = false) {
        if (destination.key != next.key) focusManager.clearFocus(force = true)
        val offset = when {
            returning || destination.depth > next.depth -> (-13).dp
            next.depth == 0 -> 6.dp
            else -> 17.dp
        }
        destination = next.copy(entryOffset = offset)
    }
    val back = { navigate(destination.back(), returning = true) }
    BackHandler(destination.page != Page.Gallery || destination.backPages.isNotEmpty(), back)
    val visibleChatId = destination.displayedChatId
    LifecycleStartEffect(visibleChatId) {
        model.perform { service.setViewedConversation(visibleChatId) }
        onStopOrDispose { model.perform { service.setViewedConversation(null) } }
    }

    ElymBotTheme(darkTheme) {
        val pageBackground = pageBackgroundBrush()
        val imeVisible = WindowInsets.isImeVisible
        val density = LocalDensity.current
        Box(
            Modifier.fillMaxSize()
                .background(pageBackground)
                .safeDrawingPadding(),
        ) {
            AnimatedContent(
                targetState = destination,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentKey = { it.key },
                label = "Page navigation",
                transitionSpec = {
                    (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished).using(null)
                },
            ) { screen ->
                val current = screen.key == destination.key
                val offset = tween<IntOffset>(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement)
                val fade = tween<Float>(PrimaryMotion.FullMillis, easing = PrimaryMotion.Movement)
                val bodyMotion = Modifier.animateEnterExit(
                    enter = slideInHorizontally(offset) { with(density) { screen.entryOffset.roundToPx() } } +
                        fadeIn(fade, initialAlpha = 0.35f),
                    exit = ExitTransition.None,
                    label = "Page content entry",
                )
                val backFromScreen = { if (destination.key == screen.key) back() }
                val persona = if (screen.sessionId == null && screen.page == Page.Chat) active
                    else personas.firstOrNull { it.id == screen.ownerId }
                val footerClearance = if (screen.depth == 0 && !imeVisible) {
                    FloatingNavigationClearance
                } else 0.dp
                Box(
                    Modifier.fillMaxSize().layout { measurable, constraints ->
                        val page = measurable.measure(constraints)
                        layout(page.width, page.height) {
                            // Let the previous page clear its transient state without placing its UI.
                            if (current) page.place(0, 0)
                        }
                    },
                ) {
                    CompositionLocalProvider(LocalPageBodyMotion provides bodyMotion) {
                        when (screen.page) {
                            Page.Gallery -> stateHolder.SaveableStateProvider(screen.key) { GalleryPage(
                                model, personas, personaTags, main?.personaId, main?.unread == true, current, footerClearance,
                                { if (current) personaForm = PersonaCard() },
                                setupGuide = if (appGuide.status == AppGuideStatus.NeedsSetup) {
                                    {
                                        AppSetupBanner(
                                            onOpen = { if (current) navigate(Destination(Page.AppSetup)) },
                                            onSkip = { if (current) model.perform { service.skipAppGuide() } },
                                        )
                                    }
                                } else null,
                            ) { selected ->
                                val target = main
                                if (current && target != null) {
                                    if (selected.id == target.personaId) navigate(Destination(Page.Chat, mainId = target.id))
                                    else navigate(Destination(Page.Sessions, ownerId = selected.id))
                                }
                            } }
                            Page.Models -> stateHolder.SaveableStateProvider(screen.key) {
                                val fromFaq = screen.backPages.lastOrNull() == Page.Faq
                                ConfigurationPage(model, config, current, footerClearance,
                                    category = if (fromFaq) faqConfigurationCategory else dockConfigurationCategory,
                                    onCategoryChange = { category ->
                                        if (current) {
                                            if (fromFaq) faqConfigurationCategory = category else dockConfigurationCategory = category
                                        }
                                    },
                                    onBack = backFromScreen.takeIf { screen.backPages.isNotEmpty() })
                            }
                            Page.Settings -> stateHolder.SaveableStateProvider(screen.key) {
                                SettingsPage(model, commands, current, appearanceMode, onAppearanceModeChange, footerClearance,
                                    onAbout = { if (current) navigate(Destination(Page.About)) },
                                    onFaq = { if (current) navigate(Destination(Page.Faq)) })
                            }
                            Page.About -> AboutPage(
                                backFromScreen, appUpdate,
                                onCheckForUpdate = { if (current) model.perform { service.checkForAppUpdate(forceRefresh = true) } },
                                onShowUpdate = { if (current) requestedUpdateId = availableUpdate?.id },
                            )
                            Page.Faq -> stateHolder.SaveableStateProvider(screen.key) {
                                FaqPage(
                                    backFromScreen,
                                    actions = buildMap {
                                        put(FaqAction.Setup, R.string.elym_faq_open_setup)
                                        put(FaqAction.Models, R.string.elym_faq_open_models)
                                        if (pluginManagement != null) put(FaqAction.Plugins, R.string.elym_faq_open_plugins)
                                        if (qqConnection != null) put(FaqAction.Qq, R.string.elym_faq_open_qq)
                                        if (mainId != null && tasksAvailable) put(FaqAction.Tasks, R.string.elym_faq_open_tasks)
                                    },
                                ) { action ->
                                    if (current) {
                                        val faqBack = listOf(Page.Faq)
                                        val page = when (action) {
                                            FaqAction.Setup -> Destination(Page.AppSetup, backPages = faqBack)
                                            FaqAction.Models -> {
                                                faqConfigurationCategory = ConfigurationCategory.Models
                                                Destination(Page.Models, backPages = faqBack)
                                            }
                                            FaqAction.Tasks -> mainId?.takeIf { tasksAvailable }?.let {
                                                Destination(Page.ScheduledTasks, mainId = it, backPages = faqBack)
                                            }
                                            FaqAction.Plugins -> pluginManagement?.let {
                                                faqPluginTab = PluginTab.Installed
                                                Destination(Page.Plugins, backPages = faqBack)
                                            }
                                            FaqAction.Qq -> mainId?.takeIf { qqConnection != null }?.let {
                                                Destination(Page.Qq, mainId = it, backPages = faqBack)
                                            }
                                            else -> null
                                        }
                                        if (page != null) navigate(page)
                                    }
                                }
                            }
                            Page.AppSetup -> AppSetupPage(
                                model, config, personas, appGuide.structuredToolCalls, main != null && active != null,
                                current, backFromScreen,
                                onCreatePersona = { if (current) personaForm = PersonaCard() },
                                onStartChat = {
                                    val id = main?.id
                                    if (current && id != null && appGuide.status == AppGuideStatus.Ready) {
                                        navigate(Destination(Page.Chat, mainId = id))
                                    }
                                },
                                onSkip = {
                                    if (current) model.perform {
                                        service.skipAppGuide()
                                        backFromScreen()
                                    }
                                },
                            )
                            Page.Plugins -> stateHolder.SaveableStateProvider(screen.key) {
                                val fromFaq = screen.backPages.lastOrNull() == Page.Faq
                                PluginsPage(model, pluginManagement, current, footerClearance,
                                    tab = if (fromFaq) faqPluginTab else dockPluginTab,
                                    onTabChange = { tab ->
                                        if (current) {
                                            if (fromFaq) faqPluginTab = tab else dockPluginTab = tab
                                        }
                                    },
                                    onBack = backFromScreen.takeIf { fromFaq })
                            }
                            Page.Platform -> stateHolder.SaveableStateProvider(screen.key) {
                                PlatformPage(
                                    platformState, current, footerClearance,
                                    onQq = { if (current && mainId != null && qqConnection != null) navigate(Destination(Page.Qq, mainId = mainId)) },
                                    onPlugins = if (pluginManagement != null) ({ if (current) navigate(Destination(Page.Plugins)) }) else null,
                                )
                            }
                            Page.Qq -> QqPlatformPage(
                                model, mainId?.takeIf { it == screen.mainId }, qqConnection?.takeIf { mainId == screen.mainId },
                                current, backFromScreen,
                                onPlugins = if (pluginManagement != null) ({
                                    if (current) {
                                        dockPluginTab = PluginTab.Installed
                                        navigate(Destination(Page.Plugins))
                                    }
                                }) else null,
                            )
                            Page.ScheduledTasks -> screen.mainId?.let { id ->
                                ScheduledTasksPage(model, id, active?.name.orEmpty(), current, id == mainId && tasksAvailable, backFromScreen)
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
                                        onOpenTasks = if (screen.sessionId == null && id == mainId && tasksAvailable) {
                                            { if (current) navigate(Destination(Page.ScheduledTasks, mainId = id)) }
                                        } else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            var selectedRoot by remember {
                mutableStateOf(destination.page.takeIf { destination.depth == 0 } ?: when (destination.page) {
                    Page.About, Page.Faq -> Page.Settings
                    Page.Qq -> if (destination.backPages.isEmpty()) Page.Platform else Page.Settings
                    Page.AppSetup -> destination.backPages.lastOrNull()?.let { Page.Settings } ?: Page.Gallery
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
        AppUpdatePrompt(model, visibleUpdate) {
            dismissedUpdateId = visibleUpdate?.id
            requestedUpdateId = null
        }
    }
}
