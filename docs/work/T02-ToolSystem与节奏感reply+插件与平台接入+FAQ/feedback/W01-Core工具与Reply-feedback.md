# W01 · Core 工具与 Reply Feedback

## 实际结果

- 2026-11-22 完成 T02.01 Core 入口接线。`AgentRequest` 以默认字段新增规范化消息、宿主注册工具、访问策略和 `ReplySubmitter`；保留 T01 的 `currentInput`、`toolSources` 与既有构造调用。
- 新增 `AgentMessage`、路由、引用和文本/图片/音频/视频/文件段契约；媒体使用 Core/JVM 可见的稳定资源引用。
- 新增宿主创建的 `ToolRegistration` 与插件安装实例身份。`ToolRegistry` 使用同一作用域规则筛选目录并执行；在可挂起的状态回调之后、调用工具之前重查宿主授权，授权和工具执行共用每工具超时。默认策略保留 Host 工具，未配置宿主策略的插件工具不显示且被拒绝。
- `ReActAgentRuntime` 将请求身份和来源传入授权与工具上下文，按模型工具批次和批内顺序赋予稳定序号；原 T01 文本和流式事件路径未切换。
- Reply 提交契约以会话、执行、尝试、轮次、宿主工具批次序号、批内调用序号及模型调用 ID 标识。`Accepted` 表示 App 已将整组有序消息持久入队，不表示渠道送达。
- 2026-11-22 完成 T02.02 Core Reply 运行路径。只有提供 `ReplySubmitter` 的请求注册内置 `reply` 工具并走完整非流式响应；每次工具调用按序提交并以模型调用 ID 回填受控状态，单次支持 1–8 条消息、单条最多 4,000 字符、合计最多 12,000 字符、建议间隔 0–30,000 ms。Core 不等待间隔或外部送达。
- Reply 已受理后 Agent 可继续调用普通工具或再次提交 Reply。opt-in 请求中的普通模型正文及原始工具失败反馈不发为用户事件；普通正文未伴随有效 Reply 时以 `FAILED/PROTOCOL` 结束。结构化工具能力未知或不支持时在调用模型前受控失败。
- opt-in 空白 STOP 且从未调用 `reply` 时以 `COMPLETED` 结束，并通过默认值为 false 的 `AgentEvent.Finished.successfulSilence` 明确标记。Reply 被拒绝或失败后空白 STOP 不记为静默成功。`HistoryRecord.successfulSilence` 默认 false；上下文只接纳明确标记的用户单行静默轮次，或者含用户和已完成助手消息的轮次，并逐行滤除未完成记录。
- 历史读取输出该静默标记。明确截断的已完成消息、因读取 token 边界截断的已完成消息，以及分页停止时可能不完整的最旧轮次仍整体排除，避免摘要覆盖该轮已提交但未完整读入的其他 Reply。
- 2026-11-23 为 T02.05 交付最小 Core 插件 ABI：签名制品提供 `PluginEntrypoint`，由已验证宿主传入 `PluginHostContext.installationIdentity`；同步 `prepare` 返回 `PluginHandle`，只贡献 `ToolSource` 并提供 `close()`。宿主加载器负责快照来源列表，再包装为带宿主安装身份的 `ToolRegistrationOwner.Plugin`；准备异常透传给加载器处理。

