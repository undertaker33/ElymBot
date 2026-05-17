# Architecture Debt Baseline

## Snapshot

- Scope: phase 1 architecture governance baseline.
- Production scan roots: current multi-module source roots defined by `build/reports/architecture/source-roots.txt`.
- Report source: generated or updated from the root reports `build/reports/architecture/source-roots.txt` and `build/reports/architecture/debt.txt`.
- Refresh owner: main verification agent.

## Counts

- Global singleton allowlist entries: generated or updated from `build/reports/architecture/debt.txt`.
- AppModels owner map entries: generated or updated from `build/reports/architecture/debt.txt`.
- Temporary architecture debt entries: generated or updated from `build/reports/architecture/debt.txt`.
- Permanent or permanent-candidate entries: generated or updated from `build/reports/architecture/debt.txt`.
- Cross-feature dependency exceptions: generated or updated from `build/reports/architecture/debt.txt`.

## Top Owners

- feature-plugin: generated or updated from `build/reports/architecture/debt.txt`.
- feature-qq: generated or updated from `build/reports/architecture/debt.txt`.
- core-runtime: generated or updated from `build/reports/architecture/debt.txt`.
- feature-provider: generated or updated from `build/reports/architecture/debt.txt`.
- app-models-review: generated or updated from `build/reports/architecture/debt.txt`.

## Known Existing Test Failures

- Generated or updated from `build/reports/architecture/source-roots.txt`, `build/reports/architecture/debt.txt`, and the main agent verification log.
- Do not hand-edit this section with guessed counts.
- If a failure is accepted as existing debt, record the owning module, the contract name, the expiry, and the issue in the generated report source first.
