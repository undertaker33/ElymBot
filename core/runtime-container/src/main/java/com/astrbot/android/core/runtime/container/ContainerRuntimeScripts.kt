package com.astrbot.android.core.runtime.container

import java.io.File

enum class ContainerRuntimeScript(val fileName: String) {
    START_NAPCAT("start_napcat.sh"),
    STOP_NAPCAT("stop_napcat.sh"),
    STATUS_NAPCAT("status_napcat.sh"),
    LOGOUT_QQ("logout_qq.sh"),
    PREPARE_TTS_ASSETS("prepare_tts_assets.sh"),
    CLEAR_TTS_ASSETS("clear_tts_assets.sh"),
    CONVERT_TENCENT_SILK("convert_tencent_silk.sh"),
}

object ContainerRuntimeScripts {
    fun command(
        filesDir: File,
        nativeLibraryDir: String,
        script: ContainerRuntimeScript,
        extraArgs: List<String> = emptyList(),
    ): CommandSpec {
        val scriptFile = File(filesDir, "runtime/scripts/${script.fileName}")
        return CommandSpec(
            executable = File("/system/bin/sh"),
            args = listOf(
                scriptFile.absolutePath,
                filesDir.absolutePath,
                nativeLibraryDir,
            ) + extraArgs,
        )
    }

    fun scriptFile(filesDir: File, script: ContainerRuntimeScript): File {
        return File(filesDir, "runtime/scripts/${script.fileName}")
    }
}
