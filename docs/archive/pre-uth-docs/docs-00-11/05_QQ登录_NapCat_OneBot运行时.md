# 05 QQ 登录、NapCat、OneBot 运行时

> 文档层级：模块背景层
> 阅读时机：当你准备修改本编号对应模块，或需要确认其当前真源、测试入口与易错点时再读本文。
> 默认加载顺序：`README.md` -> `../AGENTS.md` -> （如需统一基线）`00_当前基线与迁移摘要.md` -> 本文。
> 交叉补读：若问题跨模块，回到相关编号文档；若需要整体执行链，再补 `11_全链路执行流程图.md`。
> 说明：本文保留独立基线；以下“当前代码基线”只服务于本文，不替代其他模块文档的独立基线。

## 1. 当前代码基线

- 基线提交：`e495263`（`Release v0.8.5`；本轮覆盖 `d060c5e..e495263`，含 `113dc12 / v0.8.2`、`a6ffa95 / v0.8.3`、`b790abce / v0.8.4`）
- QQ 模块主目录：`feature/qq/{api,data,runtime,presentation}/`；`feature/qq/impl` 当前不承载 QQ production 主线；`app/src/main/java/com/astrbot/android/feature/qq/` 不再承载 QQ production 主线。

## 2. 当前主文件

建议按这个顺序读：

1. `feature/qq/runtime/QqOneBotBridgeServer.kt`
2. `feature/qq/runtime/OneBotReverseWebSocketTransport.kt`
3. `feature/qq/runtime/QqOneBotRuntimeGraphFactory.kt`
4. `feature/qq/runtime/QqRuntimeGraphNodeFactories.kt`
5. `feature/qq/runtime/QqOneBotRuntimeGraph.kt`
6. `feature/qq/runtime/OneBotPayloadParser.kt`
7. `feature/qq/runtime/OneBotServerAdapter.kt`
8. `feature/qq/runtime/QqMessageRuntimeService.kt`
9. `feature/qq/runtime/QqPluginExecutionService.kt`
10. `feature/qq/runtime/QqPluginDispatchService.kt`
11. `feature/qq/runtime/QqBotCommandRuntimeService.kt`
12. `feature/qq/runtime/QqStreamingReplyService.kt`
13. `feature/qq/runtime/QqNewsReplyFormatter.kt`
14. `feature/qq/runtime/QqRuntimeProfileResolver.kt`
15. `feature/qq/runtime/QqAudioAttachmentMaterializer.kt`
16. `feature/qq/runtime/DefaultQqProviderInvoker.kt`
17. `feature/qq/runtime/QqReplySender.kt`
18. `feature/qq/data/FeatureQqConversationPortAdapter.kt`
19. `feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt`
20. `feature/qq/data/NapCatBridgeRepository.kt`
21. `feature/qq/data/NapCatLoginRepository.kt`
22. `feature/qq/presentation/QQLoginScreens.kt`
23. `core/runtime/container/ContainerRuntimeInstaller.kt`
24. `core/runtime/container/ContainerRuntimeEntryPoint.kt`
25. `core/runtime/container/ContainerBridgeRuntimeSupport.kt`
26. `app/src/main/assets/runtime/scripts/root_launcher.sh`

旧路径 `runtime/OneBotBridgeServer.kt`、`runtime/OneBotPayloadCodec.kt` 已不是当前主实现。

第 24 期后，QQ data/runtime/presentation 已物理拆细到对应 feature 模块。遇到 QQ 登录态、NapCat data、OneBot bridge、QQ UI 问题时，优先按 `feature/qq/data`、`feature/qq/runtime`、`feature/qq/presentation` 查找，不要回到 `:feature:qq:impl` 或 app 内旧路径判断生产 owner。

还要注意 `b045602` 的 Manifest / network security 配套收紧：

- App 全局 `usesCleartextTraffic=false`
- cleartext 白名单只剩 `127.0.0.1` / `localhost`

