package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.runtime.plugin.compareVersions
import com.astrbot.android.ui.screen.plugin.PluginBadgePalette
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.MonochromeUi
import java.net.URI

enum class PluginHomepageSection {
    Hero,
    QuickInstall,
    HealthOverview,
    InstalledLibrary,
    Discover,
    Repositories,
}

enum class PluginQuickInstallMode {
    LocalZip,
    RepositoryUrl,
    DirectPackageUrl,
}

enum class PluginLocalFilter {
    ALL,
    ENABLED,
    DISABLED,
    UPDATES,
}

enum class PluginInstalledLibraryFilter {
    All,
    Enabled,
    Disabled,
    Updates,
    Issues,
    PermissionChanges,
}

enum class PluginInstalledLibraryPriority {
    Normal,
    Attention,
    Critical,
}

enum class PluginInstalledLibraryStatus {
    Enabled,
    Disabled,
    UpdateAvailable,
    CompatibilityUnknown,
    Incompatible,
    PermissionChanges,
    Suspended,
}

enum class PluginInstalledLibraryInsight {
    UpToDate,
    DisabledReady,
    UpdateAvailable,
    CompatibilityUnknown,
    Incompatible,
    PermissionChanges,
    Suspended,
}

enum class PluginInstalledLibraryPrimaryAction {
    Open,
    Review,
    Update,
}

enum class PluginInstalledLibraryBulkAction {
    ReviewIssues,
    ReviewUpdates,
    ReviewDisabled,
}

data class PluginInstalledLibrarySummaryPresentation(
    val totalCount: Int,
    val enabledCount: Int,
    val needsReviewCount: Int,
    val updatesCount: Int,
    val disabledCount: Int,
)

data class PluginInstalledLibraryBulkActionPresentation(
    val action: PluginInstalledLibraryBulkAction,
    val count: Int,
    val targetFilter: PluginInstalledLibraryFilter,
    val enabled: Boolean,
)

data class PluginInstalledLibraryFilterPresentation(
    val filter: PluginInstalledLibraryFilter,
    val count: Int,
    val selected: Boolean,
)

data class PluginInstalledLibraryCardPresentation(
    val pluginId: String,
    val title: String,
    val versionLabel: String,
    val sourceLabel: String,
    val priority: PluginInstalledLibraryPriority,
    val status: PluginInstalledLibraryStatus,
    val insight: PluginInstalledLibraryInsight,
    val primaryAction: PluginInstalledLibraryPrimaryAction,
    val isEnabled: Boolean,
    val hasUpdateAvailable: Boolean,
    val hasPermissionChanges: Boolean,
)

data class PluginInstalledLibraryPresentation(
    val summary: PluginInstalledLibrarySummaryPresentation,
    val bulkActions: List<PluginInstalledLibraryBulkActionPresentation>,
    val filters: List<PluginInstalledLibraryFilterPresentation>,
    val cards: List<PluginInstalledLibraryCardPresentation>,
)

data class PluginLocalFilterPresentation(
    val filter: PluginLocalFilter,
    val count: Int,
    val selected: Boolean,
)

data class PluginLocalCardPresentation(
    val pluginId: String,
    val title: String,
    val description: String,
    val author: String,
    val isEnabled: Boolean,
    val hasUpdateAvailable: Boolean,
)

data class PluginLocalWorkspacePresentation(
    val filters: List<PluginLocalFilterPresentation>,
    val cards: List<PluginLocalCardPresentation>,
)

enum class PluginMarketStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
}

enum class PluginMarketPrimaryAction {
    INSTALL,
    UPDATE,
    INSTALLED,
}

data class PluginMarketCardPresentation(
    val pluginId: String,
    val title: String,
    val description: String,
    val author: String,
    val versionLabel: String,
    val status: PluginMarketStatus,
    val repositoryUrl: String,
)

data class PluginMarketWorkspacePresentation(
    val cards: List<PluginMarketCardPresentation>,
)

