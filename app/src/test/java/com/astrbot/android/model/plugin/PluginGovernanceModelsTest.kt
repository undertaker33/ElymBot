package com.astrbot.android.model.plugin

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginGovernanceModelsTest {

    @Test
    fun resolve_governance_snapshot_preserves_existing_semantics_and_exposes_phase6_defaults() {
        val manifest = PluginManifest(
            pluginId = "com.example.governance.compat",
            version = "2.3.4",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Governance Compat",
            description = "Compatibility coverage",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "host.fs.write",
                    title = "Write files",
                    description = "Writes plugin files",
                    riskLevel = PluginRiskLevel.HIGH,
                ),
            ),
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.REPOSITORY,
            entrySummary = "governance compat",
            riskLevel = PluginRiskLevel.MEDIUM,
        )
        val record = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(
                sourceType = PluginSourceType.REPOSITORY,
                location = "https://repo.example.com/catalog.json",
                importedAt = 10L,
            ),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
            ),
            permissionSnapshot = manifest.permissions,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = false,
                maxHostVersionSatisfied = true,
            ),
        )

        val snapshot = record.resolveGovernanceSnapshot()

        assertEquals(PluginRiskLevel.HIGH, snapshot.riskLevel)
        assertEquals(PluginTrustLevel.REPOSITORY_LISTED, snapshot.trustLevel)
        assertEquals(PluginReviewState.HOST_COMPATIBILITY_BLOCKED, snapshot.reviewState)
        assertEquals("com.example.governance.compat", snapshot.pluginId)
        assertEquals("2.3.4", snapshot.pluginVersion)
        assertEquals(2, snapshot.protocolVersion)
        assertEquals("js_quickjs", snapshot.runtimeKind)
        assertEquals("UNAVAILABLE", snapshot.runtimeSessionState)
        assertEquals(PluginRuntimeHealthStatus.UpgradeRequired, snapshot.runtimeHealth.status)
        assertEquals(PluginSuspensionState.NOT_SUSPENDED, snapshot.suspensionState)
        assertEquals("UNAVAILABLE", snapshot.bootstrapStatus.status)
        assertEquals("IDLE", snapshot.autoRecoveryState.status)
        assertEquals(
            listOf(
                PluginCapabilityGrantSnapshot(
                    permissionId = "host.fs.write",
                    title = "Write files",
                    riskLevel = PluginRiskLevel.HIGH,
                    required = true,
                    source = "install_permission_snapshot",
                ),
            ),
            snapshot.capabilityGrants,
        )
        assertEquals(null, snapshot.lastFailure)
        assertEquals(null, snapshot.lastSuccessAtEpochMillis)
        assertEquals(
            PluginWorkspaceStateSummary(
                source = "install_record",
                extractedDir = "",
                localPackagePath = "",
                hasExtractedDir = false,
                hasLocalPackage = false,
            ),
            snapshot.workspaceStateSummary,
        )
        assertEquals(
            PluginConfigStateSummary(
                source = "package_contract_snapshot",
                hasStaticSchema = false,
                hasSettingsSchema = false,
            ),
            snapshot.configStateSummary,
        )
        assertEquals(PluginRegistrationSummary(), snapshot.registrationSummary)
        assertEquals(PluginDiagnosticsSummary(), snapshot.diagnosticsSummary)
    }

    @Test
    fun runtime_log_record_lifts_phase6_structured_fields_from_metadata_without_breaking_old_callers() {
        val record = PluginRuntimeLogRecord(
            occurredAtEpochMillis = 1_000L,
            pluginId = "com.example.logs.phase6",
            pluginVersion = "1.0.0",
            trigger = PluginTriggerSource.OnCommand,
            category = PluginRuntimeLogCategory.Dispatcher,
            level = PluginRuntimeLogLevel.Info,
            code = "tool_registry_compiled",
            message = "structured metadata",
            metadata = linkedMapOf(
                "sessionInstanceId" to "session-42",
                "requestId" to "req-42",
                "stage" to "ToolRegistry",
                "handlerId" to "hdl::governance",
                "toolId" to "com.example.logs.phase6:lookup",
                "toolCallId" to "call-42",
                "outcome" to "COMPLETED",
            ),
        )

        assertEquals("session-42", record.runtimeSessionId)
        assertEquals("req-42", record.requestId)
        assertEquals("ToolRegistry", record.stage)
        assertEquals("hdl::governance", record.handlerName)
        assertEquals("com.example.logs.phase6:lookup", record.toolId)
        assertEquals("call-42", record.toolCallId)
        assertEquals("COMPLETED", record.outcome)
    }

    @Test
    fun runtime_log_record_copy_recomputes_lifted_fields_from_new_metadata_with_safe_fallback_order() {
        val original = PluginRuntimeLogRecord(
            occurredAtEpochMillis = 1_000L,
            pluginId = "com.example.logs.phase6",
            category = PluginRuntimeLogCategory.Dispatcher,
            level = PluginRuntimeLogLevel.Info,
            code = "copy_test",
            metadata = linkedMapOf(
                "runtimeSessionId" to "session-original",
                "requestId" to "req-original",
                "stage" to "Active",
                "handlerName" to "handler-original",
                "toolId" to "tool-original",
                "toolCallId" to "call-original",
                "outcome" to "COMPLETED",
            ),
        )

        val copied = original.copy(
            metadata = linkedMapOf(
                "runtimeSessionId" to "",
                "sessionInstanceId" to "session-fallback",
                "requestId" to "req-new",
                "stage" to "BootstrapRunning",
                "handlerName" to "",
                "handlerId" to "handler-fallback",
                "toolId" to "tool-new",
                "toolCallId" to "call-new",
                "outcome" to "FAILED",
            ),
        )

        assertEquals("session-original", original.runtimeSessionId)
        assertEquals("session-fallback", copied.runtimeSessionId)
        assertEquals("req-new", copied.requestId)
        assertEquals("BootstrapRunning", copied.stage)
        assertEquals("handler-fallback", copied.handlerName)
        assertEquals("tool-new", copied.toolId)
        assertEquals("call-new", copied.toolCallId)
        assertEquals("FAILED", copied.outcome)
    }
}
