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
- 2026-11-23 修复结构化工具能力探测假阴性。`probeToolCalls()` 现在与正式 Reply 请求一样使用兼容服务自动选择工具；移除了 `requestCompletion()` 与请求编码器中的强制选择私有参数和序列化分支。只有终态 `TOOL_CALLS`、唯一工具名为 `capability_probe` 且参数值匹配探测请求时才返回 `SUPPORTED`。
- 已将现有配置模型 App 接受测试改为要求一次 `reply` 工具调用提交两条指定文本，间隔分别为 0 和 1,200 ms；验证两条消息按序到达 App，并从 Room 投递记录核对持久化的 `availableAtEpochMillis` 差值。尚未执行此 App 测试。
- 2026-11-24 针对总控提供的脱敏中英文真实模型对照，在 opt-in Reply 路径加入一次受限纠正请求：只有非空普通正文以 `STOP` 返回、此前无工具批次、无已受理 Reply、`reply` 当前已广告且模型调用预算尚有余量时，才在同一执行和尝试中将一条中英双语回复指引附到 persona 后重试。指引只进入紧接的一次模型请求，取得该响应后恢复基准请求；首次原始正文不进入下一请求上下文或用户事件。纠正后仍返回普通正文，或返回空白 `STOP` 且没有已受理 Reply，均继续按 `FAILED/PROTOCOL` 处理；首次合法空白 `STOP` 的静默语义不变。新增 runtime 回归用例覆盖中文正文后成功 Reply、连续普通正文、纠正后空白 `STOP`、预算耗尽及已受理 Reply。此轮由总控执行编译与 MuMu 复验，Worker 未运行 Gradle 或设备操作。

