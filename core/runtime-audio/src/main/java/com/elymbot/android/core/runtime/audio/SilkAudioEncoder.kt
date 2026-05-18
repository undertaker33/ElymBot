package com.elymbot.android.core.runtime.audio

import android.content.Context
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.container.CommandRunner
import com.elymbot.android.core.runtime.container.ContainerRuntimeInstallerPort
import com.elymbot.android.core.runtime.container.ContainerRuntimeScript
import com.elymbot.android.core.runtime.container.ContainerRuntimeScripts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

fun interface SilkAudioEncoder {
    fun encode(inputFile: File): File
}

@Singleton
internal class TencentSilkEncoder @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val containerRuntimeInstaller: ContainerRuntimeInstallerPort,
    private val commandRunner: CommandRunner,
    private val runtimeLogger: RuntimeLogger,
) : SilkAudioEncoder {
    override fun encode(inputFile: File): File {
        runBlocking {
            containerRuntimeInstaller.ensureInstalled()
        }

        val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir
        val outputFile = File(
            inputFile.parentFile,
            inputFile.nameWithoutExtension + ".silk",
        )

        runtimeLogger.append("QQ TTS silk conversion started: ${inputFile.name} -> ${outputFile.name}")
        val result = commandRunner.execute(
            ContainerRuntimeScripts.command(
                filesDir = appContext.filesDir,
                nativeLibraryDir = nativeLibraryDir,
                script = ContainerRuntimeScript.CONVERT_TENCENT_SILK,
                extraArgs = listOf(inputFile.absolutePath, outputFile.absolutePath),
            ),
        )
        if (result.exitCode != 0 || !outputFile.exists() || outputFile.length() <= 0L) {
            if (result.stderr.contains("tts assets are not prepared", ignoreCase = true)) {
                runtimeLogger.append("QQ TTS silk conversion skipped: assets not installed")
                throw IllegalStateException("TTS conversion assets are not installed. Download them in Asset Management.")
            }
            throw IllegalStateException(
                "Tencent silk conversion failed: ${result.stderr.ifBlank { result.stdout }.ifBlank { "unknown error" }}",
            )
        }
        runtimeLogger.append("QQ TTS silk conversion finished: ${outputFile.absolutePath}")
        return outputFile
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SilkAudioEncoderModule {
    @Binds
    @Singleton
    abstract fun bindSilkAudioEncoder(
        encoder: TencentSilkEncoder,
    ): SilkAudioEncoder
}
