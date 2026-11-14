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

## T01.08 正式入口装配准备

执行日期：2026-11-14（北京时间）。

### 实际改动

- 新增 `app/src/main/kotlin/com/elymbot/android/ElymBotApplication.kt`，由进程级 `Application` 持有唯一 `ElymBotUiService` 和协程作用域。装配链为 `ElymBotDataRepository` → `RoomModelServiceConnectionProvider` → `OpenAiCompatibleModelGateway`、`RoomHistoryStore` → `ContextCompiler` → `ReActAgentRuntime` → `DefaultSlashCommandParser` → `ElymBotSessionService`。同一个 Room history store 同时提供 `HistoryReader` 与 `HistorySummaryStore`；Agent Runtime 内建 `HistoryReadToolSource`。
- 在 `app/src/main/AndroidManifest.xml` 注册 `ElymBotApplication`，`MainActivity` 改为调用 `ElymBotApp` 并注入进程级服务，使页面离开不销毁会话执行所有者。
- 新增 `app/src/androidTest/kotlin/com/elymbot/android/MainActivityIntegrationTest.kt`，从正式 Activity 入口启动并等待默认人格库呈现，作为装配启动路径的 Android 集成测试入口。

### 静态核对与限制

- 与 W02、W03、W04 负责人核对当前构造器和文件边界，并检查实际源码实参：`ElymBotSessionService(context, repository, connectionProvider, modelGateway, agentRuntime, commandParser, scope)`；`ContextCompiler(historyStore, historyStore, modelGateway)`；`ReActAgentRuntime(modelGateway, contextCompiler, historyStore)`。未修改其他 Worker 的实现文件。
- `git diff --check` 未报告空白错误；新增文件已人工核对。尚未执行 Gradle 编译、Android 测试、APK 组装或设备操作；W03/W04 源码仍在返工，本轮不与其并发跑共享 Gradle。
- Android 集成测试尚未执行，因此只确认测试入口已写入，不声称其编译或通过。需待 W03/W04 交付稳定后协调统一 Gradle 验证；设备安装与实际服务验收仍待独立确认。

### Checklist 对应项

- 本阶段对应 T01.08 的正式入口准备。准备阶段 Checklist 的 T01.08 端到端验收、APK、插件隔离、模型服务和设备项均保持未勾选；当时尚无端到端或用户验收证据，后续复验见下节。

### Android 正式入口与双会话合成复验

执行日期：2026-11-14（北京时间）。本节补充并更新上面的准备阶段状态。

#### 测试装配

- 新增 `app/src/androidTest/kotlin/com/elymbot/android/AndroidTestAppFixture.kt`。仪器测试先创建自有测试人格、主会话和空检查点，再初始化 App 数据仓库；离场时清理本次新增的人格/会话并恢复先前主会话。启动测试与双会话测试不依赖生产九个示例人格，可继续用于移除预设后的 APK。此 fixture 预置了测试人格和主会话，因此不证明用户从真正空库首次启动的引导行为。
- 新增 `app/src/androidTest/kotlin/com/elymbot/android/AppSessionIntegrationTest.kt`。测试启动正式 `MainActivity`/Compose 页面，在 Room 中配置本地 OpenAI-compatible 模型服务，再使用仪器进程内 `ServerSocket` 提供 `127.0.0.1` SSE 端点。新增仅供 debug manifest 使用的 cleartext 网络配置，允许 `localhost` 回环；release/主 Manifest 不添加明文网络例外。
- 测试通过实际模型网关发送 `stream:true` 请求，并用可控 SSE 闸门证明 A 首段已显示后 A 仍运行；离开 A 创建并发送 B，B 首段显示且 A/B 仍各自在自己的 turn 中运行；重新进入 A 看到第二段；离开页面后释放 A、B 终态。最终从 Room 检查 A/B 各只有对应的用户消息和回复、turn 均为 `COMPLETED`，并检查未读持久状态及 Compose 会话行内的未读 badge 位置。
- `MainActivityIntegrationTest` 改为通过同一自有 fixture 验证正式 Activity 能呈现 Application 服务提供的人格。测试 UI 查找改为允许重名文案节点；双会话 fixture 让 A/B 的唯一部分落在会话标题 26 码点限制内，断言使用产品实际截断标题。

