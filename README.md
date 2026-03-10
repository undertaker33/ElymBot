# AstrBot Android Native

Native Android baseline for an AstrBot-focused mobile app.

## Current direction

This project uses:

- Android SDK 34
- Kotlin
- Jetpack Compose
- DataStore for local settings
- A native app shell for AstrBot product features
- A container bridge placeholder for NapCat runtime integration

## Product boundary

Native:

- QQ-only bot management UI
- model provider configuration
- persona management
- conversation context storage
- platform log viewer
- app settings and runtime orchestration

Container/runtime:

- NapCat runtime
- QQ protocol chain
- anti-detection related runtime behavior

## Delivery phases

### Phase 1

- native Android project baseline
- Compose navigation
- local settings storage
- runtime state model
- log center
- container bridge service placeholder

### Phase 2

- QQ bot management pages
- provider management for chat, TTS, ASR
- persona management
- conversation context persistence

### Phase 3

- NapCat local bridge integration
- message event pipeline
- bot send/receive orchestration
- runtime health monitoring

### Phase 4

- plugin management
- knowledge base module
- advanced tools and automation

## Build requirements

- Android Studio Ladybug or newer
- JDK 17+
- Android SDK Platform 34
- Android Build Tools compatible with AGP 8.5+

## Notes

This workspace environment does not currently provide Gradle or JDK 17, so the project skeleton was created without running a full Android build.
