# W03 Core 运行 Feedback

执行时段：2026-11-14 01:21 至 01:49（北京时间）。

## T01.03 上下文装配、预算与压缩

### 实际改动

- 在 `core/src/main/kotlin/com/elymbot/core/context/ContextBudget.kt` 提供可注入模型窗口预算与保守 Token 估算。缺少分词器时按 UTF-8 字节数、消息/工具结构开销估算，图片按固定估算量计入；默认窗口基线为 64K，输出预留 4K、安全余量 2K、80% 开始压缩，保留最近两个完整轮次。
- 在 `ContextCompiler.kt` 按会话 ID 和 `historyUpperBoundExclusive` 分页读取历史，只装配状态为 `COMPLETE` 且同时包含用户和助手消息的完整轮次；不把 STOPPED/INTERRUPTED 残片、指令回执或工具展示文本投影为普通模型历史。摘要覆盖的轮次从原文上下文中排除，原文仍由历史仓库保存。
- 请求顺序为宿主规则、有效人格、摘要、未被摘要覆盖的完整历史、本次用户输入和当前轮已完成工具批次。压缩请求使用主模型的非流式调用，不附带工具定义、不经过 ReAct 循环，并有 20 秒摘要调用上限。
- 摘要候选带稳定轮次范围及 `replacesSummaryIds`。只有摘要正文非空、重装后的估算占用下降时才提交；摘要服务失败、候选无降幅或仓库拒绝时，不改现有摘要。输入超预算时仅裁剪模型请求副本，按最旧可选历史优先，仍不能容纳人格、当前输入和最新工具批次时返回 `ContextLimitExceededException`。
- 调整共享 `HistorySummaryCandidate`：增加默认空集字段 `replacesSummaryIds`，并明确 Store 原子替换规则。该接口供 W04 摘要仓储使用。

### 验证

- 环境：Windows 11，PowerShell，JDK 17.0.17，Gradle Wrapper 9.9.1，Kotlin 2.4.21；工作区 `C:\Users\93445\Desktop\ElymBot`。未组装 APK、未使用设备。
- `.\gradlew.bat :core:compileKotlin`：通过。
- `.\gradlew.bat :core:test --tests com.elymbot.core.context.ContextCompilerTest`：通过。5 项覆盖完整轮次与上界过滤、旧轮次摘要和覆盖范围、已有摘要 ID 替换、摘要失败保留已存摘要、完整材料超出硬预算受控失败，以及新增工具批次造成后续上下文重新触发压缩。
- 合成摘要模型与内存 HistoryReader/HistorySummaryStore 用于验证；未连接实际模型服务或 Room。

## T01.04 顺序 ReAct、工具底座与指令规则

### 实际改动

- 在 `core/src/main/kotlin/com/elymbot/core/agent/ReActAgentRuntime.kt` 实现单轮顺序循环。文本增量即时发出；仅消费 ModelGateway 的完整终态工具批次；工具按调用顺序执行，结果按原 `callId` 回填，再向主模型续轮。结构化工具探测未确认或不支持时，不向模型发送工具定义。
- 默认最多 8 次主模型请求、16 次工具调用、单工具 10 秒、整轮 180 秒；保留调用取消。超时、网络、认证、API 拒绝、协议、中断、上下文超限和输出超限映射为受控 Agent 终态。上下文超限最多压缩/裁剪并重试当前模型请求一次，沿用已完成工具结果，不重跑工具。API 拒绝携带 HTTP 401/403 时映射为认证失败。
- 图片由明确支持视觉的主模型直接接收；主模型明确不支持时，用已确认支持视觉的备用模型单独转述，再以原用户问题和转述文字调用主模型。主模型视觉未知、备用模型缺失/不支持或视觉能力未知分别返回相应失败类型。
- 在 `core/src/main/kotlin/com/elymbot/core/tool/ToolRegistry.kt` 实现稳定 ID/工具名唯一性、会话作用范围筛选、JSON 参数 Schema 的类型/必需字段/枚举/基本边界校验、顺序可取消执行、超时受控结果、进度清理与长度限制、结果长度上限。异常细节不进入结果。
- 在 `HistoryReadToolSource.kt` 注册内置 HistoryRead。调用绑定 `ToolExecutionContext` 中的当前会话和轮次上界，不接受会话 ID 或文件路径；支持轮次范围、方向、续页游标和页大小，返回消息 ID、轮次、状态、截断标记及续页信息，并对读结果再次按当前上界过滤。
- 在 `core/src/main/kotlin/com/elymbot/core/command/DefaultSlashCommandParser.kt` 实现五种可改名 Slash Command 的纯解析：help、persona、checkpoint、reset、mark。参数按空白分隔，返回类型化动作或本地 Invalid，不修改 App 状态。

### 验证

- `.\gradlew.bat :core:test --tests com.elymbot.core.agent.ReActAgentRuntimeTest --tests com.elymbot.core.context.ContextCompilerTest --tests com.elymbot.core.tool.ToolRegistryTest --tests com.elymbot.core.command.DefaultSlashCommandParserTest`：通过，共 20 项（Agent 10、Context 5、Tool/HistoryRead 3、Command 2）。该命令同时编译 Core main 与 test Kotlin 源码。
- 合成模型、内存历史仓库和测试工具来源覆盖：顺序完整工具批次及 ID 回填、拒绝和抛错后续轮、工具超时/限量、调用方取消、工具后上下文错误只重试模型且不重跑工具、可见文本和助手反馈、视觉直传/转述/能力未知、HistoryRead 会话和轮次隔离、指令别名与无效编号。
- 曾运行未过滤的 `:core:test`，当时 34 项中有 3 项位于尚在修改的 W02 `OpenAiCompatibleModelGatewayTest` 失败：一个不支持流式的探针请求断言，以及两项测试夹具生成了非法外层 JSON。W02 随后复审并推送 `c7ab737`；本轮没有在该提交后重跑未过滤全量测试。W03 负责的 20 项定向测试已在当前共享工作区通过。

