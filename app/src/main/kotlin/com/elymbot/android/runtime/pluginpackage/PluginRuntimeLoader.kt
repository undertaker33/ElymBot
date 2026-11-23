package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import dalvik.system.DexClassLoader
import com.elymbot.core.contract.PluginEntrypoint
import com.elymbot.core.contract.PluginHandle
import com.elymbot.core.contract.PluginHostContext
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolRegistration
import com.elymbot.core.contract.ToolRegistrationOwner
import java.io.File
import java.io.IOException
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException

internal class PluginRuntimeLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Loads one already verified, immutable plugin archive and prepares its host-owned contributions. */
internal class PluginRuntimeLoader(context: Context) {
    private val appContext = context.applicationContext

    @Throws(PluginRuntimeLoadException::class)
    fun prepare(
        stagedPackage: StagedPluginPackage,
        installationIdentity: ToolInstallationIdentity,
    ): LoadedPluginRuntime {
        var optimizedDirectory: File? = null
        var pluginHandle: PluginHandle? = null
        try {
            if (stagedPackage.manifest.pluginId != installationIdentity.pluginId) {
                reject("Plugin identity does not match the host installation")
            }
            val pluginJar = requireReadOnlyPluginJar(stagedPackage)
            verifyDexArchive(pluginJar)
            val dataDirectory = pluginDataDirectory(installationIdentity)
            optimizedDirectory = createOptimizedDirectory(installationIdentity)

            val classLoader = DexClassLoader(
                pluginJar.absolutePath,
                optimizedDirectory.absolutePath,
                null,
                PluginEntrypoint::class.java.classLoader,
            )
            val entryClass = Class.forName(stagedPackage.manifest.entryClass, false, classLoader)
            if (entryClass.classLoader !== classLoader || !PluginEntrypoint::class.java.isAssignableFrom(entryClass)) {
                reject("Signed entry class is not provided by the plugin archive")
            }
            if (!Modifier.isPublic(entryClass.modifiers)) reject("Plugin entry class must be public")
            val constructor = entryClass.getDeclaredConstructor()
            if (!Modifier.isPublic(constructor.modifiers)) reject("Plugin entry class needs a public no-argument constructor")
            val entrypoint = constructor.newInstance() as PluginEntrypoint
            val preparedHandle = entrypoint.prepare(PluginHostContext(installationIdentity, dataDirectory))
            pluginHandle = preparedHandle

            val sources = preparedHandle.toolSources.toList()
            val sourceIds = HashSet<String>()
            for (source in sources) {
                val toolId = source.definition.id
                if (toolId.isBlank() || !sourceIds.add(toolId)) reject("Plugin returned an invalid or duplicate tool")
            }
            val registrations = sources.map { source ->
                ToolRegistration(source, ToolRegistrationOwner.Plugin(installationIdentity))
            }
            return LoadedPluginRuntime(
                manifest = stagedPackage.manifest,
                installationIdentity = installationIdentity,
                packageDirectory = stagedPackage.directory,
                registrations = registrations,
                pluginHandle = preparedHandle,
                classLoader = classLoader,
                optimizedDirectory = optimizedDirectory,
            )
        } catch (failure: CancellationException) {
            cleanupFailedCandidate(pluginHandle, optimizedDirectory, failure)
            throw failure
        } catch (failure: VirtualMachineError) {
            cleanupFailedCandidate(pluginHandle, optimizedDirectory, failure)
            throw failure
        } catch (failure: ThreadDeath) {
            cleanupFailedCandidate(pluginHandle, optimizedDirectory, failure)
            throw failure
        } catch (failure: Throwable) {
            cleanupFailedCandidate(pluginHandle, optimizedDirectory, failure)
            if (failure is PluginRuntimeLoadException) throw failure
            throw PluginRuntimeLoadException("Plugin runtime preparation failed", failure)
        }
    }

    private fun requireReadOnlyPluginJar(stagedPackage: StagedPluginPackage): File {
        val packageRoot = stagedPackage.directory.canonicalFile
        val pluginJar = File(packageRoot, "$PAYLOAD_DIRECTORY/$PLUGIN_JAR_PATH").canonicalFile
        if (!pluginJar.isFile || !pluginJar.canRead() || pluginJar.canWrite() ||
            !pluginJar.toPath().startsWith(packageRoot.toPath())
        ) {
            reject("Verified plugin archive is unavailable or writable")
        }
        return pluginJar
    }

