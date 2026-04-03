# Task 6 (Phase 3) Meme Manager Android Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independently packaged Android protocol sample plugin asset (meme manager) that can be discovered via catalog, installed with source tracking, participate in update checks, and drive one runtime path plus one admin path via existing host test skeletons.

**Architecture:** Ship two prebuilt plugin zip packages plus one catalog JSON fixture under `artifacts/plugins/meme-manager-sample/**`. Tests load these fixtures from repo files, then reuse existing `PluginCatalogSynchronizer` / `PluginInstaller` / `PluginRepository.getUpdateAvailability` and `PluginRuntimeRegistry` to prove the end-to-end chain without embedding plugin logic into host runtime.

**Tech Stack:** Kotlin/JUnit unit tests; existing plugin protocol (`manifest.json` in zip) and catalog schema (JSON decoded by `PluginCatalogJson`).

---

## File Structure

- Create: `artifacts/plugins/meme-manager-sample/catalog/meme-manager.sample.catalog.json`
- Create: `artifacts/plugins/meme-manager-sample/packages/meme-manager-1.0.0.zip`
- Create: `artifacts/plugins/meme-manager-sample/packages/meme-manager-1.1.0.zip`
- Create: `docs/plugins/samples/meme-manager-android-sample.md`
- Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/MemeManagerSampleCatalogTest.kt`
- Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/MemeManagerSampleInstallAndUpgradeTest.kt`
- Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/MemeManagerSampleRuntimeAndAdminPathTest.kt`
- (Optional) Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/SampleAssetPaths.kt` (shared helper)

## Tasks

### Task 1: Add failing tests for fixture-driven catalog discovery

**Files:**
- Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/MemeManagerSampleCatalogTest.kt`

- [ ] **Step 1: Write RED test** that loads the repo fixture JSON file and runs `PluginCatalogSynchronizer` with a fake fetcher, asserting the normalized catalog contains the sample plugin and version package URLs are resolved as expected.
- [ ] **Step 2: Run** `./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleCatalogTest` (expect FAIL).
- [ ] **Step 3: Add minimal catalog fixture file** under `artifacts/` to make the test pass.
- [ ] **Step 4: Re-run** the test (expect PASS).

### Task 2: Add failing tests for installation source tracking and update availability

**Files:**
- Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/MemeManagerSampleInstallAndUpgradeTest.kt`

- [ ] **Step 1: Write RED test** that installs the sample plugin from a catalog version intent and asserts:
  - extracted files exist under extracted dir
  - `installed.source.location` and `installed.installedPackageUrl` match package URL
  - `catalogSourceId` is persisted
- [ ] **Step 2: Write RED test** for `PluginRepository.getUpdateAvailability` using the sample catalog data (1.1.0 available over 1.0.0).
- [ ] **Step 3: Run** the two tests (expect FAIL).
- [ ] **Step 4: Add the two plugin zip package assets** under `artifacts/` so install and update checks can pass.
- [ ] **Step 5: Re-run** those tests (expect PASS).

### Task 3: Add failing tests proving runtime path + admin path are driven by installed assets

**Files:**
- Create: `app/src/test/java/com/astrbot/android/runtime/plugin/samples/MemeManagerSampleRuntimeAndAdminPathTest.kt`

- [ ] **Step 1: Write RED test** that:
  - installs the sample plugin package
  - registers a `PluginRuntimeRegistry` provider for the sample pluginId
  - handler captures `extractedDir` and reads a resource file to return `TextResult`
  - executes through `PluginExecutionEngine` for `OnCommand` and asserts output.
- [ ] **Step 2: Write RED test** that:
  - installs the sample plugin package
  - registers a runtime handler supporting `OnPluginEntryClick` returning `SettingsUiRequest` (optionally reading a resource marker)
  - constructs a `PluginViewModel` with fake deps containing the installed record
  - selects plugin and asserts schema UI state becomes Settings.
- [ ] **Step 3: Run** these tests (expect FAIL).
- [ ] **Step 4: Ensure zip resources include the expected files** used by tests.
- [ ] **Step 5: Re-run** (expect PASS).

### Task 4: Documentation

**Files:**
- Create: `docs/plugins/samples/meme-manager-android-sample.md`

- [ ] **Step 1: Write doc** stating:
  - derived from `astrbot_plugin_meme_manager-main` via Android protocol adaptation
  - this is a "Phase 4 sample prerequisite asset"
  - preserved capabilities: local gallery/category, trigger-to-send meme, basic admin management
  - removed: WebUI, cloud sync, Python runtime compatibility
  - how to run the verifying unit tests

## Verification

- [ ] Run focused tests:
  - `./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleCatalogTest`
  - `./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleInstallAndUpgradeTest`
  - `./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleRuntimeAndAdminPathTest`
- [ ] If feasible, run full unit tests:
  - `./gradlew.bat :app:testDebugUnitTest`

