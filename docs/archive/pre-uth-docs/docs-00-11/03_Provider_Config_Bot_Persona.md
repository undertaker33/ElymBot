# 03 Provider、Config、Bot、Persona

> 文档层级：模块背景层
> 阅读时机：当你准备修改本编号对应模块，或需要确认其当前真源、测试入口与易错点时再读本文。
> 默认加载顺序：`README.md` -> `../AGENTS.md` -> （如需统一基线）`00_当前基线与迁移摘要.md` -> 本文。
> 交叉补读：若问题跨模块，回到相关编号文档；若需要整体执行链，再补 `11_全链路执行流程图.md`。
> 说明：本文保留独立基线；以下“当前代码基线”只服务于本文，不替代其他模块文档的独立基线。

## 1. 当前代码基线

- 基线提交：`e495263`（`Release v0.8.5`；本轮覆盖 `d060c5e..e495263`，含 `113dc12 / v0.8.2`、`a6ffa95 / v0.8.3`、`b790abce / v0.8.4`）
- 共享模型文件：`app/src/main/java/com/astrbot/android/model/AppModels.kt`
- 当前主真源已迁到 feature：
  - `feature/provider/data/FeatureProviderRepository.kt`
  - `feature/config/data/FeatureConfigRepository.kt`
  - `feature/bot/data/FeatureBotRepository.kt`
  - `feature/persona/data/FeaturePersonaRepository.kt`

## 2. feature-first 分层

当前四个核心 profile 模块都至少包含：

- `data`：Feature repository / Feature*PortAdapter / compatibility shell
- `domain`：repository port
- `presentation`：screen / ViewModel
- `runtime`：预留或 feature 私有 runtime 代码

示例：

- Config UI：`feature/config/presentation/ConfigDetailScreen.kt`
- Config ViewModel：`feature/config/presentation/ConfigViewModel.kt`，package 仍是 `com.astrbot.android.ui.viewmodel`
- Config port：`feature/config/domain/ConfigRepositoryPort.kt`
- Config repository：`feature/config/data/FeatureConfigRepository.kt`

不要再按旧路径 `ui/config/*` 或 `data/ConfigRepository.kt` 找主实现。

## 3. 当前职责边界

### 3.1 Provider

`ProviderProfile` 仍负责：

- provider type
- base URL / model / API key
- `ProviderCapability`
- multimodal / streaming / STT / TTS capability 状态
- TTS voice options
- search provider capability（`ProviderCapability.SEARCH`）

新代码优先依赖 `ProviderRepositoryPort`。当前生产 Hilt binding 是 `FeatureProviderRepositoryPortAdapter`；不要再把 `LegacyProviderRepositoryAdapter` 写成现状。

`a6ffa95` 后 Provider catalog 还要按搜索能力理解：

- 新增搜索 provider 类型：`TAVILY_SEARCH`、`BRAVE_SEARCH`、`BOCHA_SEARCH`、`BAIDU_AI_SEARCH`
- 这些 provider 的默认 capability 是 `SEARCH`
- Provider UI 支持按 capability 过滤；搜索 provider 不进入聊天 provider 选择，而是供 `UnifiedSearchPort` 的 configured provider 阶段使用

Provider 的运行时能力也单独抽到了：

- contract：`feature/provider/api/src/main/java/com/astrbot/android/feature/provider/api/runtime/ProviderRuntimePort.kt`
- implementation：`feature/provider/runtime/src/main/java/com/astrbot/android/feature/provider/runtime/DefaultProviderRuntimePort.kt`

`ProviderViewModel` 通过这个 port 做模型拉取、multimodal/native streaming 探测、STT/TTS 探测、Sherpa/TTS 资产状态与语音合成，不应再直接散落调用 `ChatCompletionService`、`SherpaOnnxBridge`、`RuntimeAssetRepository`。

`32dd105` 之后还要明确一层：

- `ProviderRuntimePort` 的 provider capability / STT / TTS 探测底层依赖是 `core/runtime/llm/LlmProviderProbePort.kt`
- 当前 production 实现是 `core/runtime/llm/HiltLlmProviderProbePort.kt`
- `LegacyLlmProviderProbeAdapter` 已不是当前生产文件口径，只能当历史 compat seam 理解

第 25 期后 Provider 运行时能力继续收口到 injected owner/port：

- `ProviderRuntimePort` 现在同时暴露 `providers` 与 `voiceAssets`
- 导入、保存、重命名、删除 voice asset / binding 的入口也通过这个 port 下沉
- 这些能力不应再绕回静态 `RuntimeAssetRepository` / `TtsVoiceAssetRepository` helper；底层改看 `feature/voiceasset:data` 的 `RuntimeAssetStateOwner` / `TtsVoiceAssetRepository` 与 `feature/voiceasset:api` ports

### 3.2 Config

`ConfigProfile` 仍是运行时行为控制面，负责：