data class PluginMarketDetailPresentation(
    val pluginId: String,
    val title: String,
    val author: String,
    val summary: String,
    val latestVersionLabel: String,
    val installedVersionLabel: String,
    val status: PluginMarketStatus,
    val primaryAction: PluginMarketPrimaryAction,
    val repositoryUrl: String,
    val repositoryHost: String,
    val sourceName: String,
)

data class PluginHealthOverviewPresentation(
    val installedCount: Int,
    val updatesAvailableCount: Int,
    val needsReviewCount: Int,
    val sourceCount: Int,
)

data class PluginQuickInstallPresentation(
    val selectedMode: PluginQuickInstallMode,
    val showLocalZipAction: Boolean,
    val showRepositoryUrlForm: Boolean,
    val showDirectPackageUrlForm: Boolean,
    val promotedModes: List<PluginQuickInstallMode>,
    val advancedModes: List<PluginQuickInstallMode>,
    val isAdvancedModeSelected: Boolean,
)

data class PluginLocalInstallSheetPresentation(
    val showSheet: Boolean,
    val selectedMode: PluginQuickInstallMode,
    val availableModes: List<PluginQuickInstallMode>,
)

data class PluginRecordPresentation(
    val badges: List<String>,
)

data class PluginPermissionPresentation(
    val title: String,
    val description: String,
    val requirementLabel: String,
)

internal fun buildPluginHomepageSections(): List<PluginHomepageSection> {
    return listOf(
        PluginHomepageSection.Hero,
        PluginHomepageSection.HealthOverview,
        PluginHomepageSection.InstalledLibrary,
        PluginHomepageSection.QuickInstall,
        PluginHomepageSection.Discover,
        PluginHomepageSection.Repositories,
    )
}

internal fun buildPluginHealthOverviewPresentation(
    uiState: PluginScreenUiState,
): PluginHealthOverviewPresentation {
    val updatesAvailableCount = uiState.updateAvailabilitiesByPluginId.values.count { it.updateAvailable }
    val needsReviewCount = uiState.records.count { record ->
        uiState.failureStatesByPluginId.containsKey(record.pluginId) ||
            record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE ||
            uiState.updateAvailabilitiesByPluginId[record.pluginId]?.updateAvailable == true
    }
    return PluginHealthOverviewPresentation(
        installedCount = uiState.records.size,
        updatesAvailableCount = updatesAvailableCount,
        needsReviewCount = needsReviewCount,
        sourceCount = uiState.repositorySources.size,
    )
}

internal fun buildPluginLocalWorkspacePresentation(
    uiState: PluginScreenUiState,
    searchQuery: String,
    selectedFilter: PluginLocalFilter,
): PluginLocalWorkspacePresentation {
    val normalizedQuery = searchQuery.trim()
    val cards = uiState.records
        .map { record -> buildPluginLocalCardPresentation(record, uiState) }
        .sortedBy { it.title.lowercase() }
    return PluginLocalWorkspacePresentation(
        filters = listOf(
            PluginLocalFilter.ALL,
            PluginLocalFilter.ENABLED,
            PluginLocalFilter.DISABLED,
            PluginLocalFilter.UPDATES,
        ).map { filter ->
            PluginLocalFilterPresentation(
                filter = filter,
                count = cards.count { it.matchesLocalFilter(filter) },
                selected = filter == selectedFilter,
            )
        },
        cards = cards
            .filter { it.matchesSearch(normalizedQuery) }
            .filter { it.matchesLocalFilter(selectedFilter) },
    )
}

internal fun buildPluginMarketWorkspacePresentation(
    uiState: PluginScreenUiState,
    searchQuery: String,
): PluginMarketWorkspacePresentation {
    val normalizedQuery = searchQuery.trim()
    val cards = uiState.catalogEntries
        .map { entry ->
            val installedRecord = uiState.records.firstOrNull { it.pluginId == entry.pluginId }
            PluginMarketCardPresentation(
                pluginId = entry.pluginId,
                title = entry.title,
                description = entry.summary,
                author = entry.author,
                versionLabel = entry.latestVersion,
                status = when {
                    installedRecord == null -> PluginMarketStatus.NOT_INSTALLED
                    compareVersions(entry.latestVersion, installedRecord.installedVersion) > 0 ->
                        PluginMarketStatus.UPDATE_AVAILABLE
                    else -> PluginMarketStatus.INSTALLED
                },
                repositoryUrl = entry.repositoryUrl,
            )
        }
        .sortedBy { it.title.lowercase() }
        .filter { it.matchesSearch(normalizedQuery) }
    return PluginMarketWorkspacePresentation(cards = cards)
}