### 交接与限制

- W04 装配入口：`ContextCompiler(historyReader, summaryStore, modelGateway, budgetProvider?, tokenCounter?)`；`ReActAgentRuntime(modelGateway, contextCompiler, historyReader, maxModelCalls?, maxToolCalls?, toolTimeoutMillis?, turnTimeoutMillis?)`；`DefaultSlashCommandParser().parse(input, CommandNames)`。`ReActAgentRuntime` 自动注册 Core 的 `HistoryReadToolSource`，`AgentRequest.toolSources` 只传其他来源，避免重复注册 HistoryRead。
- W04 的 `HistoryReader` 应只投影会话中保留的 `category=HISTORY` user/assistant 消息。ContextCompiler 另外筛除非 COMPLETE 轮；HistoryRead 保留停止/中断状态供历史工具返回。摘要 Store 需在同一事务校验候选会话、仍保留的完整轮次覆盖和 `replacesSummaryIds`，原文不变。
- 模型窗口与分词器当前没有服务商专属来源，使用可配置 64K 回退预算及保守估算；需后续用实际模型与有效窗口校准。历史读取容量、摘要长度和估算值是 Core 默认值，不代表服务端真实 token 计量。
- 若上下文仍超硬预算，Core 会成对省略最旧的完整工具批次并保留最新批次；旧工具批次目前不另生成观察摘要。未验证超大工具结果在实际服务端窗口下的摘要质量，交正式集成复审确认。
- 当前验证为 JVM 合成输入；真实 HTTP/SSE 行为由 W02 负责，Room 事务与历史投影由 W04 负责，App 正式入口和 APK 由 W01 后续集成验证。

## 返工与复验（2026-11-14）

- `ContextCompiler.readCompleteTurns` 遇到截断记录时只排除该轮，并继续读取更旧历史；之前的处理会提前停止，连带丢弃仍完整的旧轮次。新增 `summarySourceContainsOnlyCompleteNonTruncatedTurns`，以合成材料核对截断轮、中断轮和缺少助手消息的轮次均未进入摘要请求，完整轮次仍可用于摘要。
- 调整工具摘要压力测试中的旧工具结果长度，使注入预算下较早的完整工具批次确实需要摘要或裁剪，并核对最新 `call-3` 调用和结果 ID 成对保留。此前记录的“旧工具批次目前不另生成观察摘要”限制由本轮实现和测试取代；本轮使用合成摘要响应，不证明实际服务端摘要质量。
- 首次定向执行在 `:core:compileTestKotlin` 失败，原因是测试引用了错误包下的 `ContextTokenCounter`，且一处 `ModelMessage` 测试构造缺少必需的 `content`；均已修复。随后 26 项中有 2 项 ContextCompiler 断言失败，按上文修复截断记录读取行为并校准工具批次测试夹具。
- 最终验证环境：Windows 11、PowerShell、OpenJDK 17.0.17、Gradle Wrapper 9.9.1；未组装 APK、未使用设备。命令：

  ```powershell
  .\gradlew.bat :core:test --tests com.elymbot.core.agent.ReActAgentRuntimeTest --tests com.elymbot.core.context.ContextCompilerTest --tests com.elymbot.core.tool.ToolRegistryTest --tests com.elymbot.core.command.DefaultSlashCommandParserTest
  ```

  结果：`BUILD SUCCESSFUL`；Gradle XML 结果为 26/26 通过（Agent 10、Context 10、ToolRegistry/HistoryRead 4、Command 2）。验证使用合成模型、历史仓库和工具来源；未连接实际模型服务或 Room。
- ToolProgress 继续保持执行期状态：Core 事件只携带 active 布尔值，ToolRegistry 丢弃工具来源提供的进度文本。App 侧写库路径未持久化该事件；W04 的 Android 仪器测试有对应运行中断言，但设备安装被 `INSTALL_FAILED_USER_RESTRICTED` 阻止，尚无本轮设备实测证据。

## 返工与复验（二）：服务端上下文超限恢复（2026-11-14）

- `ContextCompiler` 在 `forceCompaction` 下即使本地估算低于输入上限，也会尝试摘要早期完整工具批次；摘要失败或不能降低估算时，继续裁剪早期完整批次，保留最新批次。此前恢复可能把相同工具历史再次发给模型。
- 新增 `serverContextLimitCompactsOlderToolBatchesWhenLocalBudgetHasRoom`：无旧会话历史、连续完成两个工具批次；普通请求的估算低于压缩触发点和硬输入预算，第三次主模型请求仅因合成服务端 `ContextLimit` 进入恢复。重试请求以摘要替代早期批次，并保留最新 `id-b` 调用与结果 ID；工具各执行一次。该测试使用 2,000 字符合成旧结果，确保摘要实测降幅；真实服务端 token 计量未验证。
- 最终复验仍使用四个 W03 定向测试类，命令同上。Gradle XML 结果为 27/27 通过（Agent 11、Context 10、ToolRegistry/HistoryRead 4、Command 2），`BUILD SUCCESSFUL`。环境为 Windows 11、PowerShell、OpenJDK 17.0.17、Gradle Wrapper 9.9.1；未组装 APK、未使用设备。
