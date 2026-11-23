package com.elymbot.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elymbot.android.R
import kotlinx.coroutines.flow.last

private enum class FaqCategory(val title: Int) {
    All(R.string.elym_faq_all), GettingStarted(R.string.elym_faq_getting_started),
    Models(R.string.elym_faq_models), Plugins(R.string.elym_plugins),
}

internal enum class FaqAction(val category: Int, val title: Int, val answer: Int) {
    Setup(R.string.elym_faq_getting_started, R.string.elym_faq_setup_question, R.string.elym_faq_setup_answer),
    Models(R.string.elym_faq_models, R.string.elym_faq_models_question, R.string.elym_faq_models_answer),
    Plugins(R.string.elym_plugins, R.string.elym_faq_plugins_question, R.string.elym_faq_plugins_answer),
    Qq(R.string.elym_plugins, R.string.elym_faq_qq_question, R.string.elym_faq_qq_answer),
    Tasks(R.string.elym_plugins, R.string.elym_faq_tasks_question, R.string.elym_faq_tasks_answer),
}

@Composable
internal fun FaqPage(
    back: () -> Unit,
    actions: Map<FaqAction, Int>,
    onAction: (FaqAction) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(FaqCategory.All) }
    var open by rememberSaveable { mutableStateOf<FaqAction?>(null) }
    val scroll = rememberScrollState()
    val search = stringResource(R.string.elym_faq_search)
    val categories = FaqCategory.entries.filter { item ->
        item == FaqCategory.All || actions.keys.any { it.category == item.title }
    }
    val category = selectedCategory.takeIf { it in categories } ?: FaqCategory.All
    val visible = actions.keys.filter { item ->
        (category == FaqCategory.All || category.title == item.category) &&
            query.matchesPageSearch(stringResource(item.title), stringResource(item.answer), stringResource(item.category))
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        HelpHeader(R.string.elym_faq, back)
        Column(Modifier.weight(1f).fillMaxWidth().then(LocalPageBodyMotion.current)) {
            Input(query, { query = it; open = null }, search, Modifier.fillMaxWidth().padding(horizontal = 18.dp))
            TagStrip(rememberScrollState()) {
                categories.forEach { item ->
                    TagChip(stringResource(item.title), item == category) { selectedCategory = item; open = null }
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
                if (visible.isEmpty()) {
                    EmptyMessage(stringResource(R.string.elym_no_search_results), R.drawable.elym_search) {
                        Action(stringResource(R.string.elym_faq_clear), {
                            selectedCategory = FaqCategory.All
                            query = ""
                            open = null
                        })
                    }
                } else Group {
                    for ((index, item) in visible.withIndex()) {
                        if (index > 0) HorizontalDivider(color = Line)
                        FaqEntry(item, item == open, { open = if (item == open) null else item }, actions.getValue(item)) {
                            onAction(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppSetupBanner(onOpen: () -> Unit, onSkip: () -> Unit) {
    Group {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.elym_sliders, Modifier.size(22.dp), Accent)
                Text(
                    stringResource(R.string.elym_guide_configure_model), Modifier.weight(1f).padding(start = 9.dp),
                    color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
                RoundAction(R.drawable.elym_close, stringResource(R.string.elym_guide_later), onSkip)
            }
            Action(stringResource(R.string.elym_guide_open_setup), onOpen, primary = true)
        }
    }
}

@Composable
internal fun AppSetupPage(
    model: ElymBotViewModel,
    config: ModelConfiguration,
    personas: List<PersonaCard>,
    toolStatus: ProbeStatus,
    hasMainConversation: Boolean,
    current: Boolean,
    back: () -> Unit,
    onCreatePersona: () -> Unit,
    onStartChat: () -> Unit,
    onSkip: () -> Unit,
) {
    val serviceReady = config.services.isNotEmpty()
    val modelReady = toolStatus == ProbeStatus.Supported
    val choice = config.text
    val selectedService = config.services.firstOrNull { it.id == choice?.serviceId }
    val steps = listOf(
        R.string.elym_add_service to serviceReady,
        R.string.elym_guide_choose_model to modelReady,
        R.string.elym_guide_start_chat to false,
    )
    val currentStep = steps.indexOfFirst { !it.second }
    var addingService by remember { mutableStateOf(false) }
    var choosingModel by remember { mutableStateOf(false) }
    var choosingPersona by remember { mutableStateOf(false) }
    var verifying by remember(choice) { mutableStateOf(false) }
    var verified by remember(choice) { mutableStateOf(false) }
    var verificationIssue by remember(choice) { mutableStateOf<UiIssue?>(null) }
    fun verify() {
        if (!current || verifying || selectedService == null || choice == null) return
        verifying = true
        verificationIssue = null
        model.perform {
            try {
                val draft = ModelServiceDraft(
                    id = selectedService.id, provider = selectedService.provider, name = selectedService.name,
                    baseUrl = selectedService.baseUrl, modelId = choice.modelId, catalog = selectedService.catalog,
                )
                val result = model.service.probeModel(draft).last()
                verified = true
                verificationIssue = result.failuresByTarget[ProbeTarget.StructuredToolCalls]?.issue
                    ?: result.issue.takeIf { result.structuredToolCalls == ProbeStatus.Unknown }
            } finally {
                verifying = false
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        HelpHeader(R.string.elym_guide_title, back)
        Column(
            Modifier.fillMaxWidth().then(LocalPageBodyMotion.current).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.mipmap.icon), contentDescription = null, modifier = Modifier.size(64.dp))
                Text(stringResource(R.string.app_name), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Group {
                steps.forEachIndexed { index, (label, complete) ->
                    if (index > 0) HorizontalDivider(color = Line)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 47.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (complete) {
                            Glyph(R.drawable.elym_check, Modifier.size(20.dp).semanticsLabel(stringResource(R.string.elym_guide_complete)), Accent)
                        } else Box(
                            Modifier.size(20.dp).background(if (index == currentStep) Accent else Soft, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.elym_guide_step_number, index + 1),
                                color = if (index == currentStep) AccentInk else Muted, fontSize = 12.sp,
                            )
                        }
                        Text(stringResource(label), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Group {
                when {
                    !serviceReady -> ListRow(stringResource(R.string.elym_add_service), R.drawable.elym_sliders, { addingService = true })
                    !modelReady -> {
                        ListRow(stringResource(R.string.elym_guide_choose_model), R.drawable.elym_sliders,
                            { choosingModel = true }, subtitle = choice?.modelId)
                        HorizontalDivider(color = Line)
                        ListRow(stringResource(R.string.elym_add_service), R.drawable.elym_plus, { addingService = true })
                    }
                    !hasMainConversation -> ListRow(
                        stringResource(if (personas.isEmpty()) R.string.elym_create_persona else R.string.elym_guide_activate_persona),
                        R.drawable.elym_person,
                        { if (personas.isEmpty()) onCreatePersona() else choosingPersona = true },
                    )
                    else -> ListRow(stringResource(R.string.elym_text_role), R.drawable.elym_chat,
                        { choosingModel = true }, subtitle = choice?.modelId)
                }
            }
            if (selectedService != null && !modelReady) {
                val issue = verificationIssue
                val failure = when {
                    issue != null -> issueLabel(issue)
                    toolStatus == ProbeStatus.Unsupported -> R.string.elym_guide_tools_unsupported
                    verified && !verifying && toolStatus == ProbeStatus.Unknown -> R.string.elym_guide_tools_unknown
                    else -> null
                }
                if (failure != null) Text(stringResource(failure), color = Danger, fontSize = 14.sp)
                Action(
                    stringResource(if (verifying) R.string.elym_probing else R.string.elym_guide_verify_model),
                    ::verify, primary = true, enabled = !verifying,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (modelReady && hasMainConversation) {
                    Action(stringResource(R.string.elym_guide_start_chat), onStartChat, primary = true)
                }
                Action(stringResource(R.string.elym_guide_later), onSkip)
            }
        }
    }
    if (current && addingService) ModelEditor(model, ModelServiceDraft(), hasKey = false) { addingService = false }
    if (current && choosingModel) ModelRolePicker(model, config, ModelRole.Text) { choosingModel = false }
    if (current && choosingPersona) Sheet(stringResource(R.string.elym_guide_activate_persona), { choosingPersona = false }) {
        Group {
            personas.forEachIndexed { index, persona ->
                if (index > 0) HorizontalDivider(color = Line)
                ListRow(persona.name, R.drawable.elym_person, {
                    model.perform {
                        model.service.activatePersona(persona.id)
                        choosingPersona = false
                    }
                })
            }
        }
        Action(stringResource(R.string.elym_create_persona), { choosingPersona = false; onCreatePersona() })
    }
}

@Composable
private fun FaqEntry(item: FaqAction, open: Boolean, toggle: () -> Unit, label: Int, action: () -> Unit) {
    val rotation by animateFloatAsState(
        if (open) 90f else 0f, tween(180, easing = PrimaryMotion.Movement), label = "FAQ chevron",
    )
    val state = stringResource(if (open) R.string.elym_faq_expanded else R.string.elym_faq_collapsed)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 57.dp).clickable(role = Role.Button, onClick = toggle)
            .semantics { stateDescription = state }.padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(item.title), Modifier.weight(1f), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Glyph(R.drawable.elym_chevron, Modifier.size(17.dp).graphicsLayer { rotationZ = rotation }, color = Muted)
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Movement)) +
            fadeIn(tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade)),
        exit = shrinkVertically(tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Movement)) +
            fadeOut(tween(PrimaryMotion.FadeMillis, easing = PrimaryMotion.Fade)),
        label = "FAQ answer",
    ) {
        Column(
            Modifier.fillMaxWidth().then(if (open) Modifier else Modifier.semantics { hideFromAccessibility() })
                .padding(start = 13.dp, end = 13.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(item.answer), color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            Action(stringResource(label), { if (open) action() }, enabled = open)
        }
    }
}

@Composable
private fun HelpHeader(title: Int, back: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 5.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction(R.drawable.elym_back, stringResource(R.string.elym_back), back)
        Text(stringResource(title), Modifier.semantics { heading() }, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
