# 04 聊天会话、App 内消息链路

> 文档层级：模块背景层
> 阅读时机：当你准备修改本编号对应模块，或需要确认其当前真源、测试入口与易错点时再读本文。
> 默认加载顺序：`README.md` -> `../AGENTS.md` -> （如需统一基线）`00_当前基线与迁移摘要.md` -> 本文。
> 交叉补读：若问题跨模块，回到相关编号文档；若需要整体执行链，再补 `11_全链路执行流程图.md`。
> 说明：本文保留独立基线；以下“当前代码基线”只服务于本文，不替代其他模块文档的独立基线。

## 1. 当前代码基线

- 基线提交：`e495263`（`Release v0.8.5`；本轮覆盖 `d060c5e..e495263`，含 `113dc12 / v0.8.2`、`a6ffa95 / v0.8.3`、`b790abce / v0.8.4`）
- App Chat 已迁到 feature-first 结构
- 关键目录：`app/src/main/java/com/astrbot/android/feature/chat/`

## 2. 当前主链文件

建议按这个顺序读：

1. `feature/chat/domain/SendAppMessageUseCase.kt`
2. `feature/chat/domain/SendAppMessageUseCaseFactory.kt`
3. `feature/chat/domain/AppChatRuntimePort.kt`
4. `feature/chat/runtime/ChatRuntimeServiceFactories.kt`
5. `feature/chat/runtime/AppChatRuntimeService.kt`
6. `feature/chat/runtime/AppChatProviderInvocationService.kt`
7. `feature/chat/runtime/AppChatPreparedReplyService.kt`
8. `feature/chat/runtime/AppChatPluginCommandService.kt`
9. `feature/chat/presentation/AppChatSendHandler.kt`
10. `feature/chat/presentation/AppChatSendHandlerFactory.kt`
11. `feature/chat/presentation/ChatSessionController.kt`
12. `feature/chat/presentation/ChatViewModel.kt`
13. `feature/chat/presentation/ChatViewModelRuntimeBindings.kt`
14. `di/hilt/DefaultChatViewModelRuntimeBindings.kt`
15. `core/runtime/context/RuntimeContextResolver.kt`
16. `core/runtime/context/RuntimeContextResolverPort`
17. `feature/plugin/runtime/RuntimeLlmOrchestratorPort.kt`
18. `feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt`

## 3. 当前链路怎么走

高层顺序：

1. `ChatViewModel` 收到 UI 发送动作
2. `AppChatSendHandler` 把 presentation request 映射到 domain use case
3. `SendAppMessageUseCase` 负责：
   - 校验空消息
   - 写入 user message
   - 允许 `beforeRuntime` 插件阶段决定是否跳过模型
   - 创建/更新 assistant message
   - 把 runtime event 映射成 UI 可消费事件
4. `AppChatRuntimeService` 负责：
   - 构造 `RuntimeIngressEvent`
   - 通过 `ChatViewModelRuntimeBindings.runtimeContextResolverPort` 调 `RuntimeContextResolverPort.resolve(...)`
   - 通过 `RuntimeLlmOrchestratorPort` 调 `DefaultRuntimeLlmOrchestrator.dispatchLlm(...)`
   - 把 `PlatformLlmCallbacks` 接到 App Chat 本地 delivery
   - `e495263` 后在 `prepareReply(...)` 阶段接入 `ScheduledTaskIntentFallbackResponder`
5. `AppChatProviderInvocationService` 负责把插件 pipeline 的 `PluginProviderRequest` 转成 App Chat 既有 provider 调用，包括 tool call 与 streaming chunk 兼容。
6. `AppChatPreparedReplyService` 负责准备模型回复、附件映射和 TTS/voice streaming attachment。
7. `AppChatPluginCommandService` 负责 App 内插件 command / message ingress，不要塞回 `ChatViewModel`。
8. `ChatSessionController` 负责 bot/provider/session 选择、session 创建删除、STT/TTS 会话 flag 和 session binding。
9. `ChatViewModel` 继续处理 UI state、用户交互 glue、插件 ingress 前后状态等 presentation 责任。

`7eb953c` 下还要补一层装配事实：

- `DefaultChatViewModelRuntimeBindings` 不再 direct new 大量 runtime helper
- 当前生产装配改成：
  - `SendAppMessageUseCaseFactory`
  - `AppChatSendHandlerFactory`
  - `AppChatRuntimeServiceFactory`
  - `AppChatPluginCommandServiceFactory`
  - `AppChatProviderInvocationServiceFactory`
  - `AppChatPreparedReplyServiceFactory`
- 这些 factory 由 Hilt 注入，负责把 `LlmClientPort`、`LlmProviderProbePort`、`RuntimeLlmOrchestratorPort`、`PluginHostCapabilityGatewayFactory`、`PluginV2DispatchEngine` 等生产依赖串起来

`32dd105` 后还要再补一层 runtime 闭包事实：

