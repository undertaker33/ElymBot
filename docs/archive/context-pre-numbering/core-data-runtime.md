# core-foundation-and-db / core-runtime 模块上下文

> 归档说明：本文件是新版编号规则前的合并报告，仅作为迁移证据。当前入口已拆分为 `docs/context/03-核心基础与数据库.md` 和 `docs/context/04-核心运行时.md`。

更新时间：2026-05-17 04:32 +08:00

## 状态

- 场景：`uth-docs`
- 模式：`module-governance`
- 覆盖模块：`core-foundation-and-db`, `core-runtime`
- 模块状态：已按当前代码事实重新确认
- 完成等级：`partial/paused`
- 下一模块：`download-and-container-assets`
- 本轮代码修改：无
- 本轮 Git 写入：无
- 本轮 Gradle / 测试命令：未运行；本场景只做文档治理

文件名说明：`core-data-runtime.md` 是旧 onboarding follow-up 留下的文件名。当前该文件已经覆盖第 3 模块 `core-foundation-and-db` 与第 4 模块 `core-runtime`；后续不能再把其中任一范围视作未确认 seed。

## 代码事实来源

本上下文重新核对了以下当前源码、构建和合同测试入口：

- `settings.gradle.kts`
- `build.gradle.kts`
- `app-integration/build.gradle.kts`
- `core/common/build.gradle.kts`
- `core/backup/build.gradle.kts`
- `core/db/build.gradle.kts`
- `core/logging/build.gradle.kts`
- `core/network/build.gradle.kts`
- `core/ui/build.gradle.kts`
- `core/runtime/build.gradle.kts`
- `core/runtime-audio/build.gradle.kts`
- `core/runtime-cache/build.gradle.kts`
- `core/runtime-container/build.gradle.kts`
- `core/runtime-context/build.gradle.kts`
- `core/runtime-llm/build.gradle.kts`
- `core/runtime-search/build.gradle.kts`
- `core/runtime-secret/build.gradle.kts`
- `core/runtime-session/build.gradle.kts`
- `core/runtime-tool/build.gradle.kts`
- `core/common/src/main/java/**`
- `core/backup/src/main/java/**`
- `core/db/src/main/java/**`
- `core/logging/src/main/java/**`
- `core/network/src/main/java/**`
- `core/ui/src/main/java/**`
- `core/runtime*/src/main/java/**`
- `app-integration/src/main/java/com/astrbot/android/app/integration/db/DatabaseModule.kt`
- `app-integration/src/main/java/com/astrbot/android/di/hilt/RepositoryPortModule.kt`
- `app-integration/src/main/java/com/astrbot/android/di/hilt/BackupModule.kt`
- `app/src/test/java/com/astrbot/android/architecture/CoreDbDaoOwnerContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/CoreCommonBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RuntimeContextBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RuntimeContainerBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RuntimeSearchBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RuntimeAudioBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RuntimeSecretSessionCacheBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RuntimePersistenceBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/LlmSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/ModuleDependencyGraphContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/StaticRepositoryUsageContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/GlobalSingletonAllowlistContractTest.kt`
- `app/src/test/resources/architecture/dao-owner-allowlist.txt`
- `app/src/test/resources/architecture/static-repository-usage-allowlist.txt`
- `app/src/test/resources/architecture/global-singleton-allowlist.txt`
- `app/src/test/resources/architecture/runtime-persistence-allowlist.txt`

排除路径：

- `build/`
- `bin/`
- `.worktrees/`
- 生成物、IDE 缓存和二进制资源
- 旧文档正文自身

辅助证据：

- `AGENTS.md`
- `docs/00_当前基线与迁移摘要.md`
- `docs/02_数据真源_Room_Repository_备份基线.md`
- `docs/04_聊天会话_App内消息链路.md`
- `docs/06_STT_TTS_声音克隆_资产.md`
- `docs/07_插件平台_模型_安装_执行_治理.md`
- `docs/10_测试入口_回归面_已知风险.md`

辅助证据不得覆盖当前代码事实。

## 模块职责

`core-foundation-and-db` 当前覆盖：

- `:core:common`：低耦合通用 guard，目前主要是 profile 删除保护和引用检查接口。
- `:core:backup`：backup participant contract 与 registry。
- `:core:db`：Room database、entity、DAO、aggregate DAO、migration 和 schema 真源。
- `:core:logging`：runtime log sink / store / maintenance service / cleanup settings。
- `:core:network`：runtime network request/response/failure model、transport contract 和 OkHttp transport。
- `:core:ui`：共享 Compose UI tokens、top bar registration、通用控件和 app 资源复用。
- app-integration 的 `DatabaseModule`：生产 Room database 与 DAO 的 Hilt wiring。