#### 构建和运行结果

- 环境：Windows、Gradle Wrapper 9.9.1、Android SDK `C:\Users\93445\AppData\Local\Android\Sdk`；安装在 MEIZU 20 Pro（Android 16，序列号 `391QYGCM225VG`）。首个 Gradle 命令进程未继承 `ANDROID_HOME`，因此只因 SDK 定位失败；随后对当前 Gradle 进程临时设置 `ANDROID_HOME`，未写机器专用 `local.properties`。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 通过，包含 `:app:compileDebugAndroidTestKotlin`；仅有 `createAndroidComposeRule` 的弃用警告。APK：`app/build/outputs/apk/debug/app-debug.apk` 与 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。
- 合并执行四项的 `:app:connectedDebugAndroidTest` 曾因 A/B 共享被截断的 UUID 前缀，使 Compose 点击选择到人格卡和两个会话行而失败；当轮 XML 中 `MainActivityIntegrationTest`、W04 文字标题和真实 `MediaStore content://` 图片标题/读取/完成用例均通过。双会话测试只修了测试数据前缀后重新编译；没有改产品 UI 或 W04 实现。该 XML 保存在 `app/build/outputs/androidTest-results/connected/debug/TEST-MEIZU 20 Pro - 16.xml`，记录的是修正前的 selector 失败，不代表最终双会话结果。
- 上述 Gradle connected 后续曾遇到 Android 拒绝更新 androidTest APK（`INSTALL_FAILED_USER_RESTRICTED`，同时安装器报告 `DELETE_FAILED_INTERNAL_ERROR`）；未改系统设置，也未清除用户数据。按协调窗口对最新两个 APK 分别执行普通 `adb install -r` 后均返回 `Success`，`pm path com.elymbot.android.debug` 和 `pm path com.elymbot.android.debug.test` 均返回 APK 路径；未触碰基础包 `com.elymbot.android`。
- 最终在上述设备只运行修正后的双会话仪器方法：`adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.AppSessionIntegrationTest#mainActivityStreamsAcrossTwoSessionsAndPersistsReplyAfterLeavingPage' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (1 test)`，耗时约 6.4 秒。该结果是本地 loopback 合成服务证据，不是实际模型服务或凭据验证。
- `git diff --check` 通过；仅出现既有 CRLF 转换提示，无空白错误。

#### 尚未完成的 T01.08 验收

- 已由本轮正式入口合成测试覆盖：MainActivity 装配启动、SSE 首段可见后再放行下一段、双专属会话并行、离页继续生成、重进会话看到增量、离页完成后的消息落盘、会话间消息隔离和未读状态/badge。另有 W04 的文字标题与真实图片 URI 读取/完成两个定向仪器测试通过。
- Checklist 的 T01.08 复合项继续保持未勾选。正式 App 内的非流式、停止和断流；视觉直连与次级模型两路；检查点回退及回退后历史不可读；批量删除、新消息清除及图片位置；HistoryRead 能力定义随请求发送、压缩后回读续答；依赖图和 APK 对可选插件实现/资源的隔离，仍需按各自尚有效的证据补验。移除九个人格/图集后的空库首次创建也尚未由当前 fixture 覆盖。
- Checklist 中“实际验收服务与 Android 设备完成模型探测和正式对话”尚未验证。本轮未读取或配置真实服务凭据；需要总控确认可用服务、模型/能力探测范围及设备验收安排后执行。T01.09 交付和用户最终验收也尚未完成。

### 无内置预设 APK 与最终合成复验

执行日期：2026-11-14（北京时间）。本节记录 no-seed 生产源码与标准 debug APK、W04 最后一轮仪器测试及 APK 隔离检查。Checklist 的复合 T01.08 仍不勾选。

#### 构建与设备结果

