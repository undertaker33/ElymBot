package com.elymbot.android.di.hilt.runtime

import com.elymbot.android.core.runtime.container.CommandRunner
import com.elymbot.android.core.runtime.container.ContainerRuntimeController
import com.elymbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.elymbot.android.core.runtime.container.ContainerRuntimeInstallerPort
import com.elymbot.android.core.runtime.container.DefaultCommandRunner
import com.elymbot.android.core.runtime.container.DefaultContainerRuntimeController
import com.elymbot.android.core.runtime.container.RuntimeBridgeController
import com.elymbot.android.di.runtime.container.AndroidRuntimeBridgeController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ContainerRuntimeModule {
    @Binds
    @Singleton
    abstract fun bindCommandRunner(
        runner: DefaultCommandRunner,
    ): CommandRunner

    @Binds
    @Singleton
    abstract fun bindContainerRuntimeInstallerPort(
        installer: ContainerRuntimeInstaller,
    ): ContainerRuntimeInstallerPort

    @Binds
    @Singleton
    abstract fun bindContainerRuntimeController(
        controller: DefaultContainerRuntimeController,
    ): ContainerRuntimeController

    @Binds
    @Singleton
    abstract fun bindRuntimeBridgeController(
        controller: AndroidRuntimeBridgeController,
    ): RuntimeBridgeController
}