internal fun buildPluginMarketDetailPresentation(
    uiState: PluginScreenUiState,
    pluginId: String,
): PluginMarketDetailPresentation? {
    val entry = uiState.catalogEntries.firstOrNull { it.pluginId == pluginId } ?: return null
    val installedRecord = uiState.records.firstOrNull { it.pluginId == pluginId }
    val status = when {
        installedRecord == null -> PluginMarketStatus.NOT_INSTALLED
        compareVersions(entry.latestVersion, installedRecord.installedVersion) > 0 ->
            PluginMarketStatus.UPDATE_AVAILABLE
        else -> PluginMarketStatus.INSTALLED
    }
    return PluginMarketDetailPresentation(
        pluginId = entry.pluginId,
        title = entry.title,
        author = entry.author,
        summary = entry.summary,
        latestVersionLabel = entry.latestVersion,
        installedVersionLabel = installedRecord?.installedVersion.orEmpty(),
        status = status,
        primaryAction = when (status) {
            PluginMarketStatus.NOT_INSTALLED -> PluginMarketPrimaryAction.INSTALL
            PluginMarketStatus.UPDATE_AVAILABLE -> PluginMarketPrimaryAction.UPDATE
            PluginMarketStatus.INSTALLED -> PluginMarketPrimaryAction.INSTALLED
        },
        repositoryUrl = entry.repositoryUrl,
        repositoryHost = entry.repositoryUrl.toRepositoryHost(),
        sourceName = entry.sourceName,
    )
}

internal fun buildPluginInstalledLibraryPresentation(
    uiState: PluginScreenUiState,
    selectedFilter: PluginInstalledLibraryFilter,
): PluginInstalledLibraryPresentation {
    val cards = uiState.records
        .map { record -> buildPluginInstalledLibraryCardPresentation(record, uiState) }
        .sortedWith(
            compareBy<PluginInstalledLibraryCardPresentation>({ it.priority.sortOrder() })
                .thenBy { it.title.lowercase() }
                .thenBy { it.pluginId },
        )
    return PluginInstalledLibraryPresentation(
        summary = PluginInstalledLibrarySummaryPresentation(
            totalCount = cards.size,
            enabledCount = cards.count { it.isEnabled },
            needsReviewCount = cards.count { it.priority != PluginInstalledLibraryPriority.Normal },
            updatesCount = cards.count { it.hasUpdateAvailable },
            disabledCount = cards.count { !it.isEnabled },
        ),
        bulkActions = listOf(
            PluginInstalledLibraryBulkActionPresentation(
                action = PluginInstalledLibraryBulkAction.ReviewIssues,
                count = cards.count { it.priority != PluginInstalledLibraryPriority.Normal },
                targetFilter = PluginInstalledLibraryFilter.Issues,
                enabled = cards.any { it.priority != PluginInstalledLibraryPriority.Normal },
            ),
            PluginInstalledLibraryBulkActionPresentation(
                action = PluginInstalledLibraryBulkAction.ReviewUpdates,
                count = cards.count { it.hasUpdateAvailable },
                targetFilter = PluginInstalledLibraryFilter.Updates,
                enabled = cards.any { it.hasUpdateAvailable },
            ),
            PluginInstalledLibraryBulkActionPresentation(
                action = PluginInstalledLibraryBulkAction.ReviewDisabled,
                count = cards.count { !it.isEnabled },
                targetFilter = PluginInstalledLibraryFilter.Disabled,
                enabled = cards.any { !it.isEnabled },
            ),
        ),
        filters = listOf(
            PluginInstalledLibraryFilter.All,
            PluginInstalledLibraryFilter.Enabled,
            PluginInstalledLibraryFilter.Disabled,
            PluginInstalledLibraryFilter.Updates,
            PluginInstalledLibraryFilter.Issues,
            PluginInstalledLibraryFilter.PermissionChanges,
        ).map { filter ->
            PluginInstalledLibraryFilterPresentation(
                filter = filter,
                count = cards.count { it.matchesFilter(filter) },
                selected = filter == selectedFilter,
            )
        },
        cards = cards.filter { it.matchesFilter(selectedFilter) },
    )
}

