package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.ErrorResult
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginExecutionStage
import com.elymbot.android.model.plugin.PluginExecutionResult
import com.elymbot.android.model.plugin.PluginInstallState
import com.elymbot.android.model.plugin.PluginRuntimeLogCategory
import com.elymbot.android.model.plugin.PluginRuntimeLogLevel
import com.elymbot.android.model.plugin.PluginRuntimeLogRecord
import com.elymbot.android.model.plugin.PluginTriggerSource

class PluginExecutionEngine(
    private val dispatcher: PluginRuntimeDispatcher,
    private val failureGuard: PluginFailureGuard,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scheduler: PluginRuntimeScheduler = dispatcher.scheduler,
    private val policyResolver: (PluginRuntimePlugin, PluginTriggerSource) -> PluginSchedulePolicy = dispatcher.policyResolver,
    private val resultMerger: PluginExecutionResultMerger = PluginExecutionResultMerger(
        logBus = dispatcher.logBus,
        clock = clock,
    ),
    private val logBus: PluginRuntimeLogBus = dispatcher.logBus,
) {
    fun execute(
        plugin: PluginRuntimePlugin,
        context: PluginExecutionContext,
    ): PluginExecutionOutcome {
        val normalizedContext = normalizeContext(plugin = plugin, context = context)
        val startedAt = clock()
        val policy = policyResolver(plugin, normalizedContext.trigger)
        return try {
            val result = plugin.handler.execute(normalizedContext)
            val durationMillis = (clock() - startedAt).coerceAtLeast(0L)
            scheduler.recordSuccess(
                pluginId = plugin.pluginId,
                trigger = normalizedContext.trigger,
                policy = policy,
            )
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = startedAt,
                    pluginId = plugin.pluginId,
                    pluginVersion = plugin.pluginVersion,
                    trigger = normalizedContext.trigger,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Info,
                    code = "execution_succeeded",
                    message = "Plugin execution succeeded.",
                    succeeded = true,
                    durationMillis = durationMillis,
                    resultType = result.runtimeResultTypeWire(),
                ),
            )
            PluginExecutionOutcome(
                pluginId = plugin.pluginId,
                pluginVersion = plugin.pluginVersion,
                installState = plugin.installState,
                context = normalizedContext,
                result = result,
                succeeded = true,
                failureSnapshot = failureGuard.recordSuccess(
                    pluginId = plugin.pluginId,
                    trigger = normalizedContext.trigger,
                ),
            )
        } catch (error: Throwable) {
            val durationMillis = (clock() - startedAt).coerceAtLeast(0L)
            scheduler.recordFailure(
                pluginId = plugin.pluginId,
                trigger = normalizedContext.trigger,
                policy = policy,
            )
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = startedAt,
                    pluginId = plugin.pluginId,
                    pluginVersion = plugin.pluginVersion,
                    trigger = normalizedContext.trigger,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Error,
                    code = "execution_failed",
                    message = error.message ?: "Plugin execution failed.",
                    succeeded = false,
                    durationMillis = durationMillis,
                    resultType = "error",
                ),
            )
            PluginExecutionOutcome(
                pluginId = plugin.pluginId,
                pluginVersion = plugin.pluginVersion,
                installState = plugin.installState,
                context = normalizedContext,
                result = ErrorResult(
                    message = error.message ?: "Plugin execution failed",
                    code = "plugin_execution_failed",
                    recoverable = true,
                ),
                succeeded = false,
                failureSnapshot = failureGuard.recordFailure(
                    pluginId = plugin.pluginId,
                    trigger = normalizedContext.trigger,
                    errorSummary = error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName,
                ),
                error = error,
            )
        }
    }

    fun executeBatch(
        trigger: PluginTriggerSource,
        plugins: List<PluginRuntimePlugin>,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        val plan = dispatcher.dispatch(
            trigger = trigger,
            plugins = plugins,
        )
        val outcomes = plan.executable.map { plugin ->
            val batchContext = contextFactory(plugin).copy(trigger = trigger)
            execute(
                plugin = plugin,
                context = batchContext,
            )
        }
        return PluginExecutionBatchResult(
            trigger = trigger,
            outcomes = outcomes,
            skipped = plan.skipped,
            merged = resultMerger.mergeLegacy(
                trigger = trigger,
                outcomes = outcomes,
            ),
        )
    }

    private fun normalizeContext(
        plugin: PluginRuntimePlugin,
        context: PluginExecutionContext,
    ): PluginExecutionContext {
        return context.copy(
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
        )
    }
}
