package com.astrbot.android.core.runtime.container

interface ContainerRuntimeController {
    fun startNapCat(): CommandExecutionResult
    fun stopNapCat(): CommandExecutionResult
    fun statusNapCat(): CommandExecutionResult
    fun logoutQq(): CommandExecutionResult
}