- 在 W04 源码冻结后执行 `:app:assembleDebug :app:assembleDebugAndroidTest`，Gradle Wrapper 9.9.1 / Android SDK Platform 37 构建成功，包含 `:app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin`。构建只通过当前 PowerShell 进程环境变量指定 `ANDROID_HOME`、`ANDROID_SDK_ROOT`，未写本机配置文件。
- 产物为 `app/build/outputs/apk/debug/app-debug.apk`（14,204,283 bytes）与 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`（1,405,424 bytes）。按普通 `adb install -r` 更新 MEIZU 20 Pro（Android 16，`391QYGCM225VG`）的 `com.elymbot.android.debug` 与 `com.elymbot.android.debug.test` 均返回 `Success`；未安装、卸载或清除基础包 `com.elymbot.android`，未清除 `.debug` 应用数据。
- 亮屏后的 UI 复验无需重装，命令 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.MainActivityIntegrationTest,com.elymbot.android.AppSessionIntegrationTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 返回 `OK (2 tests)`，耗时 8.571 秒。分别覆盖正式 MainActivity 启动，以及本地 `127.0.0.1` SSE 服务上的 A/B 双会话并行、离页继续生成、增量回看、完成落盘与未读状态。
- W04 最后一项磁盘重启回归在同一安装上定向运行：`adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.data.ElymBotStorageTest#diskRestartPreservesSessionHistoryModelsCommandsAndPersonaImagesThenReusesCheckpointNumber' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 返回 `OK (1 test)`，耗时 0.424 秒。该用例关闭并重开 Room 数据库，核对主会话和两个专属会话历史、Text/Vision 模型分配、指令名、封面路径与头像/封面位置，再验证主会话回退及重新建点编号。早前完整 `ElymBotStorageTest` 在同机返回 23/23；此后 W04 只扩展了该磁盘重启方法，扩展后的实际方法已单独通过。一次旧方法名过滤器返回 `OK (0 tests)`，不计入证据。
- 之前屏幕处于 Dozing/锁屏时两项 Compose 测试报 `No compose hierarchies found`；只读 `dumpsys power/window` 记录为 `mWakefulness=Dozing`、焦点在 NotificationShade、`mDreamingLockscreen=true`。用户唤醒设备后，前述 MainActivity 与双会话测试均通过。本轮没有更改系统限制或绕过设备安装限制。

#### APK 与依赖隔离

- 用 Android SDK Build Tools 37.0.0 的 `aapt` 检查设备已安装 APK。通过 `adb pull` 只读取出安装包本身；已安装 `.debug` 和 `.debug.test` APK 与对应构建输出的 SHA-256 分别相同。APK package 为 `com.elymbot.android.debug`，versionCode `1`、versionName `0.1.0`、targetSdk `37`。
- 首轮扫描发现 `androidx.compose.ui:ui-test-manifest` 的 `debugImplementation` 将通用 `androidx.activity.ComponentActivity` 测试宿主合进 debug APK。仓库仪器测试均通过真实 `MainActivity` 启动，因此移除了这条仅用于通用 Compose 宿主的 debug manifest 依赖并重建。最终目标 APK 的 manifest 仅注册正式 `MainActivity`，无 `ComponentActivity` 通用测试宿主，也无 `AcceptanceComposeActivity`；目标 DEX 有 `MainActivity`，不含 `MainActivityIntegrationTest`、`AppSessionIntegrationTest` 或 `ElymBotStorageTest`。这些测试类只在 `debug-androidTest.apk` DEX 中。
- `aapt` 清单未发现人格图集或人像栅格资源；应用自有 drawable 为界面向量图标，另含 `gallery_song.otf` 字体及其 license。APK 的 `assets/PublicSuffixDatabase.list` 来自 OkHttp。debug network-security 配置仅为 `localhost` 启用明文回环，主 Manifest 未扩大该配置。
- `:app:dependencies --configuration debugRuntimeClasspath` 的实际树中唯一项目模块为 `:core`；未见 `:plugins:*` 依赖。`:core:dependencies --configuration compileClasspath` 仅含 Kotlin 标准库、coroutines、OkHttp 和 kotlinx serialization，无 Android、Compose 或插件实现依赖。仓库 `settings.gradle.kts` 虽包含可选插件项目，但它们不在 App 运行时依赖图或该 APK 的 Activity/资源清单中。
- 源码核对 `ElymBotDataRepository.initialize()`：空人格表时不写入人格实体；只有已有首个人格时才创建主会话。W04 的 `emptyDatabaseCreatesMainWithFirstPersonaAndSurvivesRestart` 在前述 23/23 StorageTest 中通过，使用隔离 Room 数据库验证首次创建、事务失败回滚、重启保留且不预置人格。无种子逻辑与 APK 资源扫描共同证明交付 APK 不捆绑九个人格或其图集。

