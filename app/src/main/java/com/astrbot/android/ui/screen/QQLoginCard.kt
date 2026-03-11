package com.astrbot.android.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrbot.android.model.NapCatLoginState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QQLoginCard(
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

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = CardDefaults.cardColors().containerColor,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("QQ Login", style = MaterialTheme.typography.titleLarge)
                Text(loginState.statusText)

                if (loginState.quickLoginUin.isNotBlank()) {
                    Text(
                        text = "Saved quick login account: ${loginState.quickLoginUin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