所以 QQ / NapCat / reverse WS 当前必须继续走 loopback，本地之外的 cleartext 口径不要写成现状。

## 3. 运行时依赖注入方式

`QqOneBotBridgeServer.kt` 不再直接抓所有旧 singleton，而是通过：

- `QqOneBotRuntimeDependencies`
- `QqBridgeRuntime` / `HiltQqOneBotBridgeRuntime`

历史上由手写 app container 或静态 provider callback 注入；当前不要再按这个入口写。旧口径里包含：

- bot/config/persona/provider ports
- conversation port
- platform config port
- `LegacyRuntimeOrchestratorAdapter`
- `DefaultQqProviderInvoker(LegacyChatCompletionServiceAdapter())`

这让 QQ feature runtime 不直接 import 旧 repository 或 `ChatCompletionService`。

`bbb2f5f` 后生产装配来源已改为：

- `di/AppBootstrapper.kt`
  - 当前只负责委托 `AppStartupRunner.run()`
- `di/startup/BootstrapPrerequisitesStartupChain.kt`
  - 调 `qqBridgeRuntime.initialize(application)`
- `di/startup/RuntimeLaunchStartupChain.kt`
  - 调 `qqBridgeRuntime.start()`
- `di/hilt/RuntimeServicesModule.kt`
  - 提供 `QqOneBotRuntimeDependencies`
  - 提供 `DefaultQqProviderInvoker`
  - 绑定 `QqBridgeRuntime -> HiltQqOneBotBridgeRuntime`
  - 绑定 `QqScheduledMessageSender -> HiltQqOneBotBridgeRuntime`
- `di/hilt/RepositoryPortModule.kt`
  - 提供 QQ 所需 repository ports

`7eb953c` 后 `QqOneBotRuntimeDependencies` 还显式携带：

- `appChatPluginRuntime`
- `pluginV2DispatchEngine`
- `PluginFailureStateStore`
- `PluginScopedFailureStateStore`
- `PluginHostCapabilityGatewayFactory`
- `LlmProviderProbePort`
- `PluginRuntimeLogBus`

所以 QQ runtime graph / dispatch / streaming / provider invoke 当前都应通过 injected dependencies 走 Hilt-owned production mainline，不要再回到 static provider accessors。

`fb8e7ff` 下 QQ runtime graph 还继续收口了一层：

- 新增 `QqOneBotRuntimeGraphFactory.kt` 与 `QqRuntimeGraphNodeFactories.kt`
- `QqMessageRuntimeService`、`QqPluginDispatchService`、`QqBotCommandRuntimeService`、`QqStreamingReplyService` 不应再在 runtime graph 文件里 direct new
- `NoManualRuntimeSubgraphContractTest.kt` 会保护这条 factory 化的 Hilt 主线
- QQ 相关 repository port 当前按 `FeatureQqConversationPortAdapter` / `FeatureQqPlatformConfigPortAdapter` 理解

`d060c5e` 下 QQ 插件 ingress 与 storage 还补了一层会话语义：

- `QqPluginDispatchService.handlePluginCommand(...)` 会把 `MessageSessionRef(...).unifiedOrigin` 作为 `sessionUnifiedOrigin` 传入 V2 message dispatch
- `IncomingQqMessage.toPluginMessageEvent(...)` 会把非空 `sessionUnifiedOrigin` 写入 event extras
- `PluginV2BootstrapHostApi.storage.session` 依赖这个值确定 session scope；没有它会返回 structured error `missing_session_scope`
- 不要把 QQ session storage 写成只看 OneBot conversationId，也不要 fallback 到 plugin scope

旧 `AstrBotAppContainer` / `ElymBotAppContainer` 不是当前生产入口。`QqOneBotBridgeServer` object 仍在 `QqOneBotBridgeServer.kt`，但主要作为 test/compat 静态入口；生产 app 启动不再手动 install 它的 runtime dependencies。

## 4. OneBot 入站链路

当前高层顺序：