#### 数据边界与剩余验收

- 只读设备核对时前台为 Meizu Launcher，最近暂停 Activity 是基础包 `com.elymbot.android/.MainActivity`；`.debug` 数据目录存在 `elymbot.db`（163,840 bytes）及 SQLite WAL。没有查询人格行、读取凭据或复制数据库，因此不能把用户先前看到的九个人格归因到 `.debug` 数据库；已安装更新采用 `-r` 并保留旧库。
- 独立空库 Room 测试不等同于 Compose 的真正空库首次创建 UI 路径。本轮尝试的 test-only Compose Activity 首次放在 instrumentation APK 时遇到进程不匹配；临时 acceptance variant 的新包安装被 Android 返回 `INSTALL_FAILED_USER_RESTRICTED`。未改系统设置；为恢复常规 `connectedDebugAndroidTest` 入口并确保交付 debug APK 不含测试宿主，已移除该临时 variant、Activity 和 Compose 测试源码。现有 MainActivity 测试 fixture 会先创建测试人格，故真正空库 Compose 首次创建仍未验收。
- T01.08 仍未覆盖或未形成正式入口证据的项：非流式、停止与断流；视觉模型直连及次级模型两路；回退后的 HistoryRead 不可读、结构化能力随模型请求发送及压缩后续读续答；批量删除、清除未读和图片位置 UI 操作；实际模型服务探测与正式对话。W04 已有服务层/Room 覆盖不替代这些 App 入口行为。正式服务验收尚未读取或配置凭据；需由总控另行安排可用测试端点。
- 测试 Activity 依赖移除后没有重跑 MainActivity/双会话 UI 用例；改动只删除了通用 `ComponentActivity` 测试宿主，正式 `MainActivity` 和 localhost 配置未改。该次构建与静态 APK 检查通过，亮屏下的 2/2 UI 证据仍来自移除宿主前同一代码和设备。

### App 正式入口断流与重试复验

执行日期：2026-11-14（北京时间）。

#### 实际改动

- 新增 `app/src/androidTest/kotlin/com/elymbot/android/AppFailureIntegrationTest.kt`。通过 `MainActivity`、共享测试数据 fixture、本地 HTTP SSE 端点及 Room 状态，验证部分增量已到达后传输意外结束时的界面错误、重试入口和最终落盘。
- 合成端点首次响应发送部分正文后关闭 chunked 流，未发送终止帧；重试响应返回完整 SSE。测试断言重试复用同一 turn sequence 和 user message ID、attempt ID 更新，结束后该 turn 仅保留一条用户消息和一条完成的助手回复。

#### 构建和设备结果

