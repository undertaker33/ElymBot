package com.elymbot.android.core.runtime.audio

import android.content.Context
import com.elymbot.android.core.common.logging.RuntimeLogger
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

interface SherpaOnnxAssetService {
    fun frameworkState(context: Context): AudioAssetSubState

    fun sttState(context: Context): AudioAssetSubState

    fun ttsState(context: Context): AudioTtsAssetState

    fun frameworkDir(context: Context): File

    fun sttDir(context: Context): File

    fun kokoroDir(context: Context): File

    fun sttArchiveFile(context: Context): File

    fun kokoroArchiveFile(context: Context): File

    fun ensureFrameworkActivated(context: Context)

    fun clearFramework(context: Context)

    fun downloadSttAssets(context: Context)

    fun clearSttAssets(context: Context)

    fun downloadKokoroAssets(context: Context)

    fun clearKokoroAssets(context: Context)

    fun clearDeprecatedTtsAssets(context: Context)

    fun installSttAssetsFromArchive(
        context: Context,
        archiveFile: File = sttArchiveFile(context),
    )

    fun installKokoroAssetsFromArchive(
        context: Context,
        archiveFile: File = kokoroArchiveFile(context),
    )
}

class DefaultSherpaOnnxAssetService @Inject constructor(
    private val runtimeLogger: RuntimeLogger,
) : SherpaOnnxAssetService {
    override fun frameworkState(context: Context): AudioAssetSubState {
        val installed = frameworkMarkerFile(context).exists()
        return AudioAssetSubState(
            installed = installed,
            details = if (installed) {
                "Sherpa ONNX Android runtime is bundled in the app. The on-device framework has been activated and is ready for local STT/TTS assets."
            } else {
                "Activate the bundled Sherpa ONNX Android runtime before downloading local STT or TTS model assets."
            },
        )
    }

    override fun sttState(context: Context): AudioAssetSubState {
        val installed = sttModelFile(context).exists() && sttTokensFile(context).exists()
        return AudioAssetSubState(
            installed = installed,
            details = if (installed) {
                "Offline Paraformer STT assets are ready."
            } else {
                "Offline Paraformer STT assets are not downloaded."
            },
        )
    }

    override fun ttsState(context: Context): AudioTtsAssetState {
        val kokoroReady = listOf(
            kokoroModelFile(context),
            kokoroVoicesFile(context),
            File(kokoroDir(context), "lexicon-us-en.txt"),
            File(kokoroDir(context), "lexicon-zh.txt"),
            File(kokoroDir(context), "espeak-ng-data"),
            File(kokoroDir(context), "dict"),
            File(kokoroDir(context), "phone-zh.fst"),
            File(kokoroDir(context), "date-zh.fst"),
            File(kokoroDir(context), "number-zh.fst"),
        ).all { it.exists() }
        return AudioTtsAssetState(
            framework = frameworkState(context),
            kokoro = AudioAssetSubState(
                installed = kokoroReady,
                details = if (kokoroReady) {
                    "Kokoro local TTS assets are ready."
                } else {
                    "Kokoro local TTS assets are missing or incomplete."
                },
            ),
        )
    }

    override fun frameworkDir(context: Context): File = File(context.filesDir, FRAMEWORK_ROOT)

    override fun sttDir(context: Context): File = File(frameworkDir(context), "stt")

    override fun kokoroDir(context: Context): File = File(frameworkDir(context), "tts/kokoro")

    override fun sttArchiveFile(context: Context): File = File(
        File(context.filesDir, TEMP_DIR),
        "sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2",
    )

    override fun kokoroArchiveFile(context: Context): File = File(
        File(context.filesDir, TEMP_DIR),
        "kokoro-int8-multi-lang-v1_1.tar.bz2",
    )

    override fun ensureFrameworkActivated(context: Context) {
        val frameworkDir = frameworkDir(context)
        if (!frameworkDir.exists()) {
            frameworkDir.mkdirs()
        }
        frameworkMarkerFile(context).writeText(FRAMEWORK_VERSION)
        clearDeprecatedTtsAssets(context)
        runtimeLogger.append("Sherpa ONNX framework activated: version=$FRAMEWORK_VERSION")
    }

    override fun clearFramework(context: Context) {
        frameworkDir(context).deleteRecursively()
        runtimeLogger.append("Sherpa ONNX framework cleared")
    }

    override fun downloadSttAssets(context: Context) {
        require(frameworkState(context).installed) {
            "Download the Sherpa ONNX framework asset first."
        }
        downloadAndExtract(
            url = STT_URL,
            archiveFile = sttArchiveFile(context),
            targetDir = sttDir(context),
        )
    }

    override fun clearSttAssets(context: Context) {
        sttDir(context).deleteRecursively()
        runtimeLogger.append("Sherpa ONNX STT assets cleared")
    }

    override fun downloadKokoroAssets(context: Context) {
        require(frameworkState(context).installed) {
            "Download the Sherpa ONNX framework asset first."
        }
        downloadAndExtract(
            url = KOKORO_URL,
            archiveFile = kokoroArchiveFile(context),
            targetDir = kokoroDir(context),
        )
    }

    override fun clearKokoroAssets(context: Context) {
        kokoroDir(context).deleteRecursively()
        runtimeLogger.append("Sherpa ONNX kokoro assets cleared")
    }

    override fun clearDeprecatedTtsAssets(context: Context) {
        val deprecatedDirs = deprecatedTtsDirNames.map { dirName ->
            File(frameworkDir(context), "tts/$dirName")
        }
        deprecatedDirs.forEach { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
                runtimeLogger.append("Sherpa ONNX deprecated TTS assets cleared: ${dir.absolutePath}")
            }
        }
    }

    override fun installSttAssetsFromArchive(
        context: Context,
        archiveFile: File,
    ) {
        require(frameworkState(context).installed) {
            "Download the Sherpa ONNX framework asset first."
        }
        extractArchiveToTarget(
            archiveFile = archiveFile,
            targetDir = sttDir(context),
        )
    }

    override fun installKokoroAssetsFromArchive(
        context: Context,
        archiveFile: File,
    ) {
        require(frameworkState(context).installed) {
            "Download the Sherpa ONNX framework asset first."
        }
        extractArchiveToTarget(
            archiveFile = archiveFile,
            targetDir = kokoroDir(context),
        )
    }

    private fun frameworkMarkerFile(context: Context): File = File(frameworkDir(context), FRAMEWORK_MARKER)

    private fun sttModelFile(context: Context): File = File(sttDir(context), "model.int8.onnx")

    private fun sttTokensFile(context: Context): File = File(sttDir(context), "tokens.txt")

    private fun kokoroModelFile(context: Context): File = File(kokoroDir(context), "model.int8.onnx")

    private fun kokoroVoicesFile(context: Context): File = File(kokoroDir(context), "voices.bin")

    private fun downloadAndExtract(
        url: String,
        archiveFile: File,
        targetDir: File,
    ) {
        downloadFile(url = url, outputFile = archiveFile)
        extractArchiveToTarget(archiveFile = archiveFile, targetDir = targetDir)
    }

    private fun extractArchiveToTarget(
        archiveFile: File,
        targetDir: File,
    ) {
        val tempDir = archiveFile.parentFile ?: targetDir.parentFile ?: targetDir
        tempDir.mkdirs()
        val stagingDir = File(tempDir, "${targetDir.name}.staging")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        extractTarBz2(archiveFile, stagingDir)
        targetDir.parentFile?.mkdirs()
        targetDir.deleteRecursively()
        stagingDir.copyRecursively(targetDir, overwrite = true)
        stagingDir.deleteRecursively()
        runtimeLogger.append("Sherpa ONNX asset extracted: target=${targetDir.absolutePath}")
    }

    private fun downloadFile(
        url: String,
        outputFile: File,
    ) {
        runtimeLogger.append("Sherpa ONNX asset download started: $url")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code while downloading Sherpa ONNX assets.")
            }
            outputFile.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
        runtimeLogger.append("Sherpa ONNX asset download finished: ${outputFile.absolutePath}")
    }

    private fun extractTarBz2(
        archiveFile: File,
        destinationDir: File,
    ) {
        TarArchiveInputStream(BZip2CompressorInputStream(archiveFile.inputStream().buffered())).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.name.isBlank()) continue
                val relativePath = normalizeTarEntry(entry)
                if (relativePath.isBlank()) continue
                val outputFile = resolveDestination(destinationDir, relativePath)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                    setExecutableIfNeeded(outputFile, entry)
                }
            }
        }
    }

    private fun normalizeTarEntry(entry: TarArchiveEntry): String {
        val normalized = entry.name.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return ""
        val segments = normalized.split('/')
        return if (segments.size <= 1) {
            ""
        } else {
            segments.drop(1).joinToString(File.separator)
        }
    }

    private fun resolveDestination(
        destinationDir: File,
        relativePath: String,
    ): File {
        val resolved = File(destinationDir, relativePath)
        val normalizedBase = destinationDir.toPath().normalize()
        val normalizedResolved = resolved.toPath().normalize()
        check(normalizedResolved.startsWith(normalizedBase)) {
            "Invalid archive entry: $relativePath"
        }
        return resolved
    }

    private fun setExecutableIfNeeded(
        file: File,
        entry: TarArchiveEntry,
    ) {
        val mode = entry.mode
        if (mode and 0b001_001_001 != 0) {
            runCatching { file.setExecutable(true, false) }
        }
    }

    private companion object {
        private const val FRAMEWORK_ROOT = "runtime/sherpa-onnx"
        private const val FRAMEWORK_VERSION = "1.12.30"
        private const val FRAMEWORK_MARKER = ".framework-ready"
        private const val TEMP_DIR = "runtime/downloads"
        private const val STT_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
        private const val KOKORO_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"
        private val deprecatedTtsDirNames = listOf("matcha", "melo", "fanchen-c", "zh-ll")
    }
}
