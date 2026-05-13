package com.astrbot.android.feature.plugin.runtime

object PluginRuntimeLogBusProvider {
    @Volatile
    private var busOverrideForTests: PluginRuntimeLogBus? = null

    private val sharedBus: PluginRuntimeLogBus by lazy {
        InMemoryPluginRuntimeLogBus()
    }

    fun bus(): PluginRuntimeLogBus = busOverrideForTests ?: sharedBus

    fun setBusOverrideForTests(bus: PluginRuntimeLogBus?) {
        busOverrideForTests = bus
    }
}

object PluginV2ActiveRuntimeStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginV2ActiveRuntimeStore? = null

    private val sharedStore: PluginV2ActiveRuntimeStore by lazy {
        PluginV2ActiveRuntimeStore()
    }

    fun store(): PluginV2ActiveRuntimeStore = storeOverrideForTests ?: sharedStore

    fun setStoreOverrideForTests(store: PluginV2ActiveRuntimeStore?) {
        storeOverrideForTests = store
    }
}

object PluginV2DispatchEngineProvider {
    @Volatile
    private var engineOverrideForTests: PluginV2DispatchEngine? = null

    private val sharedEngine: PluginV2DispatchEngine by lazy {
        PluginV2DispatchEngine()
    }

    fun engine(): PluginV2DispatchEngine = engineOverrideForTests ?: sharedEngine

    fun setEngineOverrideForTests(engine: PluginV2DispatchEngine?) {
        engineOverrideForTests = engine
    }
}

object PluginV2LifecycleManagerProvider {
    @Volatile
    private var managerOverrideForTests: PluginV2LifecycleManager? = null

    private val sharedManager: PluginV2LifecycleManager by lazy {
        PluginV2LifecycleManager()
    }

    fun manager(): PluginV2LifecycleManager = managerOverrideForTests ?: sharedManager

    fun setManagerOverrideForTests(manager: PluginV2LifecycleManager?) {
        managerOverrideForTests = manager
    }
}

object PluginV2RuntimeLoaderProvider {
    @Volatile
    private var loaderOverrideForTests: PluginV2RuntimeLoader? = null

    private val sharedLoader: PluginV2RuntimeLoader by lazy {
        PluginV2RuntimeLoader()
    }

    fun loader(): PluginV2RuntimeLoader = loaderOverrideForTests ?: sharedLoader

    fun setLoaderOverrideForTests(loader: PluginV2RuntimeLoader?) {
        loaderOverrideForTests = loader
    }
}

object PluginRuntimeCatalog {
    @Volatile
    private var compatProvider: (() -> List<PluginRuntimePlugin>)? = null

    fun plugins(): List<PluginRuntimePlugin> = compatProvider?.invoke().orEmpty()

    fun registerProvider(provider: () -> List<PluginRuntimePlugin>) {
        compatProvider = provider
    }

    fun reset() {
        compatProvider = null
    }
}

object PluginRuntimeRegistry {
    @Volatile
    private var pluginProvider: () -> List<PluginRuntimePlugin> = { emptyList() }

    @Volatile
    private var externalProviders: List<() -> List<PluginRuntimePlugin>> = emptyList()

    fun plugins(): List<PluginRuntimePlugin> {
        return buildList {
            addAll(pluginProvider())
            externalProviders.forEach { provider ->
                addAll(provider())
            }
        }
    }

    fun registerProvider(provider: () -> List<PluginRuntimePlugin>) {
        pluginProvider = provider
    }

    fun registerExternalProvider(provider: () -> List<PluginRuntimePlugin>) {
        externalProviders = externalProviders + provider
    }

    fun reset() {
        pluginProvider = { emptyList() }
        externalProviders = emptyList()
    }
}

internal object AppChatPluginRuntimeCoordinatorProvider {
    internal fun setCoordinatorOverrideForTests(
        coordinator: PluginV2LlmPipelineCoordinator?,
    ) = Unit
}
