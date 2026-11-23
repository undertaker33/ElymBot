package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import com.elymbot.core.contract.PluginHandle
import com.elymbot.core.contract.ToolInstallationIdentity
import com.elymbot.core.contract.ToolRegistration
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class PluginPackageReference(
    val pluginId: String,
    val publisherId: String,
    val version: String,
)

internal enum class PluginInstanceAvailability {
    RESTORING,
    ACTIVE,
    UNAVAILABLE,
    INVALIDATED,
}

internal data class PluginRuntimeStatus(
    val pluginId: String,
    val publisherId: String,
    val version: String,
    val enabled: Boolean,
    val installationIdentity: ToolInstallationIdentity,
    val availability: PluginInstanceAvailability,
    val cleanupPending: Boolean = false,
)

/** Host-owned install, activation and revocation boundary for verified plugin runtimes. */
internal class PluginRuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val packageStager = PluginPackageStager(appContext)
    private val runtimeLoader = PluginRuntimeLoader(appContext)
    private val stateLock = Any()
    private val restoreLock = Mutex()
    private val operationLocks = ConcurrentHashMap<String, Mutex>()
    private val installed = LinkedHashMap<String, InstalledPlugin>()
    private val active = LinkedHashMap<String, LoadedPluginRuntime>()

    @Volatile
    var isRestorationComplete: Boolean = false
        private set

    /** Loads the persisted active instances before App delivery recovery or Agent requests begin. */
    suspend fun restore(
        resolveExpectation: suspend (PluginPackageReference) -> PluginPackageExpectation?,
    ): List<PluginRuntimeStatus> = restoreLock.withLock {
        if (isRestorationComplete) return@withLock statusSnapshot()

        val persisted = readPersistedState()
        val restoredInstalled = persisted.associateByTo(LinkedHashMap()) { it.pluginId }
        val restoredActive = LinkedHashMap<String, LoadedPluginRuntime>()
        var committed = false
        try {
            for (record in persisted) {
                if (!record.enabled) continue
                currentCoroutineContext().ensureActive()
                val expectation = try {
                    resolveExpectation(PluginPackageReference(record.pluginId, record.publisherId, record.version))
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    null
                } ?: continue
                if (!expectationMatchesRecord(expectation, record)) continue

                try {
                    val verified = packageStager.verifyInstalledPackage(record.packageDirectory, expectation)
                    val runtime = loadCandidate(verified, record.installationIdentity)
                    restoredInstalled[record.pluginId] = record.copy(
                        expectation = expectation,
                        manifest = verified.manifest,
                    )
                    restoredActive[record.pluginId] = runtime
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    // Persisted identity remains current but unavailable until an explicit repair/update.
                }
            }
            currentCoroutineContext().ensureActive()
            synchronized(stateLock) {
                installed.clear()
                installed.putAll(restoredInstalled)
                active.clear()
                active.putAll(restoredActive)
                isRestorationComplete = true
            }
            committed = true
            restoredActive.clear()
            statusSnapshot()
        } finally {
            if (!committed) {
                for (runtime in restoredActive.values) closeUncommittedRuntime(runtime)
            }
        }
    }

    /** First installation enables automatically; updates preserve the prior enabled choice. */
    suspend fun install(
        packageFile: File,
        expectation: PluginPackageExpectation,
    ): PluginRuntimeStatus {
        requireRestored()
        val pluginId = expectation.pluginId
        require(isPluginId(pluginId))
        return operationLock(pluginId).withLock {
            val previous = synchronized(stateLock) { installed[pluginId] }
            val shouldEnable = previous?.enabled ?: true
            val staged = packageStager.stage(packageFile, expectation)
            val packageId = UUID.randomUUID().toString()
            val instanceId = UUID.randomUUID().toString()
            val identity = ToolInstallationIdentity(pluginId, instanceId)
            var destination: File? = null
            var candidateRuntime: LoadedPluginRuntime? = null
            var committed = false
            try {
                val packageDirectory = installedPackageDirectory(pluginId, packageId)
                destination = packageDirectory
                moveStagedPackage(staged.directory, packageDirectory)
                val installedPackage = staged.copy(directory = packageDirectory)
                if (shouldEnable) {
                    try {
                        candidateRuntime = loadCandidate(installedPackage, identity)
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        if (previous != null) throw failure
                        // A valid first release stays installed and can be retried after prepare fails.
                    }
                }
                currentCoroutineContext().ensureActive()

                val next = InstalledPlugin(
                    pluginId = pluginId,
                    publisherId = staged.manifest.publisherId,
                    version = staged.manifest.version,
                    packageId = packageId,
                    installationIdentity = identity,
                    packageDirectory = packageDirectory,
                    enabled = shouldEnable,
                    expectation = expectation,
                    manifest = staged.manifest,
                )
                val readyRuntime = candidateRuntime
                val previousRuntime = withContext(NonCancellable) {
                    persistState(next)
                    val oldRuntime = synchronized(stateLock) {
                        val previousRuntime = active[pluginId]
                        installed[pluginId] = next
                        if (readyRuntime != null) {
                            active[pluginId] = readyRuntime
                        } else {
                            active.remove(pluginId)
                        }
                        previousRuntime
                    }
                    candidateRuntime = null
                    committed = true
                    oldRuntime
                }

                val cleanupPending = retirePrevious(previousRuntime, previous?.packageDirectory)
                val final = if (cleanupPending) next.copy(cleanupPending = true) else next
                if (cleanupPending) synchronized(stateLock) { installed[pluginId] = final }
                status(final)
            } finally {
                if (!committed) {
                    candidateRuntime?.let { closeUncommittedRuntime(it) }
                    withContext(NonCancellable + Dispatchers.IO) {
                        destination?.deleteRecursively()
                        staged.directory.deleteRecursively()
                    }
                }
            }
        }
    }

    /** Re-enabling an explicitly disabled plugin creates a fresh host installation identity. */
    suspend fun enable(
        pluginId: String,
        expectation: PluginPackageExpectation,
    ): PluginRuntimeStatus {
        requireRestored()
        require(isPluginId(pluginId) && expectation.pluginId == pluginId)
        return operationLock(pluginId).withLock {
            val previous = synchronized(stateLock) { installed[pluginId] }
                ?: throw PluginRuntimeManagerException("Plugin is not installed")
            if (previous.enabled && synchronized(stateLock) { active[pluginId]?.installationIdentity == previous.installationIdentity }) {
                return@withLock status(previous)
            }
            if (!expectationMatchesRecord(expectation, previous)) {
                throw PluginRuntimeManagerException("Trusted release does not match the installed plugin")
            }

            val verified = packageStager.verifyInstalledPackage(previous.packageDirectory, expectation)
            val identity = if (previous.enabled) previous.installationIdentity else {
                ToolInstallationIdentity(pluginId, UUID.randomUUID().toString())
            }
            var candidateRuntime: LoadedPluginRuntime? = null
            var committed = false
            try {
                val readyRuntime = loadCandidate(verified, identity)
                candidateRuntime = readyRuntime
                currentCoroutineContext().ensureActive()
                val next = previous.copy(
                    installationIdentity = identity,
                    enabled = true,
                    expectation = expectation,
                    manifest = verified.manifest,
                    cleanupPending = false,
                )
                val previousRuntime = withContext(NonCancellable) {
                    if (identity != previous.installationIdentity || !previous.enabled) persistState(next)
                    val oldRuntime = synchronized(stateLock) {
                        val previousRuntime = active[pluginId]
                        installed[pluginId] = next
                        active[pluginId] = readyRuntime
                        previousRuntime
                    }
                    candidateRuntime = null
                    committed = true
                    oldRuntime
                }
                val cleanupPending = retirePrevious(previousRuntime, null)
                val final = if (cleanupPending) next.copy(cleanupPending = true) else next
                if (cleanupPending) synchronized(stateLock) { installed[pluginId] = final }
                status(final)
            } finally {
                if (!committed) candidateRuntime?.let { closeUncommittedRuntime(it) }
            }
        }
    }

    /** Removes authority before closing the runtime; the verified package and data stay installed. */
    suspend fun disable(pluginId: String): PluginRuntimeStatus {
        requireRestored()
        require(isPluginId(pluginId))
        return operationLock(pluginId).withLock {
            val previous = synchronized(stateLock) { installed[pluginId] }
                ?: throw PluginRuntimeManagerException("Plugin is not installed")
            if (!previous.enabled) return@withLock status(previous)

            val disabled = previous.copy(enabled = false, cleanupPending = false)
            currentCoroutineContext().ensureActive()
            val previousRuntime = withContext(NonCancellable) {
                persistState(disabled)
                synchronized(stateLock) {
                    installed[pluginId] = disabled
                    active.remove(pluginId)
                }
            }
            val cleanupPending = retirePrevious(previousRuntime, null)
            val final = if (cleanupPending) disabled.copy(cleanupPending = true) else disabled
            if (cleanupPending) synchronized(stateLock) { installed[pluginId] = final }
            status(final)
        }
    }

    /** Removes installation identity; callers choose whether plugin-owned business data is retained. */
    suspend fun uninstall(pluginId: String, deleteData: Boolean): Boolean {
        requireRestored()
        require(isPluginId(pluginId))
        return operationLock(pluginId).withLock {
            val previous = synchronized(stateLock) { installed[pluginId] } ?: return@withLock true
            currentCoroutineContext().ensureActive()
            val previousRuntime = withContext(NonCancellable) {
                deletePersistedState(pluginId)
                synchronized(stateLock) {
                    installed.remove(pluginId)
                    active.remove(pluginId)
                }
            }
            var cleanupPending = retirePrevious(previousRuntime, previous.packageDirectory)
            if (deleteData) {
                val deleted = withContext(NonCancellable + Dispatchers.IO) {
                    val dataDirectory = existingPluginDataDirectory(pluginId)
                    dataDirectory == null || dataDirectory.deleteRecursively()
                }
                cleanupPending = cleanupPending || !deleted
            }
            !cleanupPending
        }
    }

    /** A fresh host-owned snapshot for the next Agent request. */
    fun activeRegistrations(): List<ToolRegistration> = synchronized(stateLock) {
        if (!isRestorationComplete) return@synchronized emptyList()
        active.values.flatMap { it.registrations }.toList()
    }

    fun isActive(identity: ToolInstallationIdentity): Boolean = synchronized(stateLock) {
        isRestorationComplete && active[identity.pluginId]?.installationIdentity == identity
    }

    fun instanceAvailability(identity: ToolInstallationIdentity): PluginInstanceAvailability = synchronized(stateLock) {
        if (!isRestorationComplete) return@synchronized PluginInstanceAvailability.RESTORING
        val record = installed[identity.pluginId]
        when {
            record == null || !record.enabled || record.installationIdentity != identity ->
                PluginInstanceAvailability.INVALIDATED
            active[identity.pluginId]?.installationIdentity == identity -> PluginInstanceAvailability.ACTIVE
            else -> PluginInstanceAvailability.UNAVAILABLE
        }
    }

    fun statusSnapshot(): List<PluginRuntimeStatus> = synchronized(stateLock) {
        installed.values.map(::statusLocked)
    }

    /** Serializes a feature-specific action against disable, replacement and uninstall. */
    suspend fun <T : PluginHandle, Result> withActivePluginHandle(
        pluginId: String,
        installationIdentity: ToolInstallationIdentity,
        handleType: Class<T>,
        action: suspend (T) -> Result,
    ): Result? {
        if (!isRestorationComplete || pluginId != installationIdentity.pluginId) return null
        return operationLock(pluginId).withLock {
            val runtime = synchronized(stateLock) {
                active[pluginId]?.takeIf { it.installationIdentity == installationIdentity }
            } ?: return@withLock null
            if (!handleType.isInstance(runtime.pluginHandle)) return@withLock null
            val handle = handleType.cast(runtime.pluginHandle) ?: return@withLock null
            action(handle)
        }
    }

    private suspend fun loadCandidate(
        verifiedPackage: StagedPluginPackage,
        identity: ToolInstallationIdentity,
    ): LoadedPluginRuntime {
        val pending = AtomicReference<LoadedPluginRuntime?>(null)
        try {
            val runtime = withContext(Dispatchers.IO) {
                runtimeLoader.prepare(verifiedPackage, identity).also(pending::set)
            }
            currentCoroutineContext().ensureActive()
            pending.set(null)
            return runtime
        } catch (failure: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    pending.getAndSet(null)?.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
            throw failure
        }
    }

    private suspend fun retirePrevious(
        runtime: LoadedPluginRuntime?,
        packageDirectory: File?,
    ): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        if (runtime != null) {
            try {
                runtime.close()
            } catch (_: Exception) {
                return@withContext true
            }
        }
        if (packageDirectory != null && packageDirectory.exists() && !packageDirectory.deleteRecursively()) {
            return@withContext true
        }
        false
    }

    private suspend fun closeUncommittedRuntime(runtime: LoadedPluginRuntime) {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { runtime.close() }
        }
    }

    private suspend fun persistState(record: InstalledPlugin) {
        withContext(NonCancellable + Dispatchers.IO) {
            val stateDirectory = stateDirectoryOnIo()
            val destination = File(stateDirectory, "${record.pluginId}$STATE_EXTENSION")
            val temporary = File(stateDirectory, "${record.pluginId}-${UUID.randomUUID()}$TEMP_EXTENSION")
            val values = Properties().apply {
                setProperty("formatVersion", STATE_FORMAT_VERSION.toString())
                setProperty("pluginId", record.pluginId)
                setProperty("publisherId", record.publisherId)
                setProperty("version", record.version)
                setProperty("packageId", record.packageId)
                setProperty("instanceId", record.installationIdentity.instanceId)
                setProperty("enabled", record.enabled.toString())
            }
            try {
                FileOutputStream(temporary).use { values.store(it, null) }
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (failure: Exception) {
                temporary.delete()
                throw PluginRuntimeManagerException("Plugin install state could not be saved", failure)
            }
        }
    }

    private suspend fun deletePersistedState(pluginId: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                Files.deleteIfExists(File(stateDirectoryOnIo(), "$pluginId$STATE_EXTENSION").toPath())
            } catch (failure: Exception) {
                throw PluginRuntimeManagerException("Plugin install state could not be removed", failure)
            }
        }
    }

    private suspend fun readPersistedState(): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val directory = stateDirectoryOnIo()
        if (!directory.exists()) return@withContext emptyList()
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) {
            throw PluginRuntimeManagerException("Plugin state directory is invalid")
        }
        val stateFiles = directory.listFiles()?.filter { file ->
            file.isFile && !Files.isSymbolicLink(file.toPath()) && file.name.endsWith(STATE_EXTENSION)
        }.orEmpty()
        stateFiles.mapNotNull { file ->
            if (file.length() > MAX_STATE_FILE_BYTES) return@mapNotNull null
            val properties = Properties()
            try {
                FileInputStream(file).use(properties::load)
                val pluginId = properties.getProperty("pluginId") ?: return@mapNotNull null
                val publisherId = properties.getProperty("publisherId") ?: return@mapNotNull null
                val version = properties.getProperty("version") ?: return@mapNotNull null
                val packageId = properties.getProperty("packageId") ?: return@mapNotNull null
                val instanceId = properties.getProperty("instanceId") ?: return@mapNotNull null
                val enabled = properties.getProperty("enabled")?.toBooleanStrictOrNull() ?: return@mapNotNull null
                if (properties.getProperty("formatVersion") != STATE_FORMAT_VERSION.toString() ||
                    !isPluginId(pluginId) || file.name != "$pluginId$STATE_EXTENSION" ||
                    !IDENTIFIER_PATTERN.matches(publisherId) || !TOKEN_PATTERN.matches(version) ||
                    !UUID_PATTERN.matches(packageId) || !UUID_PATTERN.matches(instanceId)
                ) {
                    return@mapNotNull null
                }
                InstalledPlugin(
                    pluginId = pluginId,
                    publisherId = publisherId,
                    version = version,
                    packageId = packageId,
                    installationIdentity = ToolInstallationIdentity(pluginId, instanceId),
                    packageDirectory = installedPackageDirectoryOnIo(pluginId, packageId),
                    enabled = enabled,
                    expectation = null,
                    manifest = null,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun moveStagedPackage(stageDirectory: File, destination: File) {
        withContext(Dispatchers.IO) {
            ensureDirectory(destination.parentFile ?: throw IOException())
            if (destination.exists()) throw PluginRuntimeManagerException("Plugin package instance path already exists")
            Files.move(stageDirectory.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
    }

    private suspend fun installedPackageDirectory(pluginId: String, packageId: String): File =
        withContext(Dispatchers.IO) { installedPackageDirectoryOnIo(pluginId, packageId) }

    private fun installedPackageDirectoryOnIo(pluginId: String, packageId: String): File {
        val installedRoot = File(appContext.filesDir, "$PACKAGE_ROOT_NAME/$INSTALLED_DIRECTORY_NAME")
        val pluginDirectory = File(installedRoot, pluginId)
        ensureDirectory(pluginDirectory)
        val directory = File(pluginDirectory, packageId).canonicalFile
        if (!directory.toPath().startsWith(installedRoot.canonicalFile.toPath())) {
            throw PluginRuntimeManagerException("Plugin package path escapes private storage")
        }
        return directory
    }

    private fun existingPluginDataDirectory(pluginId: String): File? {
        val dataRoot = File(appContext.filesDir, "$PACKAGE_ROOT_NAME/$DATA_DIRECTORY_NAME").canonicalFile
        val directory = File(dataRoot, pluginId)
        if (!directory.exists()) return null
        val canonical = directory.canonicalFile
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath()) ||
            !canonical.toPath().startsWith(dataRoot.toPath())
        ) {
            throw PluginRuntimeManagerException("Plugin data path is invalid")
        }
        return canonical
    }

    private fun stateDirectoryOnIo(): File {
        val directory = File(appContext.filesDir, "$PACKAGE_ROOT_NAME/$STATE_DIRECTORY_NAME")
        ensureDirectory(directory)
        return directory.canonicalFile
    }

    private fun ensureDirectory(directory: File) {
        val appFiles = appContext.filesDir.canonicalFile
        val target = directory.absoluteFile
        if (!target.exists() && !target.mkdirs() && !target.isDirectory) {
            throw IOException("Unable to create App-private plugin directory")
        }
        if (!target.isDirectory || Files.isSymbolicLink(target.toPath()) ||
            !target.canonicalFile.toPath().startsWith(appFiles.toPath())
        ) {
            throw PluginRuntimeManagerException("Plugin path escapes App-private storage")
        }
    }

    private fun status(record: InstalledPlugin): PluginRuntimeStatus = synchronized(stateLock) {
        statusLocked(record)
    }

    private fun statusLocked(record: InstalledPlugin): PluginRuntimeStatus {
        val availability = when {
            !isRestorationComplete -> PluginInstanceAvailability.RESTORING
            !record.enabled -> PluginInstanceAvailability.INVALIDATED
            active[record.pluginId]?.installationIdentity == record.installationIdentity ->
                PluginInstanceAvailability.ACTIVE
            else -> PluginInstanceAvailability.UNAVAILABLE
        }
        return PluginRuntimeStatus(
            pluginId = record.pluginId,
            publisherId = record.publisherId,
            version = record.version,
            enabled = record.enabled,
            installationIdentity = record.installationIdentity,
            availability = availability,
            cleanupPending = record.cleanupPending,
        )
    }

    private fun expectationMatchesRecord(expectation: PluginPackageExpectation, record: InstalledPlugin): Boolean =
        expectation.pluginId == record.pluginId &&
            expectation.publisherId == record.publisherId &&
            expectation.version == record.version

    private fun operationLock(pluginId: String): Mutex = operationLocks.computeIfAbsent(pluginId) { Mutex() }

    private fun requireRestored() {
        check(isRestorationComplete) { "Plugin runtime restoration has not completed" }
    }

    private fun isPluginId(value: String): Boolean = PLUGIN_ID_PATTERN.matches(value)

    private data class InstalledPlugin(
        val pluginId: String,
        val publisherId: String,
        val version: String,
        val packageId: String,
        val installationIdentity: ToolInstallationIdentity,
        val packageDirectory: File,
        val enabled: Boolean,
        val expectation: PluginPackageExpectation?,
        val manifest: PluginPackageManifest?,
        val cleanupPending: Boolean = false,
    )

    private companion object {
        const val PACKAGE_ROOT_NAME = "plugin-packages"
        const val INSTALLED_DIRECTORY_NAME = "installed"
        const val DATA_DIRECTORY_NAME = "data"
        const val STATE_DIRECTORY_NAME = "state"
        const val STATE_EXTENSION = ".properties"
        const val TEMP_EXTENSION = ".tmp"
        const val STATE_FORMAT_VERSION = 1
        const val MAX_STATE_FILE_BYTES = 16 * 1024L
        val PLUGIN_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val TOKEN_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}

internal class PluginRuntimeManagerException(message: String, cause: Throwable? = null) : Exception(message, cause)
