package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.common.logging.RuntimeLogger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

data class CommandSpec(
    val executable: File,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDir: File = File("/"),
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val maxStdoutBytes: Int = DEFAULT_OUTPUT_LIMIT_BYTES,
    val maxStderrBytes: Int = DEFAULT_OUTPUT_LIMIT_BYTES,
    val redactionLiterals: List<String> = emptyList(),
) {
    init {
        require(executable.path.isNotBlank()) { "Command executable is blank" }
        require(timeoutMs > 0L) { "Command timeout must be positive" }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L
        const val DEFAULT_OUTPUT_LIMIT_BYTES = 128 * 1024
    }
}

data class CommandExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

interface CommandRunner {
    fun execute(spec: CommandSpec): CommandExecutionResult
}

@Singleton
class DefaultCommandRunner @Inject constructor(
    private val runtimeLogger: RuntimeLogger,
) : CommandRunner {
    override fun execute(spec: CommandSpec): CommandExecutionResult {
        val commandPreview = buildCommandPreview(spec)
        runtimeLogger.append("Command exec: $commandPreview")
        return try {
            val processBuilder = ProcessBuilder(listOf(spec.executable.absolutePath) + spec.args)
                .directory(spec.workingDir)
                .redirectErrorStream(false)
            processBuilder.environment().putAll(spec.env)
            val process = processBuilder.start()

            var stdout = ""
            var stderr = ""
            val stdoutThread = thread(name = "astrbot-cmd-stdout") {
                stdout = process.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText().limitBytes(spec.maxStdoutBytes).trim()
                }
            }
            val stderrThread = thread(name = "astrbot-cmd-stderr") {
                stderr = process.errorStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText().limitBytes(spec.maxStderrBytes).trim()
                }
            }

            val completed = process.waitFor(spec.timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
            }
            stdoutThread.join()
            stderrThread.join()
            val exitCode = if (completed) process.exitValue() else -1
            val normalizedStderr = if (completed) stderr else stderr.ifBlank { "Command timed out after ${spec.timeoutMs}ms" }
            runtimeLogger.append(
                "Command exit: code=$exitCode stdout=${stdout.redacted(spec).singleLine(120)} stderr=${normalizedStderr.redacted(spec).singleLine(120)}",
            )
            CommandExecutionResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = normalizedStderr,
            )
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            runtimeLogger.append("Command exception: ${message.redacted(spec)}")
            CommandExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = message,
            )
        }
    }

    private fun buildCommandPreview(spec: CommandSpec): String {
        val parts = listOf(spec.executable.absolutePath) + spec.args
        return parts.joinToString(" ") { part -> part.redacted(spec).singleLine(80) }.singleLine(180)
    }

    private fun String.limitBytes(maxBytes: Int): String {
        if (toByteArray().size <= maxBytes) return this
        return toByteArray().copyOf(maxBytes).toString(Charsets.UTF_8) + "\n...[truncated]"
    }

    private fun String.redacted(spec: CommandSpec): String {
        return spec.redactionLiterals
            .filter { it.isNotBlank() }
            .fold(this) { acc, secret -> acc.replace(secret, "***") }
    }

    private fun String.singleLine(limit: Int): String {
        if (isBlank()) return "-"
        val normalized = replace('\n', ' ').replace('\r', ' ').trim()
        return if (normalized.length <= limit) normalized else normalized.take(limit) + "..."
    }
}
