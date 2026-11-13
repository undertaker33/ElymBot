# W01 总控与集成 Feedback

## T01.01 模块基线与交接契约

执行时段：2026-11-13 23:00 至 2026-11-14 01:08（北京时间）。

### 实际改动

- 在 `core/.../contract/` 建立模型、历史/摘要、Agent、工具来源和 Slash Command 的共享接口。Core 仍是 Kotlin/JVM；ModelGateway 的公开 `Flow` 类型通过 `api` 暴露协程依赖。
- 在版本目录与模块构建中加入 HTTP/JSON、协程、Room/KSP、Lifecycle Compose 和 UI 测试入口依赖。App Manifest 加入 `INTERNET` 权限。
- Core 不引用 Android、Compose 或插件实现。App 运行时项目依赖为 `:core`。
- W05 的 App 接缝由 `app/src/main/kotlin/com/elymbot/android/ui/ElymBotUiService.kt` 提供，Composable 入口为 `ElymBotApp(service)`。W04 实现该 App 服务，W05 的 ViewModel 只经服务获取人格、会话、配置与运行状态；本阶段没有重复定义 Android UI DTO。

### 共享接口交接

- W02：`ModelGateway` 提供模型列表、探测和可取消增量调用。`ModelRequest` 绑定 `serviceId + modelId`，包含消息、图片字节和工具定义；响应按 `TextDelta` 与唯一终态 `Completed(finishReason, toolCalls)` 交付。工具批次只在完整终止并校验后返回，长度截断保留文本但没有可执行工具批次。调用者取消继续传播为 `CancellationException`。
- W02/W04：三项用户可见探测与内部结构化工具探测分别使用 `CapabilityStatus` 三态。`ModelCapabilityReport.failuresByTarget` 默认为空，按 `AVAILABILITY`、`VISION`、`NATIVE_STREAMING`、`STRUCTURED_TOOL_CALLS` 保留各项错误分类，不覆盖其他成功状态；失败分类不含原始服务端消息或凭据。`content_filter` 映射为 `API_REJECTED`；只有已取得 HTTP 状态时才填入状态码。
- W02/W04：Base URL 与凭据解析作为 W02 `core.model` 连接构造依赖。W04 提供按 service ID 解析的实现；API Key 只用于认证头，不进入模型消息、事件或日志。`ModelRequestException` 区分网络、HTTP、API 拒绝、协议、中断、上下文超限和超时。
- W04/W03：`HistoryReader` 按 session ID、稳定轮次闭区间、方向/游标及 token budget 分页，读取上界排除当前输入，序号允许空洞，页内按时间正序；超预算记录标明截断并允许续页。摘要由 `HistorySummaryStore` 独立持久化，提交时核对覆盖范围且不改原文。
- W03/W04/W05：`ToolSource` 提供稳定 ID、名称、参数 schema、作用范围和可取消执行；结果带原调用 ID，并区分成功、拒绝与失败。`AgentRuntime` 按 session、execution、attempt 和稳定轮次接收快照，发出带 execution/attempt ID 的文本增量、助手工具反馈和终态。受控终态含中断、失败、超时、上下文/输出超限；调用取消保持取消语义。工具进度由宿主做有界脱敏后呈现。
- W03/W04：`SlashCommandParser` 将可改名的五项指令解析为类型化本地动作，不执行 App 状态变更。人格列表、检查点数据和界面回执由 App 负责提供。
- W03/W04/W05：Agent 失败种类另含 `VISION_MODEL_MISSING` 与 `VISION_CAPABILITY_UNKNOWN`，分别支持缺少视觉模型的配置提示和视觉能力未确认时的探测提示。W04 将 `failuresByTarget`/Agent 失败类型映射到 App 的 `UiIssue`；W05 按项呈现三项探测及对应提示，内部工具探测不作为第四项展示。
- W02、W03、W04、W05 可直接消费 `core/src/main/kotlin/com/elymbot/core/contract/` 下的契约；具体实现按各自 Prompt 的文件范围完成。

### 验证

- 当前工作区最初运行 `:core:compileKotlin :app:compileDebugKotlin` 因执行进程没有 `ANDROID_HOME` 而在 App 任务配置阶段停止。没有写入机器专用 `local.properties`。
- 为隔离 W02/W05 并发未跟踪源码，用 `git archive HEAD` 导出基线至 `C:\Users\93445\AppData\Local\Temp\elymbot-t01.01-w01-33f358fe2495433da051d09c1109e44d`，只覆盖 T01.01 文件 allowlist。临时快照另加一份仅用于编译的 App consumer，引用 `ModelGateway`、`AgentRuntime`、`HistoryReader`、`HistorySummaryStore`、`ToolSource` 和 `SlashCommandParser`，不进入仓库。
- 环境：Windows 11、JDK 17.0.17、Gradle Wrapper 9.9.1、Kotlin 2.4.21、AGP 9.5.1、Android SDK Platform 37；Gradle 进程使用 `ANDROID_HOME` 与 `ANDROID_SDK_ROOT=C:\Users\93445\AppData\Local\Android\Sdk`。命令 `:core:compileKotlin :app:compileDebugKotlin` 通过，Core 为 JVM 模块，App 编译并消费了共享接口。
- 在同一隔离快照运行 `:core:compileTestKotlin :app:compileDebugAndroidTestKotlin` 成功，两个 Kotlin test 编译任务均为 `NO-SOURCE`；本阶段未执行自动化测试，也未组装 APK。
- `:app:dependencies --configuration debugRuntimeClasspath` 成功，App 项目依赖仅为 `:core`。`:core:dependencies --configuration compileClasspath` 成功，Core 解析到 `kotlinx-coroutines-core:1.11.0`、`okhttp:5.5.1` 和 `kotlinx-serialization-json:1.13.0`，未出现 AndroidX 或插件模块。
- 初次检查 `debugAndroidTestRuntimeClasspath` 时发现 Compose UI Test JUnit4 没有版本约束；将 Compose BOM 也加入 `androidTestImplementation` 后，依赖图解析到 UI Test JUnit4 1.12.1、AndroidX Test runner/rules 1.7.0、ext-junit 1.3.0 和 Room testing 2.8.5。`core:testCompileClasspath` 解析到 JUnit4 4.13.2、coroutines-test 1.11.0 和 MockWebServer3 5.5.1。
- 增补视觉失败类型与分项探测失败 map、Compose test BOM 后，隔离快照 `:core:compileKotlin :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` 复验通过；App Android test Kotlin compile 为 `NO-SOURCE`。Room/KSP、Lifecycle、AndroidX Test 和 Compose UI test 依赖均成功解析。

### Checklist 对应项与限制

- 对应 T01.01。模块编译、依赖图和跨模块接口编译消费通过，故 Checklist T01.01 已勾选。
- W02/W03/W04 的模型服务、上下文/摘要、Agent 和 Room 实现及运行测试尚未完成；正式 MainActivity 装配、APK 与端到端证据留待 T01.08。