- 环境：MEIZU 20 Pro，Android 16，序列号 `391QYGCM225VG`；Gradle Wrapper 9.9.1、Android SDK Platform 37。执行 `:app:compileDebugAndroidTestKotlin` 成功，W01 与 W05 仪器测试均编译通过；执行 `:app:assembleDebug :app:assembleDebugAndroidTest` 成功。APK 为 `app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。
- 设备当时 `mWakefulness=Awake`。对 `com.elymbot.android.debug` 与 `com.elymbot.android.debug.test` 分别执行普通 `adb install -r` 均返回 `Success`；`pm path` 可解析两包及基础包 `com.elymbot.android`。未清除数据、卸载或更新基础包，也未读取或配置真实模型凭据。
- 命令 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.AppFailureIntegrationTest#interruptedStreamShowsRetryAndReusesTheExistingUserMessage' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 返回 `OK (1 test)`，耗时 4.519 秒。正式 App 显示回复失败、连接错误和重试操作；合成端点的第二次请求成功后，数据库中的同一轮完成，消息数量和身份断言通过。
- W05 的 `ElymBotUiIntegrationTest` 同次构建后定向运行，返回 `OK (2 tests)`，耗时 12.355 秒；详细 UI 路径由 W05 Feedback 记录。

#### 覆盖与限制

- 本次覆盖 App 正式入口的 SSE 断流、错误呈现、重试及同一用户消息复用，输入和响应来自本地 loopback 合成端点。它不证明实际服务接入、认证、模型列表、三项能力探测、非流式响应或视觉请求；这些仍按后续设备验证补充。T01.08 复合验收仍不勾选。

### 已配置 Text 模型的设置与对话验收

执行日期：2026-11-14（北京时间）。

#### 实际改动

- 新增 `app/src/androidTest/kotlin/com/elymbot/android/ConfiguredModelAcceptanceTest.kt`。仪器测试从现有 Text 角色设置读取服务名、模型 ID 和是否有已保存凭据标记，不读取凭据内容；通过正式模型设置 UI 打开已配置服务、请求模型列表、确认当前模型可选，并启动能力探测。
- 探测 UI 的可用性、视觉、原生流式三个结果行均退出“等待探测”。测试核对这三类结果记录存在，但运行器输出未保留模型名称或每项具体状态值，因此不据此声称任何能力支持或不支持。
- 使用 fixture 临时创建的专属会话，从正式聊天 UI 发送无个人信息的 `DEVICE_TEST_OK` 请求。turn 完成，Room 持久化了非空助手回复；fixture 随后恢复原主会话人格并清理本次测试会话。没有修改已保存服务、Text/Vision assignment 或凭据。

#### 构建和设备结果

- 环境：MEIZU 20 Pro，Android 16，序列号 `391QYGCM225VG`；Gradle Wrapper 9.9.1、Android SDK Platform 37。`:app:assembleDebug :app:assembleDebugAndroidTest` 成功；修复关闭嵌套 Dialog 的测试节点选择后，`:app:assembleDebugAndroidTest` 成功。测试 APK 为 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，目标 APK 为 `app/build/outputs/apk/debug/app-debug.apk`。
- 设备当时 Awake。更新 `com.elymbot.android.debug` 与 `com.elymbot.android.debug.test` 的普通 `adb install -r` 均成功；`pm path` 核对基础包 `com.elymbot.android` 仍安装。本次未清除任何数据、卸载基础包或修改系统设置。
- 命令 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.ConfiguredModelAcceptanceTest#configuredTextModelListsProbesAndRepliesToATestPrompt' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 返回 `OK (1 test)`，耗时 10.148 秒。模型列表已通过正式 UI 返回并显示当前模型可用；能力探测流程结束；正式聊天的合成提示已得到非空完成回复。
- 运行器没有提供模型 ID、服务名、能力状态细节或回复文本的可用输出。本记录只写“用户已配置 Text 模型”，不从设备日志或真实数据库额外提取这些字段。实际请求使用 App 中已配置的认证，但本轮没有读取、输出或复制凭据。

#### 其他验收边界

- 用户反馈识图当前正常；这是用户观察，本轮没有自动重跑图片输入。能力探测在真实服务中提交了产品自带视觉探针，但由于具体状态值未捕获，不把它写成识图能力的自动化证明。
- 这项验收覆盖真实服务配置下的模型列表、设置页三项探测流程和 Text 对话。用户最终选择的真实服务完整验收仍需用户确认，T01.08 其他必需项与 T01.09 未关闭，Checklist 复合项保持未勾选。