internal fun installedLibraryBadgePalette(priority: PluginInstalledLibraryPriority): PluginBadgePalette {
    return when (priority) {
        PluginInstalledLibraryPriority.Normal -> PluginBadgePalette(
            containerColor = MonochromeUi.mutedSurface,
            contentColor = MonochromeUi.textSecondary,
        )
        PluginInstalledLibraryPriority.Attention -> {
            val palette = PluginUiSpec.schemaStatusPalette(com.astrbot.android.model.plugin.PluginUiStatus.Warning)
            PluginBadgePalette(
                containerColor = palette.containerColor,
                contentColor = palette.contentColor,
            )
        }
        PluginInstalledLibraryPriority.Critical -> {
            val palette = PluginUiSpec.schemaStatusPalette(com.astrbot.android.model.plugin.PluginUiStatus.Error)
            PluginBadgePalette(
                containerColor = palette.containerColor,
                contentColor = palette.contentColor,
            )
        }
    }
}

internal fun buildPluginQuickInstallPresentation(
    selectedMode: PluginQuickInstallMode,
): PluginQuickInstallPresentation {
    return PluginQuickInstallPresentation(
        selectedMode = selectedMode,
        showLocalZipAction = selectedMode == PluginQuickInstallMode.LocalZip,
        showRepositoryUrlForm = selectedMode == PluginQuickInstallMode.RepositoryUrl,
        showDirectPackageUrlForm = selectedMode == PluginQuickInstallMode.DirectPackageUrl,
        promotedModes = listOf(
            PluginQuickInstallMode.LocalZip,
            PluginQuickInstallMode.RepositoryUrl,
        ),
        advancedModes = listOf(PluginQuickInstallMode.DirectPackageUrl),
        isAdvancedModeSelected = selectedMode == PluginQuickInstallMode.DirectPackageUrl,
    )
}

internal fun buildPluginLocalInstallSheetPresentation(
    showSheet: Boolean = true,
    selectedMode: PluginQuickInstallMode = PluginQuickInstallMode.LocalZip,
): PluginLocalInstallSheetPresentation {
    return PluginLocalInstallSheetPresentation(
        showSheet = showSheet,
        selectedMode = selectedMode,
        availableModes = PluginQuickInstallMode.entries,
    )
}

internal fun buildPluginLocalInstallSheetPresentation(): PluginLocalInstallSheetPresentation {
    return buildPluginLocalInstallSheetPresentation(
        showSheet = true,
        selectedMode = PluginQuickInstallMode.LocalZip,
    )
}

private fun buildPluginLocalCardPresentation(
    record: PluginInstallRecord,
    uiState: PluginScreenUiState,
): PluginLocalCardPresentation {
    return PluginLocalCardPresentation(
        pluginId = record.pluginId,
        title = record.manifestSnapshot.title,
        description = record.manifestSnapshot.description.ifBlank { record.manifestSnapshot.entrySummary },
        author = record.manifestSnapshot.author,
        isEnabled = record.enabled,
        hasUpdateAvailable = uiState.updateAvailabilitiesByPluginId[record.pluginId]?.updateAvailable == true,
    )
}