- `EngineBackedAppChatPluginRuntime` 当前直接拿 Hilt-owned `PluginV2ActiveRuntimeStore`、`PluginV2DispatchEngine`、`PluginV2LifecycleManager`、`PluginRuntimeLogBus`
- `PluginRuntimeDependencyBridge.kt` 已删除，不要再把 App Chat runtime 写成先经由 bridge 回填再运行

`d060c5e` 后 App Chat 插件 ingress 还要记住：

- `/command` 入口不再由旧 `ExternalPluginTriggerPolicy` gating；V2 在线主链按 `PluginTriggerContracts.onlineHostTriggers` 理解
- `AppChatPluginCommandService` 会在 plugin event extras 中写入 `sessionUnifiedOrigin`
- `sessionUnifiedOrigin` 来自 `MessageSessionRef(platformId, messageType, originSessionId).unifiedOrigin`，供 `PluginV2BootstrapHostApi.storage.session` 作为 session scope
- 不要把 App Chat 的 session storage fallback 写成 plugin scope；缺会话上下文时应由插件 runtime 返回 `missing_session_scope`

`e495263` 后 App Chat 还要记住一条 proactive fallback：

- `AppChatRuntimeServiceFactory` 由 Hilt 注入 `ScheduledTaskIntentFallbackResponder`
- 如果用户文本明显是提醒/定时任务意图，且当前 config `proactiveEnabled == true`，但插件工具循环没有执行 `create_future_task`，fallback responder 会尝试通过 `ScheduledTaskIntentGuard` 创建 future task
- fallback 创建后会发起一次 non-stream provider follow-up，让模型只回复任务创建/失败结果

## 4. 与上一轮文档相比的变化

旧口径里 `ChatViewModel` 仍是主链核心。当前应改为：

- `ChatViewModel`：presentation state + 插件 ingress + UI glue
- `ChatSessionController`：session/bot/provider 选择与绑定同步
- `ChatViewModelRuntimeBindings`：Chat ViewModel 与 repository/runtime/Hilt graph 的生产 contract
- `DefaultChatViewModelRuntimeBindings`：当前 Hilt production 实现
- `SendAppMessageUseCase`：发送用例与消息更新策略
- `SendAppMessageUseCaseFactory`：发送 use case 的 Hilt-owned 装配入口
- `AppChatRuntimeService`：共享 runtime context + LLM orchestration glue
- `AppChatRuntimeServiceFactory`：App Chat runtime 的 Hilt-owned 装配入口
- `AppChatProviderInvocationService`：provider 调用适配
- `AppChatPreparedReplyService`：模型回复/附件/TTS 准备
- `AppChatPluginCommandService`：App 内插件 command / ingress
- `AppChatSendHandlerFactory` / `AppChatPluginCommandServiceFactory`：ChatViewModel 侧 send/plugin ingress 装配入口
- `DefaultRuntimeLlmOrchestrator`：插件 v2 LLM pipeline 共享实现
- `RuntimeOrchestrator`：静态兼容壳，不是新代码首选入口

不要把 runtime send 逻辑再塞回 `ChatViewModel`。

## 5. 共享上下文变化

`RuntimeContextResolver` 已迁到：

- `core/runtime/context/RuntimeContextResolver.kt`

它现在通过显式 `RuntimeContextDataPort` 获取数据，而不是直接 import repositories。`bbb2f5f` 后 `RuntimeContextDataRegistry` 不再是生产入口。

当前 runtime context 生产入口是：

- contract：`core/runtime/context/RuntimeContextDataPort.kt`
- resolver port：`core/runtime/context/RuntimeContextResolver.kt` 内的 `RuntimeContextResolverPort`
- production data port：`di/RuntimeContextDataPorts.kt` 的 `ProductionRuntimeContextDataPort`
- Hilt binding：`di/hilt/RuntimeServicesModule.kt`

解析结果里新增/强调：

- `promptSkills`
- `toolSkills`
- `toolSourceContext`
- `scheduledTaskContextWindow`
- Resource Center compatibility projection

Prompt 相关文件：

- `core/runtime/context/PromptAssembler.kt`
- `core/runtime/context/RuntimeResourceProjections.kt`
- `core/runtime/context/ToolSourceContext.kt`
- `core/runtime/search/WebSearchPromptGuidance.kt`

`e495263` 后 `PromptAssembler` 的当前职责还包括：

- 普通用户消息：继续注入 persona、prompt skills、QQ channel hint、real-world time、host capability guidance
- web search enabled 且文本命中 news/weather/realtime：注入 provider-agnostic 的 `web_search` 使用 guidance
- scheduled task trigger：注入 scheduler metadata，明确 note 是调度元数据，不是新的用户消息
- scheduled task context enabled：追加最近 user/assistant 消息作为 read-only 背景，避免模型把旧消息重新当作新任务

## 6. ToolSource 与 App Chat

App Chat 仍会走统一 tool registry。

当前 future toolsource 真源：

