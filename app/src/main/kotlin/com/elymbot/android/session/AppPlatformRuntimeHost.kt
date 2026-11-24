package com.elymbot.android.session

import com.elymbot.android.runtime.container.ProotGuestLauncher
import com.elymbot.android.runtime.pluginpackage.PluginRuntimeManager
import com.elymbot.core.contract.PlatformGuestProcess
import com.elymbot.core.contract.PlatformRuntimeHost
import com.elymbot.core.contract.ToolInstallationIdentity
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Runtime capability bound to one active installation and its loader-resolved private directory. */
internal class AppPlatformRuntimeHost(
    private val installation: ToolInstallationIdentity,
    private val pluginRuntimeManager: PluginRuntimeManager,
    private val guestLauncher: ProotGuestLauncher,
) : PlatformRuntimeHost {
    override suspend fun launchGuest(
        rootfsRelativePath: String,
        guestEntry: String,
    ): PlatformGuestProcess = handoffPlatformResource(
        launch = { registerPending ->
            withContext(Dispatchers.IO) {
                check(pluginRuntimeManager.isActive(installation)) { "Platform installation is inactive" }
                val pluginDirectory = pluginRuntimeManager.activePluginDataDirectory(installation)
                    ?: throw IllegalStateException("Platform data directory is unavailable")
                val rootfs = resolveRootfs(pluginDirectory, rootfsRelativePath)
                val process = guestLauncher.launch(pluginDirectory, rootfs, guestEntry)
                val guest = AppPlatformGuestProcess(process)
                registerPending(guest)
                check(pluginRuntimeManager.isActive(installation)) { "Platform installation changed during startup" }
                guest
            }
        },
        cleanup = PlatformGuestProcess::stop,
    )

    private fun resolveRootfs(pluginDirectory: File, relativePath: String): File {
        require(relativePath.isNotBlank() && '\u0000' !in relativePath)
        val relative = Paths.get(relativePath)
        require(!relative.isAbsolute && relative.none { it.toString() == ".." || it.toString() == "." })
        val base = pluginDirectory.canonicalFile.toPath()
        val candidate = pluginDirectory.resolve(relativePath).canonicalFile.toPath()
        require(candidate != base && candidate.startsWith(base) && Files.isDirectory(candidate))
        var current = base
        for (segment in relative) {
            current = current.resolve(segment)
            require(!Files.isSymbolicLink(current))
        }
        return candidate.toFile()
    }
}

internal suspend fun <Resource : Any> handoffPlatformResource(
    launch: suspend (registerPending: (Resource) -> Unit) -> Resource,
    cleanup: suspend (Resource) -> Unit,
): Resource {
    val pending = AtomicReference<Resource?>(null)
    try {
        val resource = launch(pending::set)
        pending.compareAndSet(resource, null)
        return resource
    } catch (failure: Throwable) {
        try {
            withContext(NonCancellable) {
                val resource = pending.getAndSet(null)
                if (resource != null) cleanup(resource)
            }
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }
}

private class AppPlatformGuestProcess(
    private val delegate: com.elymbot.android.runtime.container.ProotGuestProcess,
) : PlatformGuestProcess {
    override val isAlive: Boolean
        get() = delegate.isAlive

    override suspend fun awaitExit(timeoutMillis: Long): Int? = withContext(Dispatchers.IO) {
        delegate.awaitExit(timeoutMillis)
    }

    override suspend fun stop() {
        withContext(NonCancellable + Dispatchers.IO) { delegate.close() }
    }
}