- 默认 provider 选择
- STT / TTS / streaming / web search / proactive 开关
- scheduled task 是否注入最近会话上下文：`includeScheduledTaskConversationContext`
- QQ 规则：wake words、whitelist、rate limit、reply format
- context strategy：`contextLimitStrategy / maxContextTurns / dequeueContextTurns / llmCompress*`
- 兼容字段：`mcpServers / skills`

但要注意：MCP / skills 的新资源真源正在迁往 Resource Center。

`e495263` 后需要额外记住：

- `webSearchEnabled` 不只是打开工具 descriptor，也会让 `PromptAssembler` 按 `WebSearchTriggerRules` 注入新闻/天气/实时类 guidance
- `proactiveEnabled` 现在还会影响 host fallback：App Chat / QQ 里如果用户明显在设置提醒但模型没有调用 `create_future_task`，`ScheduledTaskIntentGuard` 可尝试兜底创建任务
- `includeScheduledTaskConversationContext` 只影响 scheduled task trigger；普通用户消息仍按原 `messageWindow` 走

`b045602` 下与 Config 最容易写错的现实是 selected state：

- `FeatureConfigRepository.selectedProfileId` 当前由 `AppPreferenceDao.observeValue(PREF_SELECTED_PROFILE_ID)` 回流驱动
- `FeatureConfigRepository.select(profileId)` 当前只负责持久化所选 profile ID
- `restoreProfiles(...)` 会用 `applyInMemoryRestore(...)` 做短期回填，但最终仍以 DAO + AppPreference 同步状态为真源

### 3.3 Bot

`BotProfile` 仍负责平台实例身份：

- 绑定 QQ UIN
- trigger words
- default provider / persona / config
- bridge endpoint
- auto reply

`FeatureBotRepository` 现在同样已切到“持久化选中项驱动状态”的口径：

- `selectedBotId` 真源来自 `AppPreferenceDao.observeValue(PREF_SELECTED_BOT_ID)`
- `select(botId)` 只持久化 selected bot，不再即时改 `_selectedBotId`
- `FeatureConversationRepository.setSelectedBotIdProvider { selectedBotId.value }` 依赖这个 selected state 作为 conversation/runtime 衔接入口

### 3.4 Persona

`PersonaProfile` 仍负责：

- system prompt
- enabled tools
- default provider
- enabled 状态

`PersonaToolEnablementSnapshot` 仍是运行时工具启用快照。

## 4. Config 与 Resource Center 的关系

这是 v0.6.8 之后最重要的新边界。

旧配置：

- `ConfigProfile.mcpServers`
- `ConfigProfile.skills`

新资源中心：

- `ResourceCenterItem`
- `ConfigResourceProjection`
- `ResourceCenterCompatibilitySnapshot`

当前 runtime 装配时，`RuntimeContextResolver` 不再只直接读 `config.mcpServers / config.skills`，而是通过：

1. `RuntimeContextDataPort.compatibilitySnapshotForConfig(config)`
2. `RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(...)`
3. 输出 `mcpServers / promptSkills / toolSkills`

`ConfigProfile.skills` 仍保留在 `ResolvedRuntimeContext.skills`，但 prompt/tool skill 的新主路径是 Resource Center projection。

## 5. RuntimeContextResolver 现在如何读这四类对象

入口：`core/runtime/context/RuntimeContextResolver.kt`

它通过 `RuntimeContextDataPort` 读取：

- `resolveConfig(configProfileId)`
- `listProviders()`
- `findEnabledPersona(personaId)`
- `session(sessionId)`
- `compatibilitySnapshotForConfig(config)`

这个 port 由 `di/RuntimeContextDataPorts.kt` 提供 production data port，再由 `di/hilt/RuntimeServicesModule.kt` 注入 resolver；不要再把它写成 `AppBootstrapper.bootstrap()` 手写安装的 registry。

`e495263` 后 scheduled task trigger 下还有一条分支：

- 普通用户消息：`messageWindow = session.messages.takeLast(contextWindow)`
- scheduled task：`messageWindow = emptyList()`
- 若 config 开启 `includeScheduledTaskConversationContext`：`scheduledTaskContextWindow` 会从同一 session 取最近 user/assistant 消息，作为只读背景交给 `PromptAssembler`

## 6. 当前初始化与装配方式

`fb8e7ff` 下 profile 相关生产主线已经继续收口到 Hilt，而不是 startup initializer：

- `RepositoryInitializationStartupChain.kt` 当前是过渡空壳，不再承担 profile repository 初始化主线
- `ProviderRepositoryInitializer.kt` 已删除
- `ProviderRepositoryWarmup.kt` 仍保留符号，但 `warmUp()` 现在是 no-op compatibility shell
- repository port 由 `di/hilt/RepositoryPortModule.kt` 提供
- ViewModel 依赖由 `di/hilt/ViewModelDependencyModule.kt` 提供
- Bot / Provider / Config / Persona ViewModel 都是 `@HiltViewModel`
- Compose 页面使用 `hiltViewModel()`

