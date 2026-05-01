# Post-Hilt A Round 1 Contract

## Allowed Production DI Paths

Production dependency injection must stay inside the Hilt-owned graph. Allowed paths are `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@HiltWorker`, constructor injection, and explicit bindings in `di/hilt/*` plus the production ports already wired from `di/BackupDataPorts.kt`, `di/RuntimeContextDataPorts.kt`, and `di/ContainerBridgeStatePorts.kt`.

## Forbidden Patterns

The production mainline must not reintroduce handwritten app containers, service locators, `ViewModelProvider.Factory`, `viewModelFactory`, `astrBotViewModel(...)`, `install*` or `configure*` runtime callbacks, or new static provider objects for runtime stores. Round 1 also forbids production declarations or fallback usage of `PluginRuntimeFailureStateStoreProvider`, `PluginRuntimeScopedFailureStateStoreProvider`, and `PluginRuntimeScheduleStateStoreProvider`.

## Transition Allowlist

Compat seams are only allowed where architecture contracts explicitly pin them. This includes narrow wrappers that keep legacy callers compiling while the real production path is already Hilt-owned. Test-only helpers may still mention `PluginRuntimeFailureStateStoreProvider`, `PluginRuntimeScopedFailureStateStoreProvider`, `PluginRuntimeLogBusProvider`, `PluginRuntimeScheduleStateStoreProvider`, `PluginV2ActiveRuntimeStoreProvider`, `PluginV2DispatchEngineProvider`, `PluginV2LifecycleManagerProvider`, and `PluginV2RuntimeLoaderProvider`, but those names are debt inventory, not production DI permission.

## Round 2 Debt

Round 2 debt still tracks static or compat residues that must not grow: `PluginRuntimeFailureStateStoreProvider`, `PluginRuntimeScopedFailureStateStoreProvider`, `PluginRuntimeLogBusProvider`, `PluginRuntimeScheduleStateStoreProvider`, `PluginV2ActiveRuntimeStoreProvider`, `PluginV2DispatchEngineProvider`, `PluginV2LifecycleManagerProvider`, `PluginV2RuntimeLoaderProvider`, `PluginExecutionHostApi`, `DefaultPluginHostCapabilityGateway`, and `ProviderRepositoryInitializer`.

### Round 3 Debt

Round 3 Debt is the remaining cleanup after the Hilt-only mainline is frozen: remove residual compat wrappers, collapse test-only seams when no longer needed, and keep the production path from drifting back toward registry or provider-based dependency access.
