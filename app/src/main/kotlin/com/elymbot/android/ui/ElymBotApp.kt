package com.elymbot.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.R
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
    Chat(R.string.elym_chat, R.drawable.elym_chat),
    Sessions(R.string.elym_sessions, R.drawable.elym_chat),
}

@Composable
fun ElymBotApp(service: ElymBotUiService) {
    val model: ElymBotViewModel = viewModel { ElymBotViewModel(service) }
    val personas by service.personas.collectAsStateWithLifecycle(emptyList())
    val activeId by service.activePersonaId.collectAsStateWithLifecycle(null)
    val main by service.mainSession.collectAsStateWithLifecycle(null)
    val config by service.models.collectAsStateWithLifecycle(ModelConfiguration())
    val commands by service.commandNames.collectAsStateWithLifecycle(emptyMap())
    val stateHolder = rememberSaveableStateHolder()
    var page by rememberSaveable { mutableStateOf(Page.Gallery) }
    var owner by rememberSaveable { mutableStateOf<String?>(null) }
    var chatId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatTitle by rememberSaveable { mutableStateOf("") }
    var mainChat by rememberSaveable { mutableStateOf(false) }
    var modelsFromChat by rememberSaveable { mutableStateOf(false) }
    var personaForm by remember { mutableStateOf<PersonaCard?>(null) }
    var deletePersona by remember { mutableStateOf<PersonaCard?>(null) }
    val active = personas.firstOrNull { it.id == activeId }
    val persona = if (page == Page.Chat && mainChat) active else personas.firstOrNull { it.id == owner }
    val openSessions = { id: String -> owner = id; page = Page.Sessions }
    val back = {
        page = when (page) {
            Page.Chat -> if (mainChat) Page.Gallery else Page.Sessions
            Page.Sessions -> {
                chatId = main?.id
                mainChat = true
                if (chatId == null) Page.Gallery else Page.Chat
            }
            Page.Settings -> Page.Gallery
            Page.Models -> if (modelsFromChat) Page.Chat else Page.Gallery
            else -> Page.Gallery
        }
    }
    BackHandler(page != Page.Gallery, back)
    val visibleChatId = chatId.takeIf { page == Page.Chat }
    LifecycleStartEffect(visibleChatId) {
        model.perform { service.setViewedConversation(visibleChatId) }
        onStopOrDispose { model.perform { service.setViewedConversation(null) } }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, onPrimary = AccentInk, surface = SurfaceColor, onSurface = Ink, background = Paper, error = Danger)) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(PaperTop, Paper, Paper), endY = 1200f))
                .safeDrawingPadding().imePadding(),
        ) {
            when (page) {
                Page.Gallery -> stateHolder.SaveableStateProvider(Page.Gallery.name) { GalleryPage(
                    personas, activeId, main?.unread == true, { personaForm = PersonaCard() },
                ) { selected ->
                    val target = main
                    if (target != null) model.perform {
                        if (!target.running && selected.id != activeId) service.activatePersona(selected.id)
                        chatId = target.id
                        chatTitle = ""
                        mainChat = true
                        page = Page.Chat
                    }
                }
                }
                Page.Models -> ModelsPage(model, config, if (modelsFromChat) back else null)
                Page.Settings -> CommandsPage(model, commands)
                Page.Sessions -> if (persona != null) SessionsPage(model, persona, back) { id, title ->
                    chatId = id
                    chatTitle = title
                    mainChat = false
                    page = Page.Chat
                }
                Page.Chat -> if (persona != null && chatId != null) {
                    stateHolder.SaveableStateProvider(chatId.orEmpty()) {
                        ChatPage(
                            model, persona, chatId.orEmpty(), chatTitle, mainChat, personas, commands, back,
                            onSessions = openSessions,
                            onEdit = { personaForm = persona },
                            onDelete = { deletePersona = persona },
                            onModelSettings = { modelsFromChat = true; page = Page.Models },
                            onRename = { chatTitle = it },
                        )
                    }
                }
                else -> Unit
            }
            if (page == Page.Gallery || page == Page.Settings || (page == Page.Models && !modelsFromChat)) {
                Navigation(page, Modifier.align(Alignment.BottomCenter)) { destination ->
                    modelsFromChat = false
                    page = destination
                }
            }
        }
        personaForm?.let { existing ->
            PersonaForm(existing, { personaForm = null }) { edited ->
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
                    if (hasSessions) Action(stringResource(R.string.elym_view_sessions), { dismiss(); openSessions(target.id) }, primary = true)
                    else Action(stringResource(R.string.elym_delete_persona), {
                        model.perform { service.deletePersona(target.id); dismiss(); page = Page.Gallery }
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
            .border(1.dp, Color(0xFFFFFDF8), shape).padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        listOf(Page.Gallery, Page.Models, Page.Platform, Page.Plugins, Page.Settings).forEach { destination ->
            val enabled = destination != Page.Platform && destination != Page.Plugins
            val current = selected == destination
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(19.dp))
                    .clickable(enabled = enabled) { onSelect(destination) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.size(45.dp, 27.dp).background(if (current) Soft else Color.Transparent, CircleShape),
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
    personas: List<PersonaCard>,
    activeId: String?,
    unread: Boolean,
    onAdd: () -> Unit,
    onOpen: (PersonaCard) -> Unit,
) {
    val filters = listOf(R.string.elym_all, R.string.elym_favorites, R.string.elym_company, R.string.elym_learning, R.string.elym_creative)
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val tag = stringResource(when (filter) {
        2 -> R.string.elym_default_tag_company
        3 -> R.string.elym_default_tag_learning
        4 -> R.string.elym_default_tag_creative
        else -> R.string.elym_all
    })
    val visible = personas.filter {
        val filtered = when (filter) {
            0 -> true
            1 -> it.favorite
            else -> tag in it.tags
        }
        filtered && (listOf(it.name, it.description, it.prompt) + it.tags).any { value -> value.contains(query.trim(), ignoreCase = true) }
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
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEachIndexed { index, label ->
                val chosen = index == filter
                Box(Modifier.weight(1f).height(48.dp).clickable { filter = index }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(32.dp).background(if (chosen) Accent else SurfaceColor, CircleShape)
                            .border(1.dp, if (chosen) Accent else Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(label), color = if (chosen) AccentInk else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            EmptyMessage(stringResource(R.string.elym_no_personas), R.drawable.elym_search) {
                Action(stringResource(R.string.elym_clear_filter), { filter = 0; query = "" })
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
