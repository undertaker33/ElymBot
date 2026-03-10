# Architecture

## Goal

Build a native Android app around AstrBot product capabilities while keeping NapCat as a dedicated runtime boundary for QQ protocol handling.

## Boundaries

### Native app

- bot configuration and orchestration
- provider management for chat, TTS, ASR
- persona management
- conversation context management
- platform log center
- plugin and knowledge base management UI

### Container runtime

- NapCat runtime
- QQ event ingress and egress
- anti-detection sensitive behavior

## Runtime shape

```text
Android UI
  -> Native domain layer
  -> Native local storage
  -> Container bridge service
  -> NapCat runtime
  -> QQ platform
```

## Phase breakdown

### Phase 1

- create SDK 34 native baseline
- establish navigation and app shell
- define runtime state and bridge service placeholder
- define local settings and log center

### Phase 2

- QQ bot module pages
- provider CRUD
- persona CRUD
- conversation context persistence

### Phase 3

- connect bridge service to actual NapCat runtime
- add health checks and state sync
- wire QQ message send/receive orchestration

### Phase 4

- plugin management
- knowledge base management
- tool registry and execution policy

## Android compatibility

- compileSdk = 34
- targetSdk = 34
- code should avoid hidden APIs and legacy background execution assumptions
- foreground runtime behavior must follow Android 14+ restrictions
- storage and notification behavior should remain policy-compliant on Android 15+