## 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.01 A01` | `.\gradlew.bat :core:test`；合成 Core JVM 测试 | Gradle Wrapper 9.9.1、JDK 17 工具链、Kotlin 2.4.21 | 通过，61/61 测试。覆盖注册工具可见与执行作用域一致、来源授权、安装实例撤销、挂起状态回调间撤权、策略失败/超时、未知工具、非法参数、取消、批次重复调用 ID、跨批次重复模型 ID、逐调用结果及序号。 | `core/src/test/kotlin/com/elymbot/core/tool/ToolRegistryTest.kt`；`core/src/test/kotlin/com/elymbot/core/agent/ReActAgentRuntimeTest.kt` |
| `T02.01 A05` | `.\gradlew.bat :core:test`；合成 Core JVM 契约测试 | Core Kotlin/JVM；无 Android 或插件实现依赖 | 通过。规范化输入携带消息、来源、会话路由、目标、引用和五类内容段；Core 运行入口将来源与安装身份交给宿主策略和工具。 | `core/src/test/kotlin/com/elymbot/core/contract/CoreContractsTest.kt`；`core/src/main/kotlin/com/elymbot/core/contract/MessageContracts.kt` |
| `T02.02 A02–A03 · Core` | `.\gradlew.bat :core:test --offline --console=plain --tests '*ReplyRuntimeMockWebServerTest' --tests '*ReplyToolSourceTest' --tests '*ContextCompilerTest' --tests '*ToolRegistryTest' --tests '*ReActAgentRuntimeTest'` | OpenAI-compatible Core gateway、MockWebServer3、Core JVM | 通过。真实 HTTP 编解码及 Agent 路径验证 `stream=false`、多消息/重复 Reply、穿插普通工具、调用 ID 回填、入队后续跑、正文与失败反馈抑制、空白 STOP、拒绝、能力不足和投递取消。 | `core/src/test/kotlin/com/elymbot/core/agent/ReplyRuntimeMockWebServerTest.kt`；`core/src/test/kotlin/com/elymbot/core/tool/ReplyToolSourceTest.kt` |
| `T02.02 A05 · Core` | 上述定向测试；MockWebServer 请求体检查 | 合成五类 `AgentMessageSegment`、文本/图片 `ModelMessage` | 通过 Core 请求投影检查：请求包含文本与实际编码图片，不包含语音、视频和文件资源引用。App 正式适配入口的实际投影由 W02/W08 验证。 | `core/src/test/kotlin/com/elymbot/core/agent/ReplyRuntimeMockWebServerTest.kt` |
| `T02.02 A15 · Core` | `.\gradlew.bat :core:test --offline --console=plain` | Gradle Wrapper 9.9.1、JDK 17 工具链、Kotlin 2.4.21 | 通过，当前 Core 套件 73/73 测试。Opt-in 非流式路径覆盖视觉图片请求、工具批次上下文压缩和外部取消；历史测试区分明确静默、完成 Reply 与未完成行，并验证后续截断 Reply 不被部分摘要永久遮盖。 | `core/src/test/kotlin/com/elymbot/core/agent/ReActAgentRuntimeTest.kt`；`core/src/test/kotlin/com/elymbot/core/context/ContextCompilerTest.kt` |
| `T02.05 Core ABI handoff` | `.\gradlew.bat :core:test :core:jar --offline --console=plain` | Gradle Wrapper 9.9.1、JDK 17 工具链、Kotlin 2.4.21 | 总控协调运行成功；Core 测试 74/74 通过，契约消费方测试覆盖宿主身份传入、来源包装和 handle 关闭；Core JAR 构建通过。 | `core/src/main/kotlin/com/elymbot/core/contract/PluginContracts.kt`；`core/src/test/kotlin/com/elymbot/core/contract/PluginContractsTest.kt` |
| `T02.01 A01 · structured tool probe` | `:core:test --tests com.elymbot.core.model.OpenAiCompatibleModelGatewayTest` | JDK 17、Kotlin/JVM；总控辅助验证检出基线 `05dedcf` 并应用本次三个模型 adapter/test 文件 | 总控验证通过，23/23、0 跳过。正式能力探测请求体不含 `tool_choice`；探测仍按工具名、参数及终态校验。 | `core/src/test/kotlin/com/elymbot/core/model/OpenAiCompatibleModelGatewayTest.kt`；`core/src/main/kotlin/com/elymbot/core/model/OpenAiCompatibleRequestEncoder.kt` |
| `T02.01 A01 · configured model direct gateway` | 总控通过非流式生产网关发送相同探测工具 | MuMu；同一已配置模型与探测 schema | 1/1 通过。旧探测带 `tool_choice=required` 返回 HTTP 400；使用正式请求的自动工具选择得到 `capability_probe` 与 `TOOL_CALLS` 终态。 | `core/src/main/kotlin/com/elymbot/core/model/OpenAiCompatibleModelGateway.kt`；`core/src/test/kotlin/com/elymbot/core/model/OpenAiCompatibleModelGatewayTest.kt` |
| `T02.02 A02 · configured App Reply acceptance` | `ConfiguredModelAcceptanceTest#configuredTextModelListsProbesAndRepliesToATestPrompt` | MuMu Android 15 / x86_64，已配置真实模型 | 1/1 通过，0 跳过。模型探测识别结构化工具支持；App 同一 Reply 调用的两条消息均按序送达，Room 两个投递可用时间相差 1,200 ms。 | `app/src/androidTest/kotlin/com/elymbot/android/ConfiguredModelAcceptanceTest.kt` |
| `T02.02 · ordinary STOP correction` | `:core:test --tests '*ReActAgentRuntimeTest' --tests '*ReplyRuntimeMockWebServerTest' --offline` | Core Kotlin/JVM，JDK 17，基线 `f62fc5a` 与本轮变更 | 26/26 通过，0 跳过。覆盖一次纠正、恢复后移除临时指引、连续普通正文与纠正后空白 `STOP` 的受控失败、预算、既有 Reply 与首次合法静默。模拟 HTTP 校对第二请求不带首回包原文且没有第三条请求。 | [Core runtime 定向测试](../../../../core/src/test/kotlin/com/elymbot/core/agent/ReActAgentRuntimeTest.kt) · [HTTP 定向测试](../../../../core/src/test/kotlin/com/elymbot/core/agent/ReplyRuntimeMockWebServerTest.kt) |
| 源码检查 | `git diff --check -- core/src/main/kotlin/com/elymbot/core core/src/test/kotlin/com/elymbot/core` | 当前 Windows 工作区 | 通过；Git 提示工作文件的 LF 在后续写入时可能转换为 CRLF。 | `core/src/main/kotlin/com/elymbot/core/tool/ToolRegistry.kt` |

