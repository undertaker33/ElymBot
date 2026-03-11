package com.astrbot.android.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    onOpenQqAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    EntryListPage(
        entries = listOf(
            EntryCardState(
                title = "QQ 账号",
                subtitle = "扫码登录、快速登录、密码登录和验证码流程",
                icon = Icons.Outlined.PersonOutline,
                onClick = onOpenQqAccount,
            ),
            EntryCardState(
                title = "设置",
                subtitle = "运行设置、容器控制和应用配置",
                icon = Icons.Outlined.Settings,
                onClick = onOpenSettings,
            ),
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QQAccountScreen(
    onBack: () -> Unit,
    qqLoginViewModel: QQLoginViewModel = viewModel(),
) {
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val qrBitmap = remember(loginState.qrCodeUrl) {
        loginState.qrCodeUrl.takeIf { it.isNotBlank() }?.let { buildAccountQrBitmap(it, 720) }
    }
    val scope = rememberCoroutineScope()
    var uinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val uinBringIntoViewRequester = remember { BringIntoViewRequester() }
    val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(loginState.quickLoginUin, loginState.uin) {
        if (uinInput.isBlank()) {
            uinInput = loginState.quickLoginUin.ifBlank { loginState.uin }
        }
    }

    Scaffold(
        topBar = { SubPageHeader(title = "QQ 账号", onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF161616),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "QQ 登录中心",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(loginState.statusText, color = Color.White.copy(alpha = 0.78f))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionChip("刷新状态", Icons.Outlined.Refresh) { qqLoginViewModel.refreshNow() }
                                QuickActionChip("刷新二维码", Icons.Outlined.QrCode2) { qqLoginViewModel.refreshQrCode() }
                            }
                            if (loginState.quickLoginUin.isNotBlank() && !loginState.isLogin) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuickActionChip("快速登录", Icons.Outlined.Bolt) {
                                        qqLoginViewModel.quickLoginSavedAccount()
                                    }
                                }
                            }
                            if (loginState.isLogin) {
                                Text(
                                    "当前登录：${loginState.nick.ifBlank { loginState.uin.ifBlank { "未知账号" } }}",
                                    color = Color.White.copy(alpha = 0.78f),
                                )
                                Text(
                                    "当前 NapCat 集成还没有接好真实登出接口。",
                                    color = Color.White.copy(alpha = 0.58f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("二维码登录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (loginState.quickLoginUin.isNotBlank()) {
                            Text(
                                "已保存快速登录账号：${loginState.quickLoginUin}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            if (!loginState.isLogin) {
                                Button(
                                    onClick = { qqLoginViewModel.quickLoginSavedAccount() },
                                    enabled = loginState.quickLoginUin.isNotBlank(),
                                    colors = monochromeButtonColors(),
                                ) {
                                    Text("快速登录")
                                }
                            }
                        }
                        if (qrBitmap != null && !loginState.isLogin) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QQ 二维码",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(loginState.qrCodeUrl, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                        } else if (loginState.isLogin) {
                            Text("当前登录：${loginState.nick.ifBlank { loginState.uin.ifBlank { "未知账号" } }}")
                        } else {
                            Text("NapCat WebUI 就绪后，这里会显示二维码。")
                        }
                        if (loginState.captchaUrl.isNotBlank()) {
                            OutlinedButton(onClick = { uriHandler.openUri(loginState.captchaUrl) }) {
                                Text("打开验证码页面")
                            }
                        }
                        if (loginState.newDeviceJumpUrl.isNotBlank()) {
                            OutlinedButton(onClick = { uriHandler.openUri(loginState.newDeviceJumpUrl) }) {
                                Text("打开设备验证")
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("账号密码登录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = uinInput,
                            onValueChange = { uinInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(uinBringIntoViewRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            uinBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            label = { Text("QQ 账号") },
                            singleLine = true,
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(passwordBringIntoViewRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            passwordBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            label = { Text("QQ 密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { qqLoginViewModel.passwordLogin(uinInput, passwordInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                colors = monochromeButtonColors(),
                            ) {
                                Text("密码登录")
                            }
                            OutlinedButton(
                                onClick = { qqLoginViewModel.saveQuickLoginAccount(uinInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank(),
                            ) {
                                Text("保存账号")
                            }
                        }
                        if (loginState.needNewDevice) {
                            Button(
                                onClick = { qqLoginViewModel.newDeviceLogin(uinInput, passwordInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                colors = monochromeButtonColors(),
                            ) {
                                Text("继续设备登录")
                            }
                        }
                        if (loginState.loginError.isNotBlank()) {
                            Text(loginState.loginError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (loginState.needCaptcha) {
                item {
                    CaptchaCard(
                        onSubmit = { ticket, randstr, sid ->
                            qqLoginViewModel.captchaLogin(uinInput, passwordInput, ticket, randstr, sid)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenRuntime: () -> Unit,
) {
    SubPageScaffold(
        title = "设置",
        onBack = onBack,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            EntryListPage(
                modifier = Modifier.fillMaxSize(),
                entries = listOf(
                    EntryCardState(
                        title = "运行设置",
                        subtitle = "桥接地址、启动命令和自动启动控制",
                        icon = Icons.Outlined.Settings,
                        onClick = onOpenRuntime,
                    ),
                ),
            )
        }
    }
}

@Composable
internal fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { SubPageHeader(title = title, onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MonochromeUi.pageBackground,
        content = content,
    )
}

@Composable
internal fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Surface(
        color = MonochromeUi.pageBackground,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White, CircleShape)
                        .padding(9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MonochromeUi.textPrimary,
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EntryListPage(
    entries: List<EntryCardState>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(MonochromeUi.pageBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { entry ->
                    AccountEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun AccountEntryCard(entry: EntryCardState) {
    Surface(
        onClick = entry.onClick,
        shape = RoundedCornerShape(24.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Icon(entry.icon, contentDescription = null, tint = Color(0xFF111111))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(entry.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF2A2A2A),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(label, color = Color.White)
        }
    }
}

@Composable
private fun monochromeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF1B1B1B),
    contentColor = Color.White,
    disabledContainerColor = Color(0xFFCFCFCA),
    disabledContentColor = Color(0xFF7A7A7A),
)

@Composable
private fun CaptchaCard(
    onSubmit: (String, String, String) -> Unit,
) {
    var ticketInput by remember { mutableStateOf("") }
    var randstrInput by remember { mutableStateOf("") }
    var sidInput by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("验证码校验", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("完成验证码页面后，把返回的参数粘贴到这里。")
            OutlinedTextField(
                value = ticketInput,
                onValueChange = { ticketInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ticket") },
                colors = monochromeOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = randstrInput,
                onValueChange = { randstrInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Randstr") },
                colors = monochromeOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = sidInput,
                onValueChange = { sidInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Sid（可选）") },
                colors = monochromeOutlinedTextFieldColors(),
            )
            Button(
                onClick = { onSubmit(ticketInput, randstrInput, sidInput) },
                enabled = ticketInput.isNotBlank() && randstrInput.isNotBlank(),
                colors = monochromeButtonColors(),
            ) {
                Text("提交验证码")
            }
        }
    }
}

private data class EntryCardState(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

private fun buildAccountQrBitmap(content: String, sizePx: Int): Bitmap {
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
