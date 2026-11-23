package com.elymbot.core.contract

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginContractsTest {
    @Test
    fun hostSuppliesInstallationIdentityAndWrapsPluginToolContributions() {
        val installation = ToolInstallationIdentity("official-tool", "instance-22")
        val dataDirectory = Files.createTempDirectory("elymbot-plugin-contract-").toFile()
        val source = TestToolSource()
        var receivedContext: PluginHostContext? = null
        var closed = false
        val entrypoint = PluginEntrypoint { context ->
            receivedContext = context
            object : PluginHandle {
                override val toolSources = listOf(source)

                override fun close() {
                    closed = true
                }
            }
        }

        try {
            val handle = entrypoint.prepare(PluginHostContext(installation, dataDirectory))
            val registration = handle.toolSources.map { source ->
                ToolRegistration(source, ToolRegistrationOwner.Plugin(installation))
            }.single()
            handle.close()

            assertEquals(installation, receivedContext?.installationIdentity)
            assertEquals(dataDirectory, receivedContext?.dataDirectory)
            assertEquals(source, handle.toolSources.single())
            assertEquals(ToolRegistrationOwner.Plugin(installation), registration.owner)
            assertTrue(closed)
        } finally {
            dataDirectory.deleteRecursively()
        }
    }

    private class TestToolSource : ToolSource {
        override val definition = ToolDefinition(
            id = "test.plugin-tool",
            name = "plugin-tool",
            description = "Synthetic plugin tool",
            parametersJsonSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
            scopes = setOf(ToolScope.MAIN_SESSION),
        )

        override suspend fun execute(
            call: ModelToolCall,
            context: ToolExecutionContext,
            onProgress: suspend (ToolProgress) -> Unit,
        ) = ToolExecutionResult(call.callId, ToolResultStatus.SUCCESS, "ok")
    }
}
