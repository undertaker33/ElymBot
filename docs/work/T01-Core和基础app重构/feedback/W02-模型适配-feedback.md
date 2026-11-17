# W02 模型适配 Feedback

## T01.02 模型适配、能力探测与 SSE

执行日期：2026-11-14（北京时间）。

### 实际改动

- 新增 `core/src/main/kotlin/com/elymbot/core/model/OpenAiCompatibleModelGateway.kt` 与 `OpenAiCompatibleProtocol.kt`，实现 `ModelGateway` 的 OpenAI Chat Completions 兼容接入：`GET /models`、普通和流式 `POST /chat/completions`、文本与图片输入、工具 schema 以及助手工具调用/工具结果历史。模型 ID 列表去重；请求使用 `max_completion_tokens`。
- 连接配置通过 `ModelServiceConnectionProvider.resolve(serviceId)` 注入。`ModelServiceConnection` 将 API Key 保存在私有字段，只经 Bearer 认证头发送；连接 `toString()` 不含凭据，服务端错误消息在异常化前会脱敏。Base URL 只接受 HTTP(S)，不接受 URL 内嵌认证、查询或片段。
- 能力探测独立检查可用性、视觉输入、原生流式和结构化工具调用。支持、明确不支持、未知三态分别保留；各探测失败填入 `failuresByTarget`，不覆盖其他已探测状态。认证、网络、超时等适当失败会停止后续探测。探测请求使用有界调用时限。
- SSE 按 UTF-8 解码并支持 CR、LF、CRLF、多个 `data:` 行、注释和空事件；忽略不含 choices 的 usage 事件，保留文本空白，收到文本时立即发出 `TextDelta`。按 tool-call index 累积名称和参数片段，仅在明确终止且完整校验后发出唯一 `Completed` 工具批次。提前 EOF 映射 `StreamInterrupted`；输出长度截断保留已生成文字并丢弃未完成工具调用；非流式响应在完整返回后发出一次文本增量和终态。
- 取消通过 OkHttp `Call.cancel()` 关闭请求并保留 `CancellationException`。流请求不设 socket read/call timeout；探测有界。上下文超限映射 `ContextLimit`，`content_filter` 映射 `ApiRejected(code="content_filter")`，网络、HTTP/API、协议、中断和超时保持类型化失败。
- 新增 `core/src/test/kotlin/com/elymbot/core/model/OpenAiCompatibleModelGatewayTest.kt`，使用 MockWebServer3 覆盖模型列表与凭据边界、普通回复及图片/工具历史序列化、SSE 首片与结束顺序、空白与 UTF-8 分片、多 `data:` 行、交错工具参数累积、无效工具批次、长度截断、提前 EOF、取消、四项探测、流式不支持、认证失败、上下文超限和内容过滤。

### 验证

- 环境：Windows 11 amd64、Microsoft JDK 17.0.17、Gradle Wrapper 9.9.1、Kotlin 2.4.21。测试通过仓库 Gradle Wrapper 和 JVM 模块执行。
- 命令：`.\gradlew.bat :core:test`。结果：`BUILD SUCCESSFUL`。JUnit XML `core/build/test-results/test/TEST-com.elymbot.core.model.OpenAiCompatibleModelGatewayTest.xml` 报告 12 tests、0 failures、0 errors；末次复核 Gradle 将编译和测试任务判为 `UP-TO-DATE`。
- 本阶段证据为 MockWebServer3 合成 HTTP/SSE 与 JVM 自动化测试。未连接实际模型服务、未在 Android 设备或 App 正式入口验证，不能替代 T01.08 的实际服务与设备验收。

### Checklist 对应项与限制

- 对应 Checklist「T01.02 模型适配、能力探测与 SSE」及任务书用户验收 2–5 中模型列表/探测、首片先到、非流式完整返回、断流及停止相关模型层行为。上述模型层场景由 12 项 JVM 合成测试覆盖。
- W03/W04 可通过 `OpenAiCompatibleModelGateway` 与 `ModelServiceConnectionProvider` 消费适配器；服务 ID 的已保存配置及未保存草稿 overlay 由 App 装配 provider 提供。
- App 侧停止状态、重试复用用户消息、视觉两路转述、配置界面和正式服务行为需由 W03/W04/W05 集成验收。Checklist 全包项仍由总控依据集成复验更新。

## 审查修正与复验

### 修正内容