## 限制与待验

- W02 负责 App/Room 的 `ReplySubmitter` 持久提交、幂等键及 `TurnEntity.successfulSilence` 写入，并将成功标记映射到用户历史行。Core 已交付对应事件与历史字段；App 持久层语义需由 W02/W07 集成复验。
- 本包不实现 Android 或渠道适配。五类消息段的正式 App/平台入口、私有媒体读取授权、HistoryRead 渠道可见性仍需 W02/W04/W08 端到端验证。Core 合成投影只将当前 `ModelMessage` 中的文本和已支持图片交给兼容模型。
- T02.02 普通正文无有效 Reply 与结构化工具能力不足统一使用 `FAILED/PROTOCOL` 受控状态。本轮真实模型 App 复验覆盖本地投递。平台插件或 QQ 投递由后续集成验证。
- T02.05 本次只交付 Core ABI；签名校验、DEX/ClassLoader 实现、候选准备/激活/回退和实例关闭顺序由 W03/W02 实现并验证，Core 契约不验证插件制品或 Android 生命周期。

本轮 App 与 AndroidTest 使用基线 `05dedcf` 和此次模型兼容修复构建。App APK SHA-256 `6946D5265E119A07BFBE2DF7178657C7B7A27AA793C2F416645EE37918F3FB38`，AndroidTest APK SHA-256 `B068F028E8DD8838DD3227CDD885C1AE8310DF820B0EC26CA3D2685B10A09F18`。

## 2026-11-24 中文 Reply 实测复验

- 在 MuMu 的同一已配置模型复现。原英文请求收到结构化 `reply` 后完成；原中文请求收到有效 JSON，首个终态是普通正文 `STOP`，工具调用为零，Room 记录 `FAILED/PROTOCOL`。请求的媒体类型声明 UTF-8，脱敏形态检查未见替换字符。
- 用基线 `f62fc5a` 和本次变更运行 `:app:assembleDebug :app:assembleDebugAndroidTest --offline`，随后运行 `ConfiguredModelAcceptanceTest#equivalentChineseAndEnglishPromptsReceiveReplies`。MuMu 1/1 通过，0 跳过。两条等价提示均完成并收到 App 投递，中文回复含汉字。该用例只使用固定测试提示。[Android 定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/ConfiguredModelAcceptanceTest.kt)
- 本次实测 App APK SHA-256 `DF1D79134E86BD53F4D31E7E830054A374971CFB934272A59B755769E549976A`，AndroidTest APK SHA-256 `54AD84F374E5A270F772313EF3D81CFA90BEFF6823E6A442D2267CA6B4CBC91C`。

## 2026-11-24 · 回复节奏返工

- 宿主原先直接使用模型建议的毫秒数。现在简单回答请模型使用一条，分条按语义组织。首条通常立即交付，后续按汉字或词数计算 180–3,200 ms 的基础间隔，叠加按语境建议的停顿，单段上限 30 秒。同一轮的多次 Reply 共用持久化时间线，已花在工具上的时间可以抵扣间隔。
- `:core:test :app:assembleDebug :app:assembleDebugAndroidTest --offline` 通过，Core 81 项测试无失败或跳过。MuMu Android 15 / x86_64 执行 `ReplyHostIntegrationTest` 4 项、`RoomReplySubmitterTest` 9 项、`ConfiguredModelAcceptanceTest` 2 项，共 15 项通过。覆盖长短变化、语境建议、多目标计划、跨调用和幂等，以及真实模型的分条和中英文回复。真实模型分条测试用指定内容和建议值复验 App 交付。
- App 调试制品 SHA-256 `DDE1D490DFE4D4640CA554726F9AD9F4A0DD6E0162B4BB0D3908586D69BE2C86`；AndroidTest 制品 SHA-256 `1C144AF68A175AD8885933FDFB334637EE6FD1ABEC3DE158439BC6FB273311F0`。