`core-runtime` 当前覆盖：

- `:core:runtime`：粗粒度 runtime 剩余壳层；当前主源码只剩 HTTP client wrapper。
- `:core:runtime-context`：纯 Kotlin/JVM 的 runtime context DTO、prompt assembly、ingress、resource projection、web search prompt contract 和 tool source context。
- `:core:runtime-search`：统一搜索 contract、API provider、profile search、local search engine/parser/crawler/policy/relevance/coordinator。
- `:core:runtime-tool`：tool source contract、tool descriptor cache policy、JSON value normalizer。
- `:core:runtime-llm`：LLM invocation DTO、client/probe port 和 streaming segmenter。
- `:core:runtime-container`：container bridge/controller/installer/rootfs extraction/command runner/runtime bridge contracts。
- `:core:runtime-audio`：通用音频 runtime contract、on-device TTS catalog、Sherpa asset manager、Silk encoder、TTS prompt/style helper。
- `:core:runtime-secret`：runtime secret store contract 与默认实现。
- `:core:runtime-session`：session lock coordinator。
- `:core:runtime-cache`：runtime cache maintenance port 与默认实现。

## 非职责

- `download:*` 不在本上下文收口；后续 `download-and-container-assets` 单独治理。
- feature repository、feature runtime、provider runtime、plugin runtime、QQ runtime、chat runtime 的业务行为不归本模块完成态。
- app root 下仍存在的 Android adapter 或 compat facade 不归 `:core:runtime*` owner；它们需要在对应 feature/app-shell 模块中继续约束。
- backup UI、备份导入/恢复完整覆盖面和 settings presentation 不在本模块收口；后续 `resource-settings-backup` 单独治理。
- 不得把 `:core:*` 写回依赖 `:app` 或 `:feature:*` 的方向。

## 当前 Gradle 模块事实

`settings.gradle.kts` 当前声明了：

- `:core:common`
- `:core:backup`
- `:core:db`
- `:core:logging`
- `:core:network`
- `:core:ui`
- `:core:runtime`
- `:core:runtime-audio`
- `:core:runtime-cache`
- `:core:runtime-container`
- `:core:runtime-context`
- `:core:runtime-llm`
- `:core:runtime-search`
- `:core:runtime-secret`
- `:core:runtime-session`
- `:core:runtime-tool`

源码数量：

- `core/common/src/main/java`：2 个 Kotlin/Java 文件
- `core/backup/src/main/java`：1 个 Kotlin/Java 文件
- `core/db/src/main/java`：67 个 Kotlin/Java 文件
- `core/logging/src/main/java`：10 个 Kotlin/Java 文件
- `core/network/src/main/java`：2 个 Kotlin/Java 文件
- `core/ui/src/main/java`：9 个 Kotlin/Java 文件
- `core/runtime/src/main/java`：3 个 Kotlin/Java 文件
- `core/runtime-audio/src/main/java`：6 个 Kotlin/Java 文件
- `core/runtime-cache/src/main/java`：2 个 Kotlin/Java 文件
- `core/runtime-container/src/main/java`：14 个 Kotlin/Java 文件
- `core/runtime-context/src/main/java`：13 个 Kotlin/Java 文件
- `core/runtime-llm/src/main/java`：4 个 Kotlin/Java 文件
- `core/runtime-search/src/main/java`：38 个 Kotlin/Java 文件
- `core/runtime-secret/src/main/java`：2 个 Kotlin/Java 文件
- `core/runtime-session/src/main/java`：1 个 Kotlin/Java 文件
- `core/runtime-tool/src/main/java`：3 个 Kotlin/Java 文件

构建依赖事实：