## 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.01 A01` | `.\gradlew.bat :core:test`；合成 Core JVM 测试 | Gradle Wrapper 9.9.1、JDK 17 工具链、Kotlin 2.4.21 | 通过，61/61 测试。覆盖注册工具可见与执行作用域一致、来源授权、安装实例撤销、挂起状态回调间撤权、策略失败/超时、未知工具、非法参数、取消、批次重复调用 ID、跨批次重复模型 ID、逐调用结果及序号。 | `core/src/test/kotlin/com/elymbot/core/tool/ToolRegistryTest.kt`；`core/src/test/kotlin/com/elymbot/core/agent/ReActAgentRuntimeTest.kt` |
| `T02.01 A05` | `.\gradlew.bat :core:test`；合成 Core JVM 契约测试 | Core Kotlin/JVM；无 Android 或插件实现依赖 | 通过。规范化输入携带消息、来源、会话路由、目标、引用和五类内容段；Core 运行入口将来源与安装身份交给宿主策略和工具。 | `core/src/test/kotlin/com/elymbot/core/contract/CoreContractsTest.kt`；`core/src/main/kotlin/com/elymbot/core/contract/MessageContracts.kt` |
| `T02.02 A02–A03 · Core` | `.\gradlew.bat :core:test --offline --console=plain --tests '*ReplyRuntimeMockWebServerTest' --tests '*ReplyToolSourceTest' --tests '*ContextCompilerTest' --tests '*ToolRegistryTest' --tests '*ReActAgentRuntimeTest'` | OpenAI-compatible Core gateway、MockWebServer3、Core JVM | 通过。真实 HTTP 编解码及 Agent 路径验证 `stream=false`、多消息/重复 Reply、穿插普通工具、调用 ID 回填、入队后续跑、正文与失败反馈抑制、空白 STOP、拒绝、能力不足和投递取消。 | `core/src/test/kotlin/com/elymbot/core/agent/ReplyRuntimeMockWebServerTest.kt`；`core/src/test/kotlin/com/elymbot/core/tool/ReplyToolSourceTest.kt` |
| `T02.02 A05 · Core` | 上述定向测试；MockWebServer 请求体检查 | 合成五类 `AgentMessageSegment`、文本/图片 `ModelMessage` | 通过 Core 请求投影检查：请求包含文本与实际编码图片，不包含语音、视频和文件资源引用。App 正式适配入口的实际投影由 W02/W08 验证。 | `core/src/test/kotlin/com/elymbot/core/agent/ReplyRuntimeMockWebServerTest.kt` |
| `T02.02 A15 · Core` | `.\gradlew.bat :core:test --offline --console=plain` | Gradle Wrapper 9.9.1、JDK 17 工具链、Kotlin 2.4.21 | 通过，当前 Core 套件 73/73 测试。Opt-in 非流式路径覆盖视觉图片请求、工具批次上下文压缩和外部取消；历史测试区分明确静默、完成 Reply 与未完成行，并验证后续截断 Reply 不被部分摘要永久遮盖。 | `core/src/test/kotlin/com/elymbot/core/agent/ReActAgentRuntimeTest.kt`；`core/src/test/kotlin/com/elymbot/core/context/ContextCompilerTest.kt` |
| `T02.05 Core ABI handoff` | `.\gradlew.bat :core:test :core:jar --offline --console=plain` | Gradle Wrapper 9.9.1、JDK 17 工具链、Kotlin 2.4.21 | 总控协调运行成功；Core 测试 74/74 通过，契约消费方测试覆盖宿主身份传入、来源包装和 handle 关闭；Core JAR 构建通过。 | `core/src/main/kotlin/com/elymbot/core/contract/PluginContracts.kt`；`core/src/test/kotlin/com/elymbot/core/contract/PluginContractsTest.kt` |
| 源码检查 | `git diff --check -- core/src/main/kotlin/com/elymbot/core core/src/test/kotlin/com/elymbot/core` | 当前 Windows 工作区 | 通过；Git 提示工作文件的 LF 在后续写入时可能转换为 CRLF。 | `core/src/main/kotlin/com/elymbot/core/tool/ToolRegistry.kt` |

## 限制与待验

- W02 负责 App/Room 的 `ReplySubmitter` 持久提交、幂等键及 `TurnEntity.successfulSilence` 写入，并将成功标记映射到用户历史行。Core 已交付对应事件与历史字段；App 持久层语义需由 W02/W07 集成复验。
- 本包不实现 Android 或渠道适配。五类消息段的正式 App/平台入口、私有媒体读取授权、HistoryRead 渠道可见性仍需 W02/W04/W08 端到端验证。Core 合成投影只将当前 `ModelMessage` 中的文本和已支持图片交给兼容模型。
- T02.02 普通正文无有效 Reply 与结构化工具能力不足统一使用 `FAILED/PROTOCOL` 受控状态。没有运行真实模型、Android、插件制品、平台送达或真机验收。
- T02.05 本次只交付 Core ABI；签名校验、DEX/ClassLoader 实现、候选准备/激活/回退和实例关闭顺序由 W03/W02 实现并验证，Core 契约不验证插件制品或 Android 生命周期。