private fun buildPluginInstalledLibraryCardPresentation(
    record: PluginInstallRecord,
    uiState: PluginScreenUiState,
): PluginInstalledLibraryCardPresentation {
    val updateAvailability = uiState.updateAvailabilitiesByPluginId[record.pluginId]
    val failureState = uiState.failureStatesByPluginId[record.pluginId]
    val hasUpdateAvailable = updateAvailability?.updateAvailable == true
    val hasPermissionChanges = updateAvailability?.permissionDiff?.hasMeaningfulChanges() == true
    val criticalPermissionChanges = updateAvailability?.permissionDiff?.requiresSecondaryConfirmation == true
    val compatibilityStatus = record.compatibilityState.status

    return when {
        failureState?.isSuspended == true -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.Suspended,
            insight = PluginInstalledLibraryInsight.Suspended,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
        )
        compatibilityStatus == PluginCompatibilityStatus.INCOMPATIBLE -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.Incompatible,
            insight = PluginInstalledLibraryInsight.Incompatible,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
        )
        hasPermissionChanges && criticalPermissionChanges -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.PermissionChanges,
            insight = PluginInstalledLibraryInsight.PermissionChanges,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = true,
        )
        hasUpdateAvailable -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.UpdateAvailable,
            insight = PluginInstalledLibraryInsight.UpdateAvailable,
            primaryAction = PluginInstalledLibraryPrimaryAction.Update,
            isEnabled = record.enabled,
            hasUpdateAvailable = true,
            hasPermissionChanges = hasPermissionChanges,
        )
        hasPermissionChanges -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.PermissionChanges,
            insight = PluginInstalledLibraryInsight.PermissionChanges,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = false,
            hasPermissionChanges = true,
        )
        compatibilityStatus == PluginCompatibilityStatus.UNKNOWN -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.CompatibilityUnknown,
            insight = PluginInstalledLibraryInsight.CompatibilityUnknown,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = false,
            hasPermissionChanges = false,
        )
        else -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Normal,
            status = if (record.enabled) PluginInstalledLibraryStatus.Enabled else PluginInstalledLibraryStatus.Disabled,
            insight = if (record.enabled) {
                PluginInstalledLibraryInsight.UpToDate
            } else {
                PluginInstalledLibraryInsight.DisabledReady
            },
            primaryAction = PluginInstalledLibraryPrimaryAction.Open,
            isEnabled = record.enabled,
            hasUpdateAvailable = false,
            hasPermissionChanges = false,
        )
    }
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

private fun PluginInstalledLibraryPriority.sortOrder(): Int {
    return when (this) {
        PluginInstalledLibraryPriority.Critical -> 0
        PluginInstalledLibraryPriority.Attention -> 1
        PluginInstalledLibraryPriority.Normal -> 2
    }
}

private fun PluginInstalledLibraryCardPresentation.matchesFilter(filter: PluginInstalledLibraryFilter): Boolean {
    return when (filter) {
        PluginInstalledLibraryFilter.All -> true
        PluginInstalledLibraryFilter.Enabled -> isEnabled
        PluginInstalledLibraryFilter.Disabled -> !isEnabled
        PluginInstalledLibraryFilter.Updates -> hasUpdateAvailable
        PluginInstalledLibraryFilter.Issues -> priority != PluginInstalledLibraryPriority.Normal
        PluginInstalledLibraryFilter.PermissionChanges -> hasPermissionChanges
    }
}

private fun PluginLocalCardPresentation.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        author.contains(query, ignoreCase = true)
}

private fun PluginMarketCardPresentation.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        author.contains(query, ignoreCase = true)
}

private fun String.toRepositoryHost(): String {
    if (isBlank()) return ""
    return runCatching { URI(this).host.orEmpty() }.getOrDefault("")
}

private fun PluginLocalCardPresentation.matchesLocalFilter(filter: PluginLocalFilter): Boolean {
    return when (filter) {
        PluginLocalFilter.ALL -> true
        PluginLocalFilter.ENABLED -> isEnabled
        PluginLocalFilter.DISABLED -> !isEnabled
        PluginLocalFilter.UPDATES -> hasUpdateAvailable
    }
}

private fun PluginPermissionDiff.hasMeaningfulChanges(): Boolean {
    return added.isNotEmpty() || removed.isNotEmpty() || changed.isNotEmpty() || riskUpgraded.isNotEmpty()
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