- `:core:db` 使用 Android library、Kotlin Android、KSP、Room `2.6.1`、Room KTX 和 coroutines core。
- `:core:network` 依赖 `:core:logging`，并以 `api` 暴露 OkHttp `4.12.0`。
- `:core:ui` 开启 Compose，并复用 `../../app/src/main/res`。
- `:core:runtime` 依赖 `:core:common`、`:core:logging`、`:core:network`、`:core:runtime-search`、`:core:runtime-tool`，并使用 commons-compress、xz、OkHttp、jsoup、coroutines android 与 `javax.inject`。
- `:core:runtime-context` 使用 `elymbot.kotlin.jvm`，依赖 `:core:runtime-tool`、`javax.inject` 和 `org.json`，不声明 Android plugin、namespace、`:app`、`:app-integration`、`:feature:*` 或粗粒度 `:core:runtime` 依赖。
- `:core:runtime-tool` 使用 `elymbot.kotlin.jvm`，依赖 `org.json`。
- `:core:runtime-search` 是 Android library，依赖 `:core:logging`、`:core:network`、coroutines core、jsoup 和 `javax.inject`，不依赖 `:app`、`:app-integration`、`:core:runtime` 或 `:feature:*`。
- `:core:runtime-container` 是 Android library，依赖 `:core:logging`、`:core:network`、`:core:runtime-secret`、commons-compress、xz、coroutines android、Hilt 和 `javax.inject`。
- `:core:runtime-audio` 是 Android library，启用 KSP/Hilt，依赖 `:core:common`、`:core:logging`、`:core:runtime-container`、commons-compress、coroutines android 和 `javax.inject`。
- `:core:runtime-llm` 是 Android library，依赖 `:core:common` 和 coroutines core。
- `:core:runtime-secret` 依赖 `:core:logging` 与 `javax.inject`。
- `:core:runtime-cache` 依赖 `:core:logging`、coroutines android 和 `javax.inject`。
- `:core:runtime-session` 依赖 coroutines core 和 `javax.inject`。
- `app-integration/build.gradle.kts` 当前消费所有 `:core:runtime*` 模块；`app/build.gradle.kts` 不直接依赖这些 runtime 模块。

## Room / 数据真源

当前 Room 真源：

- DB 文件：`core/db/src/main/java/com/astrbot/android/data/db/AstrBotDatabase.kt`
- DB class：`AstrBotDatabase`
- version：`22`
- `exportSchema = true`
- schema：当前存在 `app/schemas/com.astrbot.android.data.db.AstrBotDatabase/22.json` 和 `core/db/schemas/com.astrbot.android.data.db.AstrBotDatabase/22.json`
- migrations：`astrBotDatabaseMigrations` 聚合 `migration2To3` 到 `migration21To22`

当前 `AstrBotDatabase` 实体覆盖 app preferences、bot/config/conversation/persona/provider aggregate、plugin install/catalog/config snapshot/state、download tasks、saved QQ accounts、TTS voice assets、cron jobs/execution records、resource center items 和 config resource projections。

生产 DB wiring 位于 `app-integration/src/main/java/com/astrbot/android/app/integration/db/DatabaseModule.kt`：

- 使用 `Room.databaseBuilder(..., "astrbot-native.db")`。
- 调用 `.addMigrations(*astrBotDatabaseMigrations)`。
- 通过 Hilt 提供 `AstrBotDatabase` 和各 DAO。
- 提供部分 legacy SharedPreferences / file seam，但不得作为第二套 DI 或静态数据库入口扩张。

## Repository / backup 边界

feature repository 当前仍是 feature data 模块 owner，不属于 `core/db` owner。`core/db` 提供 DAO 与 persistence primitive。

当前可确认的 selected state 数据真源：

- `FeatureBotRepository` 注入 `AppPreferenceDao`，使用 `FEATURE_BOT_PREF_SELECTED_BOT_ID`。
- `FeatureConfigRepository` 注入 `AppPreferenceDao`，使用 `FEATURE_CONFIG_PREF_SELECTED_PROFILE_ID`。
- `select(...)` 的当前语义是持久化 selected id，最终状态以后续 DAO / preference flow 回流为准。

Repository port 绑定当前不是单一大模块文件集中完成，而是分散在 app-integration 的 feature-specific bindings 中；`RepositoryPortModule.kt` 当前只提供 conversation repository port 绑定。

本模块只确认 backup foundation：

- `:core:backup` 当前提供 `BackupParticipant`、`BackupParticipantSnapshot`、`BackupParticipantRestoreResult`、`BackupParticipantRegistry`。
- `app-integration/src/main/java/com/astrbot/android/di/hilt/BackupModule.kt` 为 bots、providers、personas、configs、conversations、qqLogin、ttsAssets 提供 `SUPPORTED` participant metadata，为 plugin、resource、cron 提供 `PLANNED` participant metadata。
- `feature/settings/api/src/main/java/com/astrbot/android/feature/settings/api/backup/AppBackupDataPort.kt` 和 `ConversationBackupDataPort.kt` 是当前 backup data port contract。
- `app/src/main/java/com/astrbot/android/core/db/backup/*` 仍是 app root 下的 transition path，不等于 `:core:db` Gradle 模块。

