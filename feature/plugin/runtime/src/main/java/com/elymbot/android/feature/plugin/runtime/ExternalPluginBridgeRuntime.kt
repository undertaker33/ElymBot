package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.ExternalPluginExecutionContract
import com.elymbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginExecutionProtocolJson
import com.elymbot.android.model.plugin.PluginExecutionResult
import org.json.JSONObject

class ExternalPluginBridgeRuntime(
    private val scriptExecutor: ExternalPluginScriptExecutor = QuickJsExternalPluginScriptExecutor(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    fun execute(
        binding: ExternalPluginRuntimeBinding,
        context: PluginExecutionContext,
    ): PluginExecutionResult {
        require(binding.isReady) { "External plugin binding is not ready: ${binding.status.name}" }
        val contract = binding.contract ?: error("External plugin binding is missing contract.")
        val requestBody = PluginExecutionProtocolJson
            .encodeExecutionContext(context)
            .toString()
        return executeQuickJs(
            binding = binding,
            contract = contract,
            requestBody = requestBody,
        )
    }

    private fun executeQuickJs(
        binding: ExternalPluginRuntimeBinding,
        contract: ExternalPluginExecutionContract,
        requestBody: String,
    ): PluginExecutionResult {
        val stdout = scriptExecutor.execute(
            ExternalPluginScriptExecutionRequest(
                pluginId = binding.installRecord.pluginId,
                scriptAbsolutePath = binding.entryAbsolutePath,
                entrySymbol = contract.entryPoint.entrySymbol,
                contextJson = requestBody,
                pluginRootDirectory = binding.installRecord.extractedDir,
                timeoutMs = timeoutMs,
            ),
        ).trim()
        if (stdout.isBlank()) {
            throw IllegalStateException(
                "External plugin returned an empty response: ${binding.installRecord.pluginId}",
            )
        }
        return try {
            PluginExecutionProtocolJson.decodeResult(JSONObject(stdout))
        } catch (error: Exception) {
            throw IllegalStateException(
                "External plugin returned invalid JSON: ${binding.installRecord.pluginId}",
                error,
            )
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
