package com.astrbot.android.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.ui.viewmodel.BotViewModel
import com.astrbot.android.ui.viewmodel.BridgeViewModel
import com.astrbot.android.ui.viewmodel.ConversationViewModel
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private data class BotChoice(
    val id: String,
    val label: String,
    val sublabel: String,
)

@Composable
fun BotScreen(
    botViewModel: BotViewModel = viewModel(),
    bridgeViewModel: BridgeViewModel = viewModel(),
    conversationViewModel: ConversationViewModel = viewModel(),
    qqLoginViewModel: QQLoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val botProfiles by botViewModel.botProfiles.collectAsState()
    val botProfile by botViewModel.botProfile.collectAsState()
    val providers by botViewModel.providers.collectAsState()
    val personas by botViewModel.personas.collectAsState()
    val runtimeState by bridgeViewModel.runtimeState.collectAsState()
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val sessions by conversationViewModel.sessions.collectAsState()

    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
    val enabledPersonas = personas.filter { it.enabled }

    var displayName by remember(botProfile.id, botProfile.displayName) { mutableStateOf(botProfile.displayName) }
    var accountHint by remember(botProfile.id, botProfile.accountHint) { mutableStateOf(botProfile.accountHint) }
    var bridgeEndpoint by remember(botProfile.id, botProfile.bridgeEndpoint) { mutableStateOf(botProfile.bridgeEndpoint) }
    var triggerWords by remember(botProfile.id, botProfile.triggerWords) {
        mutableStateOf(botProfile.triggerWords.joinToString(","))
    }
    var autoReplyEnabled by remember(botProfile.id, botProfile.autoReplyEnabled) {
        mutableStateOf(botProfile.autoReplyEnabled)
    }
    var defaultProviderId by remember(botProfile.id, botProfile.defaultProviderId) {
        mutableStateOf(botProfile.defaultProviderId)
    }
    var defaultPersonaId by remember(botProfile.id, botProfile.defaultPersonaId) {
        mutableStateOf(botProfile.defaultPersonaId)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val wideLayout = maxWidth >= 960.dp

        if (wideLayout) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BotListPanel(
                    bots = botProfiles,
                    selectedBotId = botProfile.id,
                    onSelectBot = { botViewModel.select(it) },
                    onCreateBot = { botViewModel.create() },
                    onDeleteSelected = { botViewModel.deleteSelected() },
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        BotEditorCard(
                            botProfile = botProfile,
                            displayName = displayName,
                            onDisplayNameChange = { displayName = it },
                            accountHint = accountHint,
                            onAccountHintChange = { accountHint = it },
                            bridgeEndpoint = bridgeEndpoint,
                            onBridgeEndpointChange = { bridgeEndpoint = it },
                            triggerWords = triggerWords,
                            onTriggerWordsChange = { triggerWords = it },
                            autoReplyEnabled = autoReplyEnabled,
                            onAutoReplyChange = { autoReplyEnabled = it },
                            providerOptions = chatProviders.map { BotChoice(it.id, it.name, it.model) },
                            selectedProviderId = defaultProviderId,
                            onSelectProvider = { defaultProviderId = it },
                            personaOptions = enabledPersonas.map {
                                BotChoice(it.id, it.name, "Context ${it.maxContextMessages} turns")
                            },
                            selectedPersonaId = defaultPersonaId,
                            onSelectPersona = { defaultPersonaId = it },
                            onSave = {
                                botViewModel.save(
                                    botProfile.toEditableProfile(
                                        displayName = displayName,
                                        accountHint = accountHint,
                                        bridgeEndpoint = bridgeEndpoint,
                                        triggerWords = triggerWords,
                                        autoReplyEnabled = autoReplyEnabled,
                                        defaultProviderId = defaultProviderId,
                                        defaultPersonaId = defaultPersonaId,
                                    ),
                                )
                            },
                        )
                    }
                    item {
                        QQLoginCard(
                            loginState = loginState,
                            onRefresh = { qqLoginViewModel.refreshNow() },
                            onRefreshQr = { qqLoginViewModel.refreshQrCode() },
                            onQuickLogin = { qqLoginViewModel.quickLoginSavedAccount() },
                            onSaveQuickLogin = { qqLoginViewModel.saveQuickLoginAccount(it) },
                            onPasswordLogin = { uin, password -> qqLoginViewModel.passwordLogin(uin, password) },
                            onCaptchaLogin = { uin, password, ticket, randstr, sid ->
                                qqLoginViewModel.captchaLogin(uin, password, ticket, randstr, sid)
                            },
                            onNewDeviceLogin = { uin, password -> qqLoginViewModel.newDeviceLogin(uin, password) },
                        )
                    }
                    item {
                        RuntimeCard(
                            platformName = botProfile.platformName,
                            bridgeMode = botProfile.bridgeMode,
                            status = runtimeState.status,
                            details = runtimeState.details,
                            progressLabel = runtimeState.progressLabel,
                            progressPercent = runtimeState.progressPercent,
                            progressIndeterminate = runtimeState.progressIndeterminate,
                            installerCached = runtimeState.installerCached,
                            onStart = { ContainerBridgeController.start(context) },
                            onCheck = { ContainerBridgeController.check(context) },
                            onStop = { ContainerBridgeController.stop(context) },
                        )
                    }
                    item {
                        ContextPreviewCard(
                            sessionTitle = sessions.firstOrNull()?.title.orEmpty(),
                            preview = sessions.firstOrNull()?.let { conversationViewModel.contextPreview(it.id) }.orEmpty(),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    BotListPanel(
                        bots = botProfiles,
                        selectedBotId = botProfile.id,
                        onSelectBot = { botViewModel.select(it) },
                        onCreateBot = { botViewModel.create() },
                        onDeleteSelected = { botViewModel.deleteSelected() },
                    )
                }
                item {
                    BotEditorCard(
                        botProfile = botProfile,
                        displayName = displayName,
                        onDisplayNameChange = { displayName = it },
                        accountHint = accountHint,
                        onAccountHintChange = { accountHint = it },
                        bridgeEndpoint = bridgeEndpoint,
                        onBridgeEndpointChange = { bridgeEndpoint = it },
                        triggerWords = triggerWords,
                        onTriggerWordsChange = { triggerWords = it },
                        autoReplyEnabled = autoReplyEnabled,
                        onAutoReplyChange = { autoReplyEnabled = it },
                        providerOptions = chatProviders.map { BotChoice(it.id, it.name, it.model) },
                        selectedProviderId = defaultProviderId,
                        onSelectProvider = { defaultProviderId = it },
                        personaOptions = enabledPersonas.map {
                            BotChoice(it.id, it.name, "Context ${it.maxContextMessages} turns")
                        },
                        selectedPersonaId = defaultPersonaId,
                        onSelectPersona = { defaultPersonaId = it },
                        onSave = {
                            botViewModel.save(
                                botProfile.toEditableProfile(
                                    displayName = displayName,
                                    accountHint = accountHint,
                                    bridgeEndpoint = bridgeEndpoint,
                                    triggerWords = triggerWords,
                                    autoReplyEnabled = autoReplyEnabled,
                                    defaultProviderId = defaultProviderId,
                                    defaultPersonaId = defaultPersonaId,
                                ),
                            )
                        },
                    )
                }
                item {
                    QQLoginCard(
                        loginState = loginState,
                        onRefresh = { qqLoginViewModel.refreshNow() },
                        onRefreshQr = { qqLoginViewModel.refreshQrCode() },
                        onQuickLogin = { qqLoginViewModel.quickLoginSavedAccount() },
                        onSaveQuickLogin = { qqLoginViewModel.saveQuickLoginAccount(it) },
                        onPasswordLogin = { uin, password -> qqLoginViewModel.passwordLogin(uin, password) },
                        onCaptchaLogin = { uin, password, ticket, randstr, sid ->
                            qqLoginViewModel.captchaLogin(uin, password, ticket, randstr, sid)
                        },
                        onNewDeviceLogin = { uin, password -> qqLoginViewModel.newDeviceLogin(uin, password) },
                    )
                }
                item {
                    RuntimeCard(
                        platformName = botProfile.platformName,
                        bridgeMode = botProfile.bridgeMode,
                        status = runtimeState.status,
                        details = runtimeState.details,
                        progressLabel = runtimeState.progressLabel,
                        progressPercent = runtimeState.progressPercent,
                        progressIndeterminate = runtimeState.progressIndeterminate,
                        installerCached = runtimeState.installerCached,
                        onStart = { ContainerBridgeController.start(context) },
                        onCheck = { ContainerBridgeController.check(context) },
                        onStop = { ContainerBridgeController.stop(context) },
                    )
                }
                item {
                    ContextPreviewCard(
                        sessionTitle = sessions.firstOrNull()?.title.orEmpty(),
                        preview = sessions.firstOrNull()?.let { conversationViewModel.contextPreview(it.id) }.orEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BotListPanel(
    bots: List<BotProfile>,
    selectedBotId: String,
    onSelectBot: (String) -> Unit,
    onCreateBot: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onCreateBot) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create bot")
                    }
                    IconButton(
                        onClick = onDeleteSelected,
                        enabled = bots.size > 1,
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete bot")
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bots.forEach { bot ->
                    val active = bot.id == selectedBotId
                    Card(
                        onClick = { onSelectBot(bot.id) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = bot.displayName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = bot.accountHint.ifBlank { bot.platformName },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotEditorCard(
    botProfile: BotProfile,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    accountHint: String,
    onAccountHintChange: (String) -> Unit,
    bridgeEndpoint: String,
    onBridgeEndpointChange: (String) -> Unit,
    triggerWords: String,
    onTriggerWordsChange: (String) -> Unit,
    autoReplyEnabled: Boolean,
    onAutoReplyChange: (Boolean) -> Unit,
    providerOptions: List<BotChoice>,
    selectedProviderId: String,
    onSelectProvider: (String) -> Unit,
    personaOptions: List<BotChoice>,
    selectedPersonaId: String,
    onSelectPersona: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bot Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Bot ID: ${botProfile.id}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = accountHint,
                onValueChange = onAccountHintChange,
                label = { Text("QQ account hint") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bridgeEndpoint,
                onValueChange = onBridgeEndpointChange,
                label = { Text("Bridge endpoint") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = triggerWords,
                onValueChange = onTriggerWordsChange,
                label = { Text("Trigger words (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceDropdown(
                title = "Default chat provider",
                options = providerOptions,
                selectedId = selectedProviderId,
                emptyLabel = "No provider available",
                onSelect = onSelectProvider,
            )
            ChoiceDropdown(
                title = "Default persona",
                options = personaOptions,
                selectedId = selectedPersonaId,
                emptyLabel = "No persona available",
                onSelect = onSelectPersona,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Auto reply", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use this bot as the default reply profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Switch(
                    checked = autoReplyEnabled,
                    onCheckedChange = onAutoReplyChange,
                )
            }
            Button(onClick = onSave) {
                Text("Save bot")
            }
        }
    }
}

@Composable
private fun RuntimeCard(
    platformName: String,
    bridgeMode: String,
    status: String,
    details: String,
    progressLabel: String,
    progressPercent: Int,
    progressIndeterminate: Boolean,
    installerCached: Boolean,
    onStart: () -> Unit,
    onCheck: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Runtime State", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Platform: $platformName")
            Text("Bridge mode: $bridgeMode")
            Text("Status: $status")
            Text("Details: $details")
            if (progressLabel.isNotBlank() || progressPercent > 0) {
                Text("Progress: ${progressLabel.ifBlank { "Working" }}")
                if (progressIndeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = if (progressIndeterminate) {
                        "This step downloads and installs NapCat from the network. Keep the app in the foreground."
                    } else {
                        "Completed ${progressPercent.coerceIn(0, 100)}%. Network install may pause between package steps."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Text(
                text = "Installation state: ${if (installerCached) "Existing installation detected" else "First start will install from network"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart) {
                    Text("Start")
                }
                Button(onClick = onCheck) {
                    Text("Check")
                }
                Button(onClick = onStop) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun QQLoginCard(
    loginState: NapCatLoginState,
    onRefresh: () -> Unit,
    onRefreshQr: () -> Unit,
    onQuickLogin: () -> Unit,
    onSaveQuickLogin: (String) -> Unit,
    onPasswordLogin: (String, String) -> Unit,
    onCaptchaLogin: (String, String, String, String, String) -> Unit,
    onNewDeviceLogin: (String, String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val qrBitmap = remember(loginState.qrCodeUrl) {
        loginState.qrCodeUrl
            .takeIf { it.isNotBlank() }
            ?.let { buildQrBitmap(it, 720) }
    }
    var uinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var ticketInput by remember { mutableStateOf("") }
    var randstrInput by remember { mutableStateOf("") }
    var sidInput by remember { mutableStateOf("") }

    LaunchedEffect(loginState.quickLoginUin, loginState.uin) {
        if (uinInput.isBlank()) {
            uinInput = loginState.quickLoginUin.ifBlank { loginState.uin }
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("QQ Login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(loginState.statusText)

            if (loginState.quickLoginUin.isNotBlank()) {
                Text(
                    text = "Saved quick login account: ${loginState.quickLoginUin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = "This account can be used for quick login after restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                if (!loginState.isLogin) {
                    Button(
                        onClick = onQuickLogin,
                        enabled = loginState.bridgeReady,
                    ) {
                        Text("Quick login")
                    }
                }
            }

            if (loginState.isLogin) {
                Text("QQ: ${loginState.uin.ifBlank { "Unknown" }}")
                Text("Nickname: ${loginState.nick.ifBlank { "Unknown" }}")
                if (loginState.quickLoginUin == loginState.uin && loginState.uin.isNotBlank()) {
                    Text(
                        text = "Quick login is ready for the current account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                } else {
                    Button(
                        onClick = { onSaveQuickLogin(loginState.uin) },
                        enabled = loginState.bridgeReady && loginState.uin.isNotBlank(),
                    ) {
                        Text("Save quick login")
                    }
                }
            } else if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QQ login QR code",
                    modifier = Modifier.size(240.dp),
                )
                Text(
                    text = loginState.qrCodeUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "The QR code will appear here after NapCat WebUI is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            if (!loginState.isLogin) {
                OutlinedTextField(
                    value = uinInput,
                    onValueChange = { uinInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("QQ account") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("QQ password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPasswordLogin(uinInput, passwordInput) },
                        enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                    ) {
                        Text("Password login")
                    }
                    OutlinedButton(
                        onClick = { onSaveQuickLogin(uinInput) },
                        enabled = loginState.bridgeReady && uinInput.isNotBlank(),
                    ) {
                        Text("Save account")
                    }
                }

                if (loginState.needCaptcha) {
                    Text(
                        text = "Captcha verification is required for this account.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (loginState.captchaUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { uriHandler.openUri(loginState.captchaUrl) },
                        ) {
                            Text("Open captcha page")
                        }
                        Text(
                            text = loginState.captchaUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedTextField(
                        value = ticketInput,
                        onValueChange = { ticketInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Captcha ticket") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = randstrInput,
                        onValueChange = { randstrInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Captcha randstr") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = sidInput,
                        onValueChange = { sidInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Captcha sid (optional)") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            onCaptchaLogin(
                                uinInput,
                                passwordInput,
                                ticketInput,
                                randstrInput,
                                sidInput,
                            )
                        },
                        enabled = loginState.bridgeReady &&
                            uinInput.isNotBlank() &&
                            passwordInput.isNotBlank() &&
                            ticketInput.isNotBlank() &&
                            randstrInput.isNotBlank(),
                    ) {
                        Text("Submit captcha")
                    }
                }

                if (loginState.needNewDevice) {
                    Text(
                        text = "New device verification is required.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (loginState.newDeviceJumpUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { uriHandler.openUri(loginState.newDeviceJumpUrl) },
                        ) {
                            Text("Open verification page")
                        }
                        Text(
                            text = loginState.newDeviceJumpUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { onNewDeviceLogin(uinInput, passwordInput) },
                        enabled = loginState.bridgeReady &&
                            uinInput.isNotBlank() &&
                            passwordInput.isNotBlank() &&
                            loginState.newDeviceSig.isNotBlank(),
                    ) {
                        Text("Continue device login")
                    }
                }
            }

            if (loginState.loginError.isNotBlank()) {
                Text(
                    text = loginState.loginError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) {
                    Text("Refresh status")
                }
                Button(
                    onClick = onRefreshQr,
                    enabled = loginState.bridgeReady && !loginState.isLogin,
                ) {
                    Text("Refresh QR")
                }
            }
        }
    }
}

@Composable
private fun ContextPreviewCard(
    sessionTitle: String,
    preview: String,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Context Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (sessionTitle.isBlank()) {
                Text("No conversation yet")
            } else {
                Text("Session: $sessionTitle")
                Text(preview.ifBlank { "No context generated yet" })
            }
        }
    }
}

@Composable
private fun ChoiceDropdown(
    title: String,
    options: List<BotChoice>,
    selectedId: String,
    emptyLabel: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(options, selectedId) { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId } ?: options.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = selected?.label ?: emptyLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!selected?.sublabel.isNullOrBlank()) {
                        Text(
                            text = selected?.sublabel.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(emptyLabel) },
                        onClick = { expanded = false },
                    )
                } else {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (option.sublabel.isNotBlank()) {
                                        Text(
                                            text = option.sublabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelect(option.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun BotProfile.toEditableProfile(
    displayName: String,
    accountHint: String,
    bridgeEndpoint: String,
    triggerWords: String,
    autoReplyEnabled: Boolean,
    defaultProviderId: String,
    defaultPersonaId: String,
): BotProfile {
    return copy(
        displayName = displayName.trim().ifBlank { this.displayName },
        accountHint = accountHint,
        bridgeEndpoint = bridgeEndpoint,
        triggerWords = triggerWords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        autoReplyEnabled = autoReplyEnabled,
        defaultProviderId = defaultProviderId,
        defaultPersonaId = defaultPersonaId,
    )
}

private fun buildQrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            )
        }
    }
    return bitmap
}
