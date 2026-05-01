package com.astrbot.android.core.runtime.container

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class DefaultContainerRuntimeController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val commandRunner: CommandRunner,
    private val installer: ContainerRuntimeInstallerPort,
) : ContainerRuntimeController {
    override fun startNapCat(): CommandExecutionResult {
        ensureRuntimeInstalled()
        return commandRunner.execute(runtimeScript(ContainerRuntimeScript.START_NAPCAT))
    }

    override fun stopNapCat(): CommandExecutionResult {
        ensureRuntimeInstalled()
        return commandRunner.execute(runtimeScript(ContainerRuntimeScript.STOP_NAPCAT))
    }

    override fun statusNapCat(): CommandExecutionResult {
        ensureRuntimeInstalled()
        return commandRunner.execute(runtimeScript(ContainerRuntimeScript.STATUS_NAPCAT))
    }

    override fun logoutQq(): CommandExecutionResult {
        ensureRuntimeInstalled()
        return commandRunner.execute(runtimeScript(ContainerRuntimeScript.LOGOUT_QQ))
    }

    private fun runtimeScript(script: ContainerRuntimeScript): CommandSpec {
        return ContainerRuntimeScripts.command(
            filesDir = appContext.filesDir,
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
            script = script,
        )
    }

    private fun ensureRuntimeInstalled() {
        runBlocking {
            installer.ensureInstalled()
        }
    }
}
