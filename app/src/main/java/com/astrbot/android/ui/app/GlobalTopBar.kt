package com.astrbot.android.ui.app

import androidx.compose.runtime.Composable
import com.astrbot.android.ui.common.SubPageHeader
import com.astrbot.android.ui.config.detail.ConfigDetailTopBar
import com.astrbot.android.ui.navigation.AppDestination

internal fun resolveEffectiveSecondaryTopBarSpec(
    currentRoute: String?,
    registered: RegisteredSecondaryTopBar?,
    fallback: SecondaryTopBarSpec?,
): SecondaryTopBarSpec? = when {
    matchesConfigDetailRoute(currentRoute) && fallback is SecondaryTopBarSpec.ConfigDetail -> fallback
    registered != null && registered.route == currentRoute -> registered.spec
    else -> fallback
}

internal fun fallbackSecondaryTopBarSpecForRoute(
    route: String?,
    strings: SecondaryTopBarStrings,
    configDetailProfileName: String? = null,
    configDetailCurrentSectionTitle: String? = null,
    configDetailOnOpenSections: () -> Unit = {},
): SecondaryTopBarSpec? {
    if (matchesConfigDetailRoute(route)) {
        return SecondaryTopBarSpec.ConfigDetail(
            profileName = configDetailProfileName ?: strings.config,
            currentSectionTitle = configDetailCurrentSectionTitle ?: strings.configDetailDefaultSection,
            onBack = {},
            onOpenSections = configDetailOnOpenSections,
        )
    }

    val title = when (route) {
        AppDestination.Logs.route -> strings.logs
        AppDestination.QQAccount.route -> strings.qqAccount
        AppDestination.QQLogin.route -> strings.qqLogin
        AppDestination.SettingsHub.route -> strings.settings
        AppDestination.Assets.route -> strings.assetManagement
        AppDestination.Models.route -> strings.models
        AppDestination.Runtime.route -> strings.runtime
        AppDestination.CronJobs.route -> strings.cronJobs
        AppDestination.BackupHub.route,
        AppDestination.BotBackup.route,
        AppDestination.ModelBackup.route,
        AppDestination.PersonaBackup.route,
        AppDestination.ConversationBackup.route,
        AppDestination.ConfigBackup.route,
        AppDestination.TtsBackup.route,
        AppDestination.FullBackup.route,
        -> strings.dataBackup
        else -> null
    } ?: if (matchesAssetDetailRoute(route)) {
        strings.assetManagement
    } else if (matchesPluginWorkspaceRoute(route)) {
        strings.pluginWorkspace
    } else if (matchesPluginConfigRoute(route)) {
        strings.pluginConfig
    } else if (matchesPluginDetailRoute(route)) {
        strings.pluginDetail
    } else {
        null
    } ?: return null

    return SecondaryTopBarSpec.SubPage(
        title = title,
        onBack = {},
    )
}

private fun matchesConfigDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.ConfigDetail.route ||
        route.startsWith("config/detail/")
}

private fun matchesAssetDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.AssetDetail.route ||
        (route.startsWith("asset-management/") && route != AppDestination.Assets.route)
}

private fun matchesPluginDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.PluginDetail.route ||
        (route.startsWith("plugins/detail/") &&
            route != AppDestination.Plugins.route &&
            !route.endsWith("/config") &&
            !route.endsWith("/workspace"))
}

private fun matchesPluginWorkspaceRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.PluginWorkspace.route || route.endsWith("/workspace")
}

private fun matchesPluginConfigRoute(route: String?): Boolean {
    if (route == null) return false
    return route == AppDestination.PluginConfig.route || route.endsWith("/config")
}

@Composable
internal fun SecondaryTopBarHost(spec: SecondaryTopBarSpec) {
    when (spec) {
        is SecondaryTopBarSpec.SubPage -> {
            SubPageHeader(
                title = spec.title,
                onBack = spec.onBack,
            )
        }

        is SecondaryTopBarSpec.ConfigDetail -> {
            ConfigDetailTopBar(
                profileName = spec.profileName,
                currentSectionTitle = spec.currentSectionTitle,
                onBack = spec.onBack,
                onOpenSections = spec.onOpenSections,
            )
        }
    }
}
