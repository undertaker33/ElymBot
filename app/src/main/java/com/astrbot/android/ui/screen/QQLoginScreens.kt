package com.astrbot.android.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QQAccountCenterScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    qqLoginViewModel: QQLoginViewModel = viewModel(),
) {
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val quickLoginEnabled = loginState.canQuickLogin()

    Scaffold(
        topBar = { SubPageHeader(title = "QQ 账号", onBack = onBack) },
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF171717),
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "QQ 登录中心",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    if (loginState.isLogin) {
                        Text(
                            text = "当前已登录：${loginState.nick.ifBlank { loginState.uin.ifBlank { "未知账号" } }}",
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                    if (loginState.quickLoginUin.isNotBlank()) {
                        Text(
                            text = "已保存快捷登录账号：${loginState.quickLoginUin}",
                            color = Color.White.copy(alpha = 0.58f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenLogin,
                            modifier = Modifier.weight(1f),
                            colors = qqPrimaryButtonColors(),
                        ) {
                            Text("去登录")
                        }
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            enabled = false,
                            colors = qqDarkOutlinedButtonColors(),
                            border = qqDarkOutlinedButtonBorder(enabled = false),
                        ) {
                            Text("退出登录")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { qqLoginViewModel.quickLoginSavedAccount() },
                            modifier = Modifier.weight(1f),
                            enabled = quickLoginEnabled,
                            colors = qqDarkOutlinedButtonColors(),
                            border = qqDarkOutlinedButtonBorder(enabled = quickLoginEnabled),
                        ) {
                            Text("快捷登录")
                        }
                        OutlinedButton(
                            onClick = { qqLoginViewModel.refreshNow() },
                            modifier = Modifier.weight(1f),
                            colors = qqDarkOutlinedButtonColors(),
                            border = qqDarkOutlinedButtonBorder(enabled = true),
                        ) {
                            Text("刷新登录状态")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QQLoginScreen(
    onBack: () -> Unit,
    qqLoginViewModel: QQLoginViewModel = viewModel(),
) {
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val quickLoginEnabled = loginState.canQuickLogin()
    val uriHandler = LocalUriHandler.current
    val qrBitmap = remember(loginState.qrCodeUrl) {
        loginState.qrCodeUrl.takeIf { it.isNotBlank() }?.let { buildLoginQrBitmap(it, 720) }
    }
    var loginMode by remember { mutableStateOf(QqLoginMode.Qr) }
    var uinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    LaunchedEffect(loginState.quickLoginUin, loginState.uin) {
        if (uinInput.isBlank()) {
            uinInput = loginState.quickLoginUin.ifBlank { loginState.uin }
        }
    }

    Scaffold(
        topBar = { SubPageHeader(title = "去登录", onBack = onBack) },
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LoginModeToggle(
                    qrSelected = loginMode == QqLoginMode.Qr,
                    onSelectQr = { loginMode = QqLoginMode.Qr },
                    onSelectPassword = { loginMode = QqLoginMode.Password },
                )
            }

            if (loginMode == QqLoginMode.Qr) {
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
                            Text("扫码登录", fontWeight = FontWeight.SemiBold)
                            if (qrBitmap != null && !loginState.isLogin) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QQ 登录二维码",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (loginState.isLogin) {
                                Text("当前已登录：${loginState.nick.ifBlank { loginState.uin.ifBlank { "未知账号" } }}")
                            } else {
                                Text("NapCat WebUI 就绪后，这里会显示二维码。")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { qqLoginViewModel.refreshQrCode() },
                                    enabled = loginState.bridgeReady && !loginState.isLogin,
                                    colors = qqPrimaryButtonColors(),
                                ) {
                                    Text("刷新二维码")
                                }
                                OutlinedButton(
                                    onClick = { qqLoginViewModel.quickLoginSavedAccount() },
                                    enabled = quickLoginEnabled,
                                ) {
                                    Text("快捷登录")
                                }
                            }
                        }
                    }
                }
            } else {
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
                            Text("账号密码登录", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = uinInput,
                                onValueChange = { uinInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("输入 QQ 号") },
                                singleLine = true,
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("输入 QQ 密码") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = { qqLoginViewModel.passwordLogin(uinInput, passwordInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = qqPrimaryButtonColors(),
                            ) {
                                Text("登录")
                            }
                            OutlinedButton(
                                onClick = { qqLoginViewModel.saveQuickLoginAccount(uinInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("保存账号")
                            }
                            if (loginState.needNewDevice) {
                                Button(
                                    onClick = { qqLoginViewModel.newDeviceLogin(uinInput, passwordInput) },
                                    enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = qqPrimaryButtonColors(),
                                ) {
                                    Text("继续设备验证")
                                }
                            }
                            if (loginState.loginError.isNotBlank()) {
                                Text(loginState.loginError, color = Color(0xFFB3261E))
                            }
                            if (loginState.captchaUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(loginState.captchaUrl) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("打开验证码页面")
                                }
                            }
                            if (loginState.newDeviceJumpUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(loginState.newDeviceJumpUrl) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("打开设备验证")
                                }
                            }
                        }
                    }
                }

                if (loginState.needCaptcha) {
                    item {
                        QQCaptchaCard(
                            onSubmit = { ticket, randstr, sid ->
                                qqLoginViewModel.captchaLogin(uinInput, passwordInput, ticket, randstr, sid)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginModeToggle(
    qrSelected: Boolean,
    onSelectQr: () -> Unit,
    onSelectPassword: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFE7E7E4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QQModeChip(
                label = "扫码登录",
                selected = qrSelected,
                modifier = Modifier.weight(1f),
                onClick = onSelectQr,
            )
            QQModeChip(
                label = "账号密码登录",
                selected = !qrSelected,
                modifier = Modifier.weight(1f),
                onClick = onSelectPassword,
            )
        }
    }
}

@Composable
private fun QQModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color.White else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                color = if (selected) Color(0xFF202020) else Color(0xFF777777),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QQCaptchaCard(
    onSubmit: (String, String, String) -> Unit,
) {
    var ticketInput by remember { mutableStateOf("") }
    var randstrInput by remember { mutableStateOf("") }
    var sidInput by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("验证码校验", fontWeight = FontWeight.SemiBold)
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
                modifier = Modifier.fillMaxWidth(),
                colors = qqPrimaryButtonColors(),
            ) {
                Text("提交验证码")
            }
        }
    }
}

@Composable
private fun qqPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF1B1B1B),
    contentColor = Color.White,
    disabledContainerColor = Color(0xFFD8D8D4),
    disabledContentColor = Color.White.copy(alpha = 0.75f),
)

@Composable
private fun qqDarkOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = Color.Transparent,
    contentColor = Color.White,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = Color.White.copy(alpha = 0.28f),
)

private fun qqDarkOutlinedButtonBorder(enabled: Boolean): BorderStroke {
    return BorderStroke(
        width = 1.5.dp,
        color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.18f),
    )
}

private fun NapCatLoginState.canQuickLogin(): Boolean {
    return bridgeReady && quickLoginUin.isNotBlank() && !isLogin
}

private fun buildLoginQrBitmap(content: String, sizePx: Int): Bitmap {
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

private enum class QqLoginMode {
    Qr,
    Password,
}
