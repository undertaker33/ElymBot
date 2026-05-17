# Architecture Governance

This directory records architecture governance contracts for the ElymBot Android codebase. It is a local architecture entry point, not a replacement for the module background documents under `docs/01` to `docs/10`.

## Three-Phase Route

1. Phase 1: freeze the current debt surface. Add source contracts, owner maps, and baseline reports so new debt is visible immediately.
2. Phase 2: assign owners and ports. Move shared models and service access behind feature-first domain models, production ports, and Hilt-owned wiring.
3. Phase 3: turn the proven soft boundaries into Gradle module boundaries. Split by API/implementation first, keep architecture tests for DAO ownership and debt explanations, then retire compatibility seams as modules stabilize.

## Promotion Gates

Phase 1 can promote only when architecture contracts exist for singleton debt, AppModels ownership, cross-feature dependencies, and Hilt-only production DI, and when the debt baseline can be regenerated without manual counting.

Phase 2 can promote only when every temporary owner map entry has a confirmed feature/core owner, new ports are in place before production caller migration, and added allowlist entries include an expiry plus a rollback path.

Phase 3 can promote only when temporary allowlist entries are decreasing, compatibility seams have removal issues, module tests cover migrated callers, and production code no longer depends on review-only ownership.

## Build Chain Baseline

Phase 3 should stabilize the build chain before Gradle module splitting:

- Keep Android Gradle Plugin at `8.13.2` for the first Phase 3 round.
- Pin Gradle Wrapper to stable Gradle `8.13`; do not use milestone, rc, alpha, or beta distributions.
- `compileSdk` and `targetSdk` may move to `36` only when local machines and CI install `platforms;android-36`.
- Treat AGP `9.x` as a separate build-chain major upgrade task with its own compatibility spike and full verification pass.

## Allowlist Eight Fields

New architecture allowlist formats should use these eight fields unless a contract explicitly documents a narrower legacy format:

`path | type | category | owner | target | reason | expires | issue`

- `path`: production path relative to its architecture source root; current multi-module roots are defined by `build/reports/architecture/source-roots.txt`.
- `type`: debt kind such as `object`, `companion`, `dependency`, or `model`.
- `category`: `temporary`, `permanent`, or `permanent-candidate`.
- `owner`: accountable module or review owner.
- `target`: future owner path, port, or service.
- `reason`: why the entry exists.
- `expires`: phase, review gate, or date-like milestone.
- `issue`: tracking issue or review token.

## Adding A Singleton

1. Prefer constructor injection or an existing Hilt binding.
2. If a singleton is unavoidable, prove it is stateless or pure.
3. Add the narrowest architecture contract coverage before introducing the production symbol.
4. Add or update the allowlist entry with all required fields, an expiry, and an issue.
5. Run the relevant architecture test and the module test that owns the caller.

## Adding A Cross-Feature Dependency

1. Stop and identify the source feature, target feature, and direction of ownership.
2. Prefer a domain port owned by the caller-facing module instead of importing another feature implementation.
3. Wire production implementations through Hilt modules under `di/hilt/*` or the owning feature DI boundary.
4. Add architecture coverage when a new dependency direction is allowed temporarily.
5. Record temporary exceptions in an allowlist with a target port/service and expiry.

## Architecture Contract Entry Points

Root transition entry:

- `./gradlew architectureCheck --console=plain`

Current `architectureCheck` generates the architecture reports and runs `:architecture-tests:test`. Keep this root entry stable as the summary gate; when debugging a contract failure, run `:architecture-tests:test` directly.

Build convention checks now live under `build-logic`; run `:build-logic:check` when changing shared Gradle configuration or convention plugins.

Current app-level contracts:

- `app/src/test/java/com/astrbot/android/architecture/GlobalSingletonAllowlistContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/AppModelsOwnershipContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/FeatureFirstBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/Phase7RootBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RepositoryPortSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/StrictHiltOnlyContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/StrictHiltOnlyFinalContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/NoLegacyAdapterContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/NoManualRuntimeSubgraphContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/NoProductionFileDeprecationSuppressContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/BuildBaselineContractTest.kt`
- `app/src/test/java/com/astrbot/android/di/startup/StrictHiltOnlyStartupHotspotSourceTest.kt`

Regenerate or refresh report-backed debt numbers from the root reports `build/reports/architecture/source-roots.txt` and `build/reports/architecture/debt.txt` when the main agent runs the full architecture verification pass.
