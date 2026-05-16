# Post-Hilt A Round 1 Contract

## Allowed Production DI Paths

Production dependencies are owned by Hilt bindings, constructor injection, Hilt ViewModels, Hilt Workers, Android entry points, startup chains, or explicit production ports.

## Forbidden Patterns

Do not reintroduce hand-written app containers, service locators, global registries, static install callbacks, static runtime subgraphs, production ViewModelProvider factories, or compat gateway helpers on production hot paths.

## Transition Allowlist

Temporary static seams must remain listed in architecture allowlists with owner, target, reason, expiry, and issue. New production callers should move behind injected ports instead of expanding those seams.

## Round 2 Debt

Frozen debt inventory:

- PluginRuntimeFailureStateStoreProvider
- PluginRuntimeScopedFailureStateStoreProvider
- PluginRuntimeLogBusProvider
- PluginRuntimeScheduleStateStoreProvider
- PluginV2ActiveRuntimeStoreProvider
- PluginV2DispatchEngineProvider
- PluginV2LifecycleManagerProvider
- PluginV2RuntimeLoaderProvider
- PluginExecutionHostApi
- DefaultPluginHostCapabilityGateway
- ProviderRepositoryInitializer
- Round 3 Debt

### Round 3 Debt

Round 3 closes the remaining production hot paths by replacing compat providers and static host capability entry points with injected runtime, capability, and repository ports.