- `feature/plugin/runtime/toolsource/FutureToolSourceRegistry.kt`
- `feature/plugin/runtime/toolsource/FutureToolSourceContextResolver.kt`
- `feature/plugin/runtime/toolsource/McpToolSourceProvider.kt`
- `feature/plugin/runtime/toolsource/SkillToolSourceProvider.kt`
- `feature/plugin/runtime/toolsource/ActiveCapabilityToolSourceProvider.kt`
- `feature/plugin/runtime/toolsource/ContextStrategyToolSourceProvider.kt`
- `feature/plugin/runtime/toolsource/WebSearchToolSourceProvider.kt`

`fb8e7ff` / `e495263` 下要按这条装配路径理解：

- `FutureToolSourceRegistry` 是 Hilt `@Singleton`
- context 由 `FutureToolSourceContextResolver` 基于 `ConfigRepositoryPort + ResourceCenterPort` 投影生成
- `ActiveCapabilityToolSourceProvider` / `WebSearchToolSourceProvider` 都是 injected provider，不再走应用启动时的静态初始化
- `WebSearchToolSourceProvider` 当前通过 `UnifiedSearchPort` 执行搜索，不再维护 plugin 私有 search policy

Skill 现在分两类：

- Prompt Skill：进入 `PromptAssembler`
- Tool Skill：进入 `SkillToolSourceProvider`

## 7. App Chat 与 QQ/cron 的共同点和差异

共同点：

- 都使用 `RuntimeContextResolver`
- 都通过 `RuntimeLlmOrchestratorPort` 进入 `DefaultRuntimeLlmOrchestrator.dispatchLlm(...)`
- 都使用插件 v2 LLM pipeline 和 ToolSource registry

App Chat 差异：

- `followupSender = null`
- delivery 主要写回本地 conversation
- user/assistant message 的创建与增量更新由 `SendAppMessageUseCase` 负责

QQ 差异：

- 有 `PluginV2FollowupSender`
- 通过 `QqReplySender` / OneBot 回包

Cron 差异：

- 通过 `ScheduledTaskRuntimeExecutor`
- 成功交付会生成 `CronJobDeliverySummary`

## 8. 当前测试入口

- `app/src/test/java/com/astrbot/android/feature/chat/domain/SendAppMessageUseCaseTest.kt`
- `app/src/test/java/com/astrbot/android/feature/chat/domain/ChatMessageUpdatePolicyTest.kt`
- `app/src/test/java/com/astrbot/android/feature/chat/runtime/AppChatRuntimeServiceContractTest.kt`
- `app/src/test/java/com/astrbot/android/feature/chat/runtime/AppChatPluginCommandServiceTest.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/ChatViewModelTest.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/ConversationViewModelTest.kt`
- `app/src/test/java/com/astrbot/android/ui/chat/ChatInputStateContractTest.kt`
- `app/src/test/java/com/astrbot/android/ui/chat/ChatAutoScrollContractTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/context/PromptAssemblerTest.kt`
- `app/src/test/java/com/astrbot/android/core/runtime/context/RuntimeContextResolverProviderTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/search/WebSearchPromptGuidanceTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/context/RuntimeResourceProjectionResolverTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/context/Task8Phase3VerificationTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/PostHiltRound2PluginRuntimeContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/PostHiltRound3HostCapabilityContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/LlmSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/HiltExitContractTest.kt`

## 9. 易错点

- `feature/chat/presentation/ChatViewModel.kt` 的 package 仍是 `com.astrbot.android.ui.viewmodel`。
- `feature/chat/presentation/ChatViewModelRuntimeBindings.kt` 的 package 也仍是 `com.astrbot.android.ui.viewmodel`。
- `feature/chat/presentation/ChatSessionController.kt` 的 package 也仍是 `com.astrbot.android.ui.viewmodel`，不要只靠 import 猜物理位置。
- `SendAppMessageUseCase` 不应 import plugin/runtime/UI 实现。
- `AppChatRuntimeService` 是 runtime port 实现，不是 ViewModel helper。
- `DefaultChatViewModelRuntimeBindings` 当前通过 factories 组装 runtime/use case/handler；不要把 direct new runtime helper 或 compat gateway factory 写回 bindings。
- `di/AstrBotViewModelDependencies.kt` 已删除，不要从那里找 Chat runtime bindings。
- `RuntimeOrchestrator.kt` 只是兼容壳；新 runtime 注入优先走 `RuntimeLlmOrchestratorPort`。
- `AppChatPluginCommandService` 的两参构造函数与 `createCompatPluginHostCapabilityGatewayFactory()` 都是 compat-only；production 应走 Hilt-owned `PluginHostCapabilityGatewayFactory` + `PluginV2DispatchEngine`。
- App Chat plugin command / message ingress 必须携带 session 统一来源给 `storage.session`；不要只传 display session id。
- 改 prompt skill/tool skill 时要同时看 Resource Center projection 和 ToolSource registry。