1. WebSocket 收到 OneBot payload
2. `OneBotReverseWebSocketTransport` 在本地 reverse WebSocket endpoint 接入 OneBot，授权 token 使用当前 bridge token（文档不记录具体值）
3. `QqOneBotBridgeServer.handlePayload(...)` 识别 action response 或交给 adapter
4. `OneBotPayloadParser` 解析
5. `OneBotServerAdapter` 把 payload 交给 `QqRuntimePort`
6. `BaseQqOneBotBridgeRuntime` / `HiltQqOneBotBridgeRuntime` 用 Hilt 提供的 `QqOneBotRuntimeDependencies` + `QqRuntimeGraphFactory` 构建 `QqOneBotRuntimeGraph`
7. `QqMessageRuntimeService.handleIncomingMessage(...)` 执行 QQ 规则链与插件 ingress
8. 构造 `RuntimeIngressEvent`
9. `RuntimeContextResolver.resolve(...)`
10. 通过 `RuntimeLlmOrchestratorPort` 调 `DefaultRuntimeLlmOrchestrator.dispatchLlm(...)`
11. `QqStreamingReplyService` 准备/发送 pseudo streaming 或 voice streaming reply
12. `QqReplySender` / `QqOneBotOutboundGateway` 发送 OneBot action

`e495263` 后 QQ 共享 LLM callback 里还接了两条新分支：

- `ScheduledTaskIntentFallbackResponder`：与 App Chat 一样，proactive enabled 且模型漏调 `create_future_task` 时尝试 host fallback 创建提醒任务
- `QqStreamingReplyService.deliverNewsSearchResultIfNeeded(...)`：当 tool name 是 `web_search` 且原消息命中 news intent，会先把搜索事实直接发给 QQ，再把 tool result 改成 commentary-only follow-up，避免新闻事实只停留在模型内部

`b045602` 下 reverse WS 授权口径也变得更严格：

- 鉴权 helper 真源是 `feature/qq/runtime/OneBotReverseWebSocketTransport.kt` 里的 `isAuthorizedOneBotReverseWebSocket(headers, authToken)`
- `authToken` 为空时直接拒绝
- `Authorization` header 支持 `Bearer <token>` 或直接 token
- header token 必须与当前 bridge token 全量匹配

不要再把“连到本地端口就默认通过”写成当前授权行为。

QQ 规则仍然包括：

- wake word
- whitelist
- rate limit
- reply policy
- conversation title/session key
- keyword detector

这些文件已迁到 `feature/qq/runtime/*`。

`QqOneBotBridgeServer` 当前应保持薄胶水层：transport、runtime graph、payload 分发、scheduled message 入口。插件 dispatch、bot command、profile resolve、streaming reply、outbound payload 不应回填到 bridge。

## 5. Provider 调用边界

QQ provider 调用由：

- `feature/qq/runtime/DefaultQqProviderInvoker.kt`

负责把插件 pipeline 的 `PluginProviderRequest` 转成 core LLM contract：

- `LlmInvocationRequest`
- `LlmToolDefinition`
- `LlmStreamEvent`

所以 QQ runtime 不应再直接调用 `ChatCompletionService`。

`a6ffa95` 后 `QqRuntimeProfileResolver.resolveChatProvider(...)` 会先看当前 config 的 `defaultChatProviderId`，再看 bot default / preferred provider；可用 provider 仍必须具备 `ProviderCapability.CHAT`，不要把搜索 provider 写成 QQ chat provider。

## 6. scheduled task 向 QQ 投递

定时任务仍通过 QQ bridge 发送：

- `ScheduledTaskRuntimeExecutor`
- `ScheduledTaskLlmCallbacksFactory`
- `QqScheduledMessageSender`
- `HiltQqOneBotBridgeRuntime.sendScheduledMessage(...)`
- `QqOneBotOutboundGateway.sendScheduledMessage(...)`
- `QqReplySender`

当前 scheduled task 执行成功会返回 `CronJobDeliverySummary`，并记录到 `cron_job_execution_records`。

## 7. QQ 登录与 NapCat