    private fun verifyDexArchive(pluginJar: File) {
        ZipFile(pluginJar).use { archive ->
            val names = HashSet<String>()
            val enumeration: Enumeration<out ZipEntry> = archive.entries()
            var classesDex: ZipEntry? = null
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                if (!names.add(entry.name)) reject("Plugin archive contains duplicate entries")
                if (entry.name == CLASSES_DEX_ENTRY) classesDex = entry
            }
            val dex = classesDex ?: reject("Plugin archive does not contain classes.dex")
            if (dex.isDirectory || dex.size !in 1L..MAX_PLUGIN_DEX_BYTES) {
                reject("Plugin classes.dex has an invalid size")
            }
        }
    }

    private fun createOptimizedDirectory(identity: ToolInstallationIdentity): File {
        if (!PLUGIN_ID_PATTERN.matches(identity.pluginId) || !INSTANCE_ID_PATTERN.matches(identity.instanceId)) {
            reject("Host installation identity is invalid")
        }
        val codeCache = appContext.codeCacheDir.canonicalFile
        val pluginRoot = File(codeCache, OPTIMIZED_DIRECTORY_NAME)
        createPrivateDirectory(pluginRoot, codeCache)
        val pluginDirectory = File(pluginRoot, identity.pluginId)
        createPrivateDirectory(pluginDirectory, codeCache)
        val optimized = File(pluginDirectory, identity.instanceId)
        if (Files.isSymbolicLink(optimized.toPath())) reject("Plugin optimized path is invalid")
        if (optimized.exists()) {
            if (!optimized.isDirectory ||
                !optimized.canonicalFile.toPath().startsWith(pluginDirectory.canonicalFile.toPath()) ||
                !optimized.deleteRecursively()
            ) {
                reject("Stale plugin optimized code could not be removed")
            }
        }
        createPrivateDirectory(optimized, codeCache)
        return optimized.canonicalFile
    }

    private fun pluginDataDirectory(identity: ToolInstallationIdentity): File {
        if (!PLUGIN_ID_PATTERN.matches(identity.pluginId)) reject("Host plugin identity is invalid")
        val filesDirectory = appContext.filesDir.canonicalFile
        val packageRoot = File(filesDirectory, PACKAGE_ROOT_NAME)
        createPrivateDirectory(packageRoot, filesDirectory)
        val dataRoot = File(packageRoot, DATA_DIRECTORY_NAME)
        createPrivateDirectory(dataRoot, filesDirectory)
        val dataDirectory = File(dataRoot, identity.pluginId)
        createPrivateDirectory(dataDirectory, filesDirectory)
        return dataDirectory.canonicalFile
    }

    private fun createPrivateDirectory(directory: File, allowedParent: File) {
        val target = directory.absoluteFile
        if (!target.exists() && !target.mkdir() && !target.isDirectory) {
            throw IOException("Unable to create private plugin runtime directory")
        }
        if (!target.isDirectory || Files.isSymbolicLink(target.toPath()) ||
            !target.canonicalFile.toPath().startsWith(allowedParent.canonicalFile.toPath())
        ) {
            reject("Plugin runtime directory escapes private storage")
        }
    }

    private fun cleanupFailedCandidate(handle: PluginHandle?, optimizedDirectory: File?, failure: Throwable) {
        try {
            handle?.close()
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        } finally {
            optimizedDirectory?.deleteRecursively()
        }
    }

    private fun reject(message: String): Nothing = throw PluginRuntimeLoadException(message)

    internal class LoadedPluginRuntime internal constructor(
        val manifest: PluginPackageManifest,
        val installationIdentity: ToolInstallationIdentity,
        val packageDirectory: File,
        val registrations: List<ToolRegistration>,
        internal val pluginHandle: PluginHandle,
        internal val classLoader: ClassLoader,
        private val optimizedDirectory: File,
    ) {
        private var isClosed = false

        @Synchronized
        fun close() {
            if (isClosed) return
            isClosed = true
            try {
                pluginHandle.close()
            } finally {
                optimizedDirectory.deleteRecursively()
            }
        }
    }

    private companion object {
        const val PAYLOAD_DIRECTORY = "payload"
        const val PLUGIN_JAR_PATH = "plugin.jar"
        const val CLASSES_DEX_ENTRY = "classes.dex"
        const val OPTIMIZED_DIRECTORY_NAME = "plugin-runtime"
        const val PACKAGE_ROOT_NAME = "plugin-packages"
        const val DATA_DIRECTORY_NAME = "data"
        const val MAX_PLUGIN_DEX_BYTES = 64 * 1024 * 1024L
        val PLUGIN_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val INSTANCE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
    }
}

internal typealias LoadedPluginRuntime = PluginRuntimeLoader.LoadedPluginRuntime