初始化/装配规则由 `RepositoryPortSourceContractTest.kt`、`StrictHiltOnlyContractTest.kt`、`NoLegacyAdapterContractTest.kt` 继续保护：

- domain port 不 import legacy repository
- 生产绑定按 `Feature*PortAdapter` 理解
- 不要把测试 contract / compat wrapper 写回生产 DI

注意：Hilt module 是依赖装配层，不是新的业务真源。Provider / Config / Bot / Persona 的数据真源仍是对应 `Feature*Repository` 与 Room mapper。

## 7. 当前删除与事务边界

这是 `b045602` 里最值得明确写进 handoff 的一组边界。

### 7.1 Provider / Persona 删除前必须过引用保护

- `FeatureProviderRepository.delete(id)` 现在会先调 `ProviderReferenceGuard.requireNotInUse(id)`
- `FeaturePersonaRepository.delete(id)` 现在会先调 `PersonaReferenceGuard.requireNotInUse(id)`

所以“列表里还能删”不等于“当前代码允许删”；必须先看引用关系。

### 7.2 Config / Bot 删除已下沉到 Phase3 transaction service

当前真源：

- `Phase3DataTransactionService`
- `RoomPhase3DataTransactionService`
- `feature/config/domain/Phase3DataTransactionService.kt`
- `feature/config/data/RoomPhase3DataTransactionService.kt`
- `di/hilt/ViewModelDependencyModule.kt`

当前语义：

- `ConfigViewModel` 直接注入 `Phase3DataTransactionService` 并调 `deleteConfigProfile(profileId)`
- `BotViewModel` 直接注入 `Phase3DataTransactionService` 并调 `deleteBotProfile(botId)`
- `RoomPhase3DataTransactionService` 在 Room transaction 中处理 config 删除、bot 绑定回退、bot 删除、selected state 和 conversation 清理
- `ConfigViewModel.delete(profileId)` 不再自己拼 `delete() + replaceConfigBinding()`

如果后续窗口又把这些顺序拉回 ViewModel 或 Screen 层，或者恢复 `Phase3DataTransactionServiceRegistry`，基本就是把 `bbb2f5f` 的真实删除边界改坏了。

## 8. 当前测试入口

- `app/src/test/java/com/astrbot/android/architecture/RepositoryPortSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/FeatureFirstBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/Phase7RootBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/HiltFoundationContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/HiltExitContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/PostHiltRound1ContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/LlmSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/data/HiltViewModelDependenciesTransactionTest.kt`
- `app/src/test/java/com/astrbot/android/data/db/config/ConfigMappersTest.kt`
- `app/src/test/java/com/astrbot/android/feature/provider/ProviderCatalogSearchCapabilityTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/search/profile/ProviderRepositorySearchProviderTest.kt`
- `app/src/test/java/com/astrbot/android/core/runtime/context/RuntimeContextResolverProviderTest.kt`
- `app/src/test/java/com/astrbot/android/feature/bot/data/FeatureBotRepositorySelectedStateTest.kt`
- `app/src/test/java/com/astrbot/android/feature/config/data/FeatureConfigRepositorySelectedStateTest.kt`
- `app/src/test/java/com/astrbot/android/data/PersonaRepositoryTest.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/ProviderViewModelTest.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/BotViewModelTest.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/ConfigViewModelTest.kt`
- `app/src/androidTest/java/com/astrbot/android/ui/ConfigScreenSmokeTest.kt`

## 9. 易错点

- Config 仍是 context/proactive/web-search 等运行时策略入口，但 MCP/skills 资源化后要同时看 Resource Center。
- 文件物理路径迁到了 `feature/*`，但 package/import 可能仍是旧名字。
- 不要让 domain port 依赖 Android、Compose、旧 data/runtime 单例。
- 不要在 core 里 import feature repository；新增生产依赖通过 Hilt module、constructor injection、已有 production port 或启动 composition root 接，不要新增 port registry / service locator。
- 不要把 `Default*ViewModelDependencies` / `Hilt*ViewModelDependencies` 当当前生产依赖入口；`di/AstrBotViewModelDependencies.kt` 已删除，当前生产入口是各 ViewModel 直接注入 domain port / runtime port / bindings。
- 不要把 `LegacyLlmProviderProbeAdapter` 写成当前 provider/config 侧 production probe 入口；当前真源是 `LlmProviderProbePort` + `HiltLlmProviderProbePort`。
- 改 Bot / Config selected state 时，要同步看 `AppPreferenceDao`、selected-state tests 和 backup restore 对 selected ID 的处理。
- 改 Provider / Persona 删除逻辑时，不要绕过 `ProviderReferenceGuard` / `PersonaReferenceGuard`。