登录/桥接数据已迁到：

- `feature/qq/data/NapCatBridgeRepository.kt`
- `feature/qq/data/NapCatLoginRepository.kt`
- `feature/qq/data/NapCatLoginService.kt`
- `feature/qq/data/NapCatLoginDiagnostics.kt`

UI 已迁到：

- `feature/qq/presentation/QQLoginScreens.kt`
- `feature/qq/presentation/QQLoginViewModel.kt`
- `feature/qq/presentation/QqLoginComponents.kt`

package 可能仍是旧 `com.astrbot.android.ui.qqlogin` / `ui.viewmodel`，以物理路径为准。

`v0.7.7 ~ v0.7.8` 这段里，QQ 登录链最值得写清楚的是：

- `NapCatLoginRepository`
  - 本地初始化 `NapCatLoginLocalStore`
  - 会保存 / 合并 `quickLoginUin` 与 `savedAccounts`
  - `refreshQrCode()` 有 cooldown + in-flight guard
  - `logoutCurrentAccount()` 会跑 `logout_qq.sh`，随后 stop/start bridge 并轮询登录状态
  - 会按 trigger 采样 `NapCatLoginDiagnostics`，但加了节流，避免刷日志
- `NapCatLoginService`
  - 通过 `AstrBotHttpClient` / `OkHttpAstrBotHttpClient` 调 NapCat WebUI
  - 先走 `/auth/login` 拿 Credential，再调各 QQLogin API
  - 区分 `AUTH_TOKEN_EXPIRED` / `AUTH_LOGIN_FAILED` / `API_BUSINESS_REJECTED` / `EMPTY_OR_MALFORMED_RESPONSE` / `NETWORK_FAILURE`
  - 日志会遮蔽 `configuredToken` / `credential` / `Authorization: Bearer ...`
  - quick login account 列表先尝试 `GetQuickLoginListNew`，再 fallback 到 legacy `GetQuickLoginList`
- `QQLoginViewModel`
  - 通过 `QQLoginPollingTracker` 管理可见页面
  - 当前只保留一个 polling job 覆盖多个 QQ 登录 screen
  - 当 `bridgeReady == true && !isLogin && qrCodeUrl.isBlank()` 时，会自动触发一次 `refreshQrCode()`
  - 当前 build marker helper 在 `buildQqLoginVersionMarker(...)`

## 8. 容器安装与 root launcher

NapCat/container runtime 当前还要同时看：

- `core/runtime/container/ContainerRuntimeInstaller.kt`
  - 负责复制脚本/资产、安装 runtime symlink、解压 rootfs、写默认 `NapCatBridgeConfig`
  - 默认 bridge config 仍指向本地 bridge endpoint：
    - endpoint：`<local reverse-ws endpoint>`
    - health：`<local health endpoint>`
- `core/runtime/container/ContainerBridgeService.kt`
  - 当前是 `@AndroidEntryPoint`
  - `handleStartBridge()` / `handleCheckBridge()` 会读取 runtime progress 文件、看 health endpoint、决定 running / process-running / stalled / failed
- `core/runtime/container/ContainerRuntimeEntryPoint.kt`
  - 是 `ContainerBridgeController` / `ContainerBridgeService` 获取 `ContainerBridgeStatePort` 与 `ContainerRuntimeInstaller` 的 Hilt entry point
- `core/runtime/container/ContainerBridgeRuntimeSupport.kt`
  - 从 `runtime/run/napcat_progress*` 读阶段文本
  - 当前内置 network-install 相关 progress label，例如：
    - `download-installer`
    - `installer-downloaded`
    - `run-installer`
- `app/src/main/assets/runtime/scripts/root_launcher.sh`
  - 优先使用 bundled installer assets
  - bundled 资源缺失时会 fallback 到 upstream installer 下载
  - 已有 install 修复 launcher shim 时，对 bundled shim 缺失是容忍的，不会直接 hard fail

不要把 NapCat runtime 文档写成“只支持 bundled 离线安装”，那已经不是当前真实代码。