- 经总控和 W01 协调，在共享 `ModelRequestException.ApiRejected` 与 `ContextLimit` 增加默认空值的 `httpStatusCode`。解析非 2xx JSON 错误时保留响应状态；401/403 归类为认证失败，其他带 JSON 错误的非 2xx 归类为 HTTP 响应失败，402/429 保留状态并停止后续能力探测。上下文超限仍为 `ContextLimit`，同时保留对应状态码。
- provider 返回的 `error.code` 与 `error.type` 仅通过稳定白名单映射为适配器分类；未识别值不向调用方暴露。服务端消息在进入 typed error 前脱敏 API Key 并限长。探测只有收到明确 API 错误且内容指向相应能力时才报告 `UNSUPPORTED`；网络故障保持 `UNKNOWN`。
- SSE 和普通响应都要求工具批次完整终止且每个调用具有唯一非空 ID 和名称；缺少 ID/名称、重复 ID、传输未终止或批次结构错误均不产生 `Completed` 工具批次。完整终止的调用即使 arguments 缺失、是截断 JSON 或解析为非对象，也保留原 call ID 与原始参数文本交给 W03 校验。已对照 `core/src/main/kotlin/com/elymbot/core/tool/ToolRegistry.kt` 的参数验证：无效参数在工具来源执行前返回与该 call ID 关联的拒绝结果。长度截断在流式与普通响应中均丢弃工具调用。
- 在 `core/src/main/kotlin/com/elymbot/core/model/OpenAiCompatibleProtocol.kt`、`OpenAiCompatibleModelGateway.kt` 和 `core/src/main/kotlin/com/elymbot/core/contract/ModelContracts.kt` 修正上述语义；扩充 `OpenAiCompatibleModelGatewayTest.kt` 覆盖错误状态与短路、provider code/type 脱敏、断网未知、流式和普通调用的参数缺失/截断/数组、不完整调用 ID/name、未终止工具批次及普通响应长度截断。

### 复验

- 命令：`.\gradlew.bat :core:test --tests 'com.elymbot.core.model.OpenAiCompatibleModelGatewayTest'`。结果：`BUILD SUCCESSFUL`；JUnit XML 报告 19 tests、0 failures、0 errors。复验环境仍为 Windows 11 amd64、Microsoft JDK 17.0.17、Gradle Wrapper 9.9.1、Kotlin 2.4.21。
- W02 首次定向测试发现测试夹具的截断 JSON 外层转义缺少结束引号，以及前一个成功探测留下的 RecordedRequest 未从队列取出；已修正夹具，并在验证短路前显式消费先前探测及当前可用性探测的请求，最终 19 项筛选测试全通过。
- 此前一次全量 `:core:test` 在 `:core:compileTestKotlin` 因并行 W03 测试的三处源码编译错误中止；W01 修复后报告 W03 定向 16 项测试通过。随后 W02 单类筛选测试通过。此次未因 W02 扩展而重跑全量测试，以避免覆盖并发 W03 测试结果目录；包级集成测试仍由总控统一安排。
- 编码复核：`uth-utf8-guard` 检查本 Feedback 通过 UTF-8、乱码标记和 Markdown 围栏检查。实际模型服务、Android 正式入口及设备验证仍待 T01.08。

## 2026-11-15 首次图片发送的目标视觉探测

### 实际改动

- `ModelGateway` 增加默认返回 UNKNOWN 的 `probeVision(model)` 契约，既有假实现不需要改动；OpenAI 兼容适配器实现只执行合成图片的单次视觉请求，其余能力保持 UNKNOWN，不触发模型列表、SSE 或工具探测。
- 视觉探测只有在稳定错误分类明确表明该模型不支持图片时才返回 UNSUPPORTED。视觉专用分类仅对 HTTP 400/422 的明确能力错误生效；认证、限流、配额、过载、服务端错误和网络失败保持 UNKNOWN 并携带原 typed failure。OkHttp 的 `InterruptedIOException("timeout")` 映射为超时，同时取消仍传播 `CancellationException`。
- `OpenAiCompatibleModelGatewayTest` 增加只发一个合成视觉请求、明确不支持与 HTTP 429 不支持措辞的区分、取消传播及 OkHttp 调用超时用例。

### 验证

- Windows PowerShell，Gradle Wrapper 9.9.1：`.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core:test --continue --console=plain --quiet`，构建和 Core 测试通过。
- `core/build/test-results/test/TEST-com.elymbot.core.model.OpenAiCompatibleModelGatewayTest.xml` 报告 23 项、0 failures、0 errors；覆盖本次新增 targeted Vision 用例。使用 MockWebServer 与合成输入，未连接真实模型服务。
- 组合仪器运行结果及设备 APK 信息记在 [W04 首次图片发送视觉路由复验](W04-Android数据与会话-feedback.md#2026-11-15-首次图片发送的视觉路由复验)。

## 2026-11-17 OpenAI 兼容协议职责拆分

- 将请求序列化、普通响应与错误解码、SSE 解析和增量状态分别移入 `OpenAiCompatibleRequestEncoder.kt`、`OpenAiCompatibleResponseDecoder.kt` 和 `OpenAiCompatibleSseDecoder.kt`，并更新 Gateway 内部调用。只调整文件边界与内部引用；模型服务接口及执行语义不变。
- 集成验证：`:core:test` 最终 `BUILD SUCCESSFUL`，Core JVM 测试 50/50 通过，其中 `OpenAiCompatibleModelGatewayTest` 23/23。App 构建及 MuMu Android 15 仪器测试 8/8 的证据见 [W05 配置分类、设置分组与界面拆分](W05-Android界面-feedback.md#2026-11-17-配置分类设置分组与界面拆分)。未连接实际模型服务或实体 Android 设备。