## Runtime 边界

`runtime-context`：

- `RuntimeContextBoundaryContractTest` 要求 `:core:runtime-context` 已注册、被 architecture source root 报告、由 app-integration 消费，且 app shell 不直接依赖。
- 该模块必须是纯 Kotlin/JVM，不得 import Android、Compose、feature/app model、`AppStrings` 或 `R`。
- app root 下 `core/runtime/context` 不应再保留生产 Kotlin 文件；仅允许显式 adapter seam 位于 `app/src/main/java/com/astrbot/android/di/runtime/context`。

`runtime-search`：

- `RuntimeSearchBoundaryContractTest` 要求 `:core:runtime-search` 已注册并由 source root 报告。
- 统一搜索的 API provider、profile search、local model/policy/relevance/parser/engine/crawler/provider、prompt guidance 和 trigger rules 均位于 `core/runtime-search/src/main/java/com/astrbot/android/core/runtime/search/**`。
- `core/runtime-search` 不得依赖 `:app`、`:app-integration`、`:core:runtime` 或 `:feature:*`，也不得 import app model、feature、`R`、`AppStrings`、`AndroidWebSearchPromptStringProvider` 或 `AppLogger`。
- app 侧仅允许保留 Android prompt string adapter。

`runtime-container`：

- `RuntimeContainerBoundaryContractTest` 要求 `:core:runtime-container` 已注册、被 source root 报告、由 app-integration 消费，且 app shell 不直接依赖。
- `CommandRunner`、`ContainerRuntimeController`、`ContainerRuntimeInstallerPort`、`RuntimeBridgeController` 等跨层 contract 归 `:core:runtime-container`。
- 粗粒度 `:core:runtime` 不再拥有 container source；app 仅允许保留 `ContainerBridgeService` Android service adapter。
- 生产热路径不得调用旧 static container facade；命令跨层必须使用 `CommandSpec` / `CommandRunner`，不得把 `sh -c` 当成跨层协议。
- `ContainerRuntimeInstaller` 必须注入 `RuntimeSecretStore`，rootfs 必需制品解压失败需要记录并抛出。

`runtime-audio`：

- `RuntimeAudioBoundaryContractTest` 要求 `:core:runtime-audio` 已注册、被 source root 报告、由 app-integration 消费，且 app shell 不直接依赖。
- 通用音频 contract 与 TTS helper 位于 `core/runtime-audio/src/main/java/com/astrbot/android/core/runtime/audio/**`。
- `:core:runtime-audio` 不得 import app、feature、business model、db model 或 di 实现。
- QQ silk、voiceasset storage、voice clone owner 不得吸收到 generic core audio；`TencentSilkEncoder.kt`、`TtsVoiceAssetRepository.kt`、`VoiceCloneService.kt` 不应出现在 `core/runtime-audio`。

`runtime-secret` / `runtime-session` / `runtime-cache`：

- `RuntimeSecretSessionCacheBoundaryContractTest` 要求 `:core:runtime-secret`、`:core:runtime-session`、`:core:runtime-cache` 和 `:core:runtime-container` 已注册、被 source root 报告、由 app-integration 显式消费。
- 粗粒度 `:core:runtime` 不再拥有 `secret`、`session` 或 `cache` source。
- 生产热路径应使用 injected `RuntimeSecretStore` 或 `SessionLockCoordinator`，不得导入 legacy static secret/session object。

`runtime-llm`：

- `LlmSourceContractTest` 要求 `LlmInvocationContracts.kt`、`LlmClientPort.kt`、`LlmProviderProbePort.kt`、`StreamingResponseSegmenter.kt` 存在于 `core/runtime-llm`。
- app LLM package 只能保留 Android adapter 或明确 compat facade：`ChatCompletionService.kt`、`ChatCompletionServiceLlmClient.kt`、`HiltLlmProviderProbePort.kt`、`LlmMediaService.kt`。
- feature code 不得直接 import `ChatCompletionService`；生产代码不得在 Hilt module/definition site 之外 direct new `DefaultRuntimeLlmOrchestrator()`。

`runtime-tool`：

- 当前只包含 `ToolSourceContracts.kt`、`ToolDescriptorCachePolicy.kt`、`ToolJsonValueNormalizer.kt`。
- `:core:runtime-context` 依赖该模块；plugin/toolsource 细节仍需在后续 `plugin-platform` 模块确认。

`runtime-persistence`：

