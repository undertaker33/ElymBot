package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.ui.viewmodel.PluginScreenUiState

enum class PluginUiSection {
    Installed,
    RepositorySources,
    Discoverable,
}

data class PluginWorkspaceSectionPresentation(
    val section: PluginUiSection,
    val count: Int,
)

data class PluginRecordPresentation(
    val badges: List<String>,
)

data class PluginPermissionPresentation(
    val title: String,
    val description: String,
    val requirementLabel: String,
)

internal fun buildPluginWorkspaceSections(
    uiState: PluginScreenUiState,
): List<PluginWorkspaceSectionPresentation> {
    return listOf(
        PluginWorkspaceSectionPresentation(
            section = PluginUiSection.Installed,
            count = uiState.records.size,
        ),
        PluginWorkspaceSectionPresentation(
            section = PluginUiSection.RepositorySources,
            count = uiState.repositorySources.size,
        ),
        PluginWorkspaceSectionPresentation(
            section = PluginUiSection.Discoverable,
            count = uiState.catalogEntries.size,
        ),
    )
}

internal fun buildPluginRecordPresentation(
    record: PluginInstallRecord,
): PluginRecordPresentation {
    return PluginRecordPresentation(
        badges = listOf(
            pluginSourceLabelPlain(record.source.sourceType),
            pluginCompatibilityLabelPlain(record.compatibilityState.status),
        ),
    )
}

internal fun buildPluginPermissionPresentation(
    record: PluginInstallRecord,
): List<PluginPermissionPresentation> {
    return record.permissionSnapshot.map(::toPermissionPresentation)
}

internal fun pluginSourceLabelPlain(sourceType: PluginSourceType): String {
    return when (sourceType) {
        PluginSourceType.LOCAL_FILE -> "Local file"
        PluginSourceType.MANUAL_IMPORT -> "Manual import"
        PluginSourceType.REPOSITORY -> "Repository"
        PluginSourceType.DIRECT_LINK -> "Direct link"
    }
}

internal fun pluginCompatibilityLabelPlain(status: PluginCompatibilityStatus): String {
    return when (status) {
        PluginCompatibilityStatus.COMPATIBLE -> "Compatible"
        PluginCompatibilityStatus.INCOMPATIBLE -> "Incompatible"
        PluginCompatibilityStatus.UNKNOWN -> "Compatibility unknown"
    }
}

private fun toPermissionPresentation(
    permission: PluginPermissionDeclaration,
): PluginPermissionPresentation {
    return PluginPermissionPresentation(
        title = permission.title,
        description = permission.description,
        requirementLabel = if (permission.required) "Required permission" else "Optional permission",
    )
}
