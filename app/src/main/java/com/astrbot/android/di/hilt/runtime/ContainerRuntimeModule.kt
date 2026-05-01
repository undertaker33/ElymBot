package com.astrbot.android.di.hilt.runtime

import com.astrbot.android.core.runtime.container.CommandRunner
import com.astrbot.android.core.runtime.container.ContainerRuntimeController
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstallerPort
import com.astrbot.android.core.runtime.container.DefaultCommandRunner
import com.astrbot.android.core.runtime.container.DefaultContainerRuntimeController
import com.astrbot.android.core.runtime.container.RuntimeBridgeController
import com.astrbot.android.di.runtime.container.AndroidRuntimeBridgeController
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