- `RuntimePersistenceBoundaryContractTest` 扫描当前 `feature/*/runtime` 与 `core/runtime*` source roots。
- `runtime-persistence-allowlist.txt` 当前没有非注释 entry。
- feature/core runtime 不应直接 import `com.astrbot.android.data.db.*`；若后续出现既有债务，只能用精确 temporary allowlist，不得使用通配符或目录级豁免。

## 合同与回归入口

本轮只读取合同测试，不运行测试。相关入口包括：

- `CoreDbDaoOwnerContractTest`
- `CoreCommonBoundaryContractTest`
- `RuntimeContextBoundaryContractTest`
- `RuntimeContainerBoundaryContractTest`
- `RuntimeSearchBoundaryContractTest`
- `RuntimeAudioBoundaryContractTest`
- `RuntimeSecretSessionCacheBoundaryContractTest`
- `RuntimePersistenceBoundaryContractTest`
- `LlmSourceContractTest`
- `ModuleDependencyGraphContractTest`
- `StaticRepositoryUsageContractTest`
- `GlobalSingletonAllowlistContractTest`
- `AppStartupChainContractTest`
- `StrictHiltOnlyStartupHotspotSourceTest`

对应回归命令仍由后续验证场景执行，文档场景不得声称已运行：

```powershell
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

## 当前债务与阻塞项

- `dao-owner-allowlist.txt` 当前仍有 2 条 phase-2 精确 allowlist：
  - `feature/qq/data/NapCatLoginLocalStore.kt` 使用 `SavedQqAccountDao`。
  - `feature/resource/data/FeatureResourceCenterRepository.kt` 使用 `ConfigAggregateDao`。
- `static-repository-usage-allowlist.txt` 当前无生产 allowlist entry；后续不得新增静态 repository facade 使用。
- `global-singleton-allowlist.txt` 当前有 2 条 permanent entry：`AppStrings.kt` 和 `feature/qq/runtime/QqSessionKeyFactory.kt`。
- `runtime-persistence-allowlist.txt` 当前无非注释 entry；runtime 直接 import db 层应视为回归。
- `core/db` 仍物理集中多 feature 的 Room entity / DAO；feature data 只能按 owner DAO 使用，跨 owner 入口必须有精确 allowlist。
- `:core:runtime` 仍是 coarse runtime 剩余壳层，后续不得把 context/search/container/audio/secret/session/cache/tool/llm owner 写回其中。
- `core/runtime-context/bin/` 与 `core/runtime-tool/bin/` 是生成物或缓存路径，不作为源码事实。
- 本轮未运行 Gradle 或测试，因此不能把本模块状态扩展为构建通过或全项目文档完成。

## 旧文档判断

- `docs/02_数据真源_Room_Repository_备份基线.md`：保留为 `core-foundation-and-db` 辅助证据；其中 Room v22 仍与当前代码一致，但数据库入口应以当前 `core/db/src/main/java/com/astrbot/android/data/db/AstrBotDatabase.kt` 为准，不再使用旧 `app/src/main/java/.../AstrBotDatabase.kt` 路径。
- `docs/02` 中关于 repository port 统一集中在 `di/hilt/RepositoryPortModule.kt` 的描述已不完整；当前绑定分散在 app-integration 的 feature-specific bindings 中。
- `docs/02` 中关于 backup data port 位于 `core/db/backup/*DataPort.kt` 的描述已过期；当前 data port contract 位于 `feature/settings/api/.../backup`。
- `docs/04_聊天会话_App内消息链路.md`：runtime context 和 LLM orchestration 相关片段可作为辅助证据；chat/conversation 业务主线仍需在 `chat-and-conversation` 模块确认。
- `docs/06_STT_TTS_声音克隆_资产.md`：音频 runtime 相关片段必须以当前 `core/runtime-audio/**` 为准；其中旧 `core/runtime/audio/*` 和 `TencentSilkEncoder.kt` 口径已不能作为当前事实。
- `docs/07_插件平台_模型_安装_执行_治理.md`：tool/search/LLM runtime 相关片段只作辅助证据；plugin runtime、host capability 与 tool source 仍需在 `plugin-platform` 模块确认。
- `docs/00_当前基线与迁移摘要.md`：只作为跨模块迁移辅助证据，不能替代本模块代码事实。
- 本轮未移动、未删除 `docs/00` 到 `docs/11` 中任何文件。

## 下一步

本文件已归档，不再作为 current 入口。当前第 3 和第 4 模块分别见 `docs/context/03-核心基础与数据库.md` 和 `docs/context/04-核心运行时.md`。