## 9. 当前测试入口

- `app/src/test/java/com/astrbot/android/feature/qq/runtime/OneBotPayloadParserTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/OneBotServerAdapterTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/OneBotReverseWebSocketTransportTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/QqOneBotOutboundGatewayTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/QqPluginDispatchServiceTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/QqNewsReplyFormatterTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/QqRuntimeProfileResolverTest.kt`
- `app/src/test/java/com/astrbot/android/feature/qq/runtime/QqStreamingReplyServiceTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/OneBotBridgeServerTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/OneBotPayloadCodecTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqKeywordDetectorTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqRateLimiterTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqReplyFormatterTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqReplyPolicyEvaluatorTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqSessionKeyFactoryTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqWakeWordPolicyTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/qq/QqWhitelistMatcherTest.kt`
- `app/src/test/java/com/astrbot/android/data/NapCatLoginRepositoryTest.kt`
- `app/src/test/java/com/astrbot/android/data/NapCatLoginServiceTest.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/QQLoginViewModelTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/NapCatRuntimeScriptContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/PostHiltRound2PluginRuntimeContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/PostHiltRound3HostCapabilityContractTest.kt`
- `app/src/androidTest/java/com/astrbot/android/ui/qqlogin/SavedAccountDropdownTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/FeatureFirstBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/QqOneBotRuntimeGuardrailTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/NoManualRuntimeSubgraphContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/LlmSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/HiltFoundationContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RepositoryPortSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/AndroidManifestRuntimeContractTest.kt`

## 10. 易错点

- `QqOneBotBridgeServer` 是当前路径，不是旧 `OneBotBridgeServer`。
- 生产启动不要直接调 `QqOneBotBridgeServer.start()`；当前是 `AppBootstrapper -> QqBridgeRuntime -> HiltQqOneBotBridgeRuntime`。
- 不要在生产 bootstrap 里恢复 `QqOneBotBridgeServer.installRuntimeDependencies(...)` 或旧 `configureRuntimeDependenciesProvider`。
- `QqOneBotBridgeServer` 现在必须保持薄胶水层，不要把逻辑重新塞回 bridge server。
- 不要在 `QqOneBotRuntimeGraph.kt` / factory 里 direct new `QqMessageRuntimeService`、`QqPluginDispatchService`、`QqBotCommandRuntimeService`、`QqStreamingReplyService`；当前要经由 Hilt factories 组装。
- `QqMessageRuntimeService` 是 QQ 消息主链核心，但插件/command/profile/streaming 已继续拆到子服务，不要把它重新膨胀成万能类。
- `feature/qq/domain` 不能 import plugin runtime、旧 data singleton 或 `ChatCompletionService`。
- QQ 回包和 App Chat 本地交付不同：QQ 有 `PluginV2FollowupSender` 和 OneBot send result。
- QQ 插件 storage session scope 依赖 `sessionUnifiedOrigin`，不要只传 fallback conversation id。
- QQ 新闻搜索直送只针对 `web_search` 成功结果和 news intent；不要把所有 web_search 结果都直接分段发送。
- reverse WebSocket 当前路径和端口属于本地运行时配置，本文档不记录具体值；不要写回旧 server/transport 口径。
- `NapCatLoginRepository` / `QQLoginViewModel` 当前已经把轮询、自动 QR refresh、quick-login 保存合并到了统一链路；不要在 screen 层再开新的独立轮询 job。
- `ContainerBridgeController` / `ContainerBridgeService` 当前通过 `ContainerRuntimeEntryPoint` 获取 Hilt-owned installer/state；不要直接 new container installer 或回到 feature 层手写 singleton。
- `root_launcher.sh` 当前保留 upstream installer fallback；不要把运行时脚本说明写回“仅 bundled install”。
- 改 reverse WS 授权或本地端口白名单时，要同步看 `OneBotReverseWebSocketTransportTest.kt` 与 `AndroidManifestRuntimeContractTest.kt`。
