package com.astrbot.android.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val FloatingBottomNavFabBottomPadding = 88.dp
val AppTopBarHeight = 58.dp

sealed interface SecondaryTopBarSpec {
    data class SubPage(
        val title: String,
        val onBack: () -> Unit,
    ) : SecondaryTopBarSpec

    data class ConfigDetail(
        val profileName: String,
        val currentSectionTitle: String,
        val onBack: () -> Unit,
        val onOpenSections: () -> Unit,
    ) : SecondaryTopBarSpec
}

enum class GlobalTopBarLayer {
    MAIN,
    SECONDARY,
    NONE,
}

data class RegisteredSecondaryTopBar(
    val route: String,
    val spec: SecondaryTopBarSpec,
)

data class ConfigDetailChromeBinding(
    val currentSectionTitle: String,
    val onOpenSections: () -> Unit,
)

data class SecondaryTopBarStrings(
    val config: String,
    val logs: String,
    val qqAccount: String,
    val qqLogin: String,
    val settings: String,
    val assetManagement: String,
    val pluginDetail: String,
    val pluginWorkspace: String,
    val pluginConfig: String,
    val models: String,
    val runtime: String,
    val dataBackup: String,
    val cronJobs: String,
    val configDetailDefaultSection: String,
)

val LocalSecondaryTopBarRegistrar =
    staticCompositionLocalOf<(String, SecondaryTopBarSpec?) -> Unit> { { _, _ -> } }
val LocalConfigDetailChromeRegistrar =
    staticCompositionLocalOf<(ConfigDetailChromeBinding?) -> Unit> { { _ -> } }

fun resolveGlobalTopBarLayer(
    activeMainRoute: String?,
    hasSecondaryTopBar: Boolean,
): GlobalTopBarLayer = when {
    activeMainRoute != null -> GlobalTopBarLayer.MAIN
    hasSecondaryTopBar -> GlobalTopBarLayer.SECONDARY
    else -> GlobalTopBarLayer.NONE
}

@Composable
fun RegisterSecondaryTopBar(
    route: String,
    spec: SecondaryTopBarSpec,
) {
    val registrar = LocalSecondaryTopBarRegistrar.current
    DisposableEffect(route, spec, registrar) {
        registrar(route, spec)
        onDispose {
            registrar(route, null)
        }
    }
}

@Composable
fun RegisterSecondaryTopBar(spec: SecondaryTopBarSpec) {
    RegisterSecondaryTopBar(route = "", spec = spec)
}

@Composable
fun RegisterConfigDetailChromeBinding(binding: ConfigDetailChromeBinding) {
    val registrar = LocalConfigDetailChromeRegistrar.current
    DisposableEffect(binding, registrar) {
        registrar(binding)
        onDispose {
            registrar(null)
        }
    }
}

@Composable
fun SecondaryTopBarPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(AppTopBarHeight),
    )
}
