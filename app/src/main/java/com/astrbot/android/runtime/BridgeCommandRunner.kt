package com.astrbot.android.runtime

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class CommandExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

object BridgeCommandRunner {
    fun execute(command: String): CommandExecutionResult {
        if (command.isBlank()) {
            RuntimeLogRepository.append("Command skipped: blank command")
            return CommandExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = "Command is blank",
            )
        }

        RuntimeLogRepository.append("Command exec: ${command.singleLine(180)}")
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(File("/"))
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText().trim()
            }
            val stderr = process.errorStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText().trim()
            }
            val exitCode = process.waitFor()
            RuntimeLogRepository.append(
                "Command exit: code=$exitCode stdout=${stdout.singleLine(120)} stderr=${stderr.singleLine(120)}",
            )
            CommandExecutionResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
            )
        } catch (error: Exception) {
            RuntimeLogRepository.append(
                "Command exception: ${error.message ?: error.javaClass.simpleName}",
            )
            CommandExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun String.singleLine(limit: Int): String {
        if (isBlank()) return "-"
        val normalized = replace('\n', ' ').replace('\r', ' ').trim()
        return if (normalized.length <= limit) normalized else normalized.take(limit) + "..."
    }
}
