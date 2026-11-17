# W04 Android 数据与会话 Feedback

执行记录：2026-11-14。修复与设备复验尚未全部完成。

## T01.05 Android 本地数据与检查点

### 实际改动

- `app/src/main/kotlin/com/elymbot/android/data/ElymBotDatabase.kt` 增加 HistoryRead 投影及两条分页查询：按会话、独占上界、可选轮次范围、方向游标过滤，并在 SQL 中限量；查询联结轮次状态，避免逐条消息读取轮次。
- `RoomHistoryStore.kt` 使用上述查询，保持每页按时间正序返回，并保留请求方向上的独占续页游标、Token 预算截断标记和 `hasMore` 语义。摘要仍校验指定完整轮次覆盖并保留原文。
- `ElymBotDataRepository.kt` 保留初始化期间的全量孤儿扫描；常规清理只处理调用方交付的候选路径，并在删除前重新检查附件和封面引用。
- `ElymBotSessionService.kt` 在删除会话、回退删尾和替换封面后只清理被移除记录所对应的附件或旧封面候选。新导入文件尚未提交数据库引用时，不会被并发的全目录清扫选中。
- `ElymBotStorageTest.kt` 增加分页范围/游标/方向、回退后 HistoryRead 不可读回、目标路径清理、私有路径往返及专属会话归属测试。

### 验证

- 环境：Windows PowerShell，Gradle Wrapper 9.9.1；Android SDK 位于 `C:\Users\93445\AppData\Local\Android\Sdk`。只为命令设置临时 `ANDROID_HOME`，未创建或修改 `local.properties`。
- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`：通过。Core 主源码、App Kotlin、Room KSP 及 Android 测试 Kotlin 均编译成功。
- 首次设备定向运行：`.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.elymbot.android.data.ElymBotStorageTest'`。设备为 MEIZU 20 Pro / Android 16，序列号 `391QYGCM225VG`。共运行 13 项，11 项通过、2 项断言失败，报告为 `app/build/outputs/androidTest-results/connected/debug/TEST-MEIZU 20 Pro - 16.xml`。
- 通过的 11 项：重试复用用户消息及未读状态、认证错误映射、迟到 attempt 过滤、目标清理及路径往返、HistoryRead 范围/游标/分页、双会话并行与取消隔离、摘要覆盖校验、回退删尾与历史不可读、初始化种子、会话上界和反馈过滤、人格图位置与凭据重启持久化。
- 首次失败的两处均为测试预期：批量删除测试在尚未删除人格时错误断言人格数为 8（种子数为 9）；空专属会话在首条消息前不进入列表，符合设计中的“首条消息入列”。已把计数断言更正为 9，并调整会话归属测试为先经会话服务完成首条消息再核对按人格列出；这两项修正尚未获得设备复验。
- 更新后的最新 APK 已由 `:app:connectedDebugAndroidTest` 构建和打包，但 Gradle runner 使用 streamed install 时收到 `INSTALL_FAILED_USER_RESTRICTED`，未运行测试。随后普通安装方式成功安装目标 `app-debug.apk`；对最新 `app-debug-androidTest.apk` 执行 `adb -s 391QYGCM225VG install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 仍收到 `INSTALL_FAILED_USER_RESTRICTED`。设备需用户放行安装限制后再执行定向测试。未卸载或清除数据，未绕过设备授权。

Checklist 的 T01.05 项目仍待更新后设备测试复验。

## T01.06 App 会话服务与持久化接线

### 实际改动

- `app/src/main/kotlin/com/elymbot/android/session/ElymBotSessionService.kt` 在运行事件消费时同时校验 `executionId` 和 `attemptId`；迟到 attempt 的增量、反馈和进度不会更新运行状态或写入历史。
- 完成写入在该会话互斥锁内核对当前运行轮次和标识后执行，避免旧尝试覆盖新尝试的状态。`finally` 用嵌套清理保证终态落库抛错时仍关闭连接快照，并按活动执行对象及其 ID 清除运行态。
- 新增宿主 `AgentEvent.ToolProgress(active)` 的运行态处理，只更新当前会话的临时状态，不写入 Room；从 `Conversation.toolProgressActive` 提供可观察状态，工具结束或取消后清除。UI 字段由共享界面契约提供，文案和等待气泡由 W05 负责。
- 增加并行会话取消/未读隔离、迟到 attempt 过滤、工具进度不落库及停止后清理等 Android 存储服务回归检查；历史回退测试同时验证删掉的轮次不能从 HistoryRead 读回。

### 验证

- 同一 `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` 编译通过。
- 首次设备运行中，`parallelSessionsKeepCancellationAndUnreadStateIsolated`、`staleAttemptEventsAreIgnoredBeforeUpdatingTheConversation`、`retryReusesOneUserMessageAndCompletionUpdatesUnreadOnlyForHiddenConversation` 均通过。其余需依赖“更新测试包安装成功”的复验尚未完成。
- 最新 androidTest 安装因 `INSTALL_FAILED_USER_RESTRICTED` 未启动 instrumentation。T01.06 相关 Checklist 继续保持未勾选。

### 待验事项

- 设备放行后安装最新 `app-debug-androidTest.apk`，重跑 `ElymBotStorageTest`，确认上述两条测试修正和整套 13 项均通过。
- 与 W01 合并正式入口和 W05 等待气泡后，从实际 App 入口补验并行生成、离开后重订阅、最终落盘、新消息提示及临时工具状态。

## 2026-11-14 去预设与竞态返工

### 实际改动

- `ElymBotDataRepository.initialize()` 不再插入九个人格。空库初始化后保持无人格、无主会话；已有人格的旧库仍保留原记录，并在缺少主会话时补建。保留 Room 的 `atlasTile` 列以兼容既有数据库。
- `ElymBotSessionService.savePersona()` 在同一事务插入首个人格、唯一主会话和 `empty:main` 空检查点。人格 DTO 不再读写图集索引；兼容列的新值固定为 0。
- 停止操作首次未找到活动执行时等待该会话启动锁并重新查询；查找结束释放锁后沿用原取消与终态清理路径。
- 模型连接提供器在单个 Room 读事务捕获 Base URL 与密文凭据，再解密该凭据快照。模型服务保存事务内重新读取旧 URL，并原子更新服务、凭据和探测记录。
- Android 存储测试改用独立的合成测试人格，不再依赖生产预设；纯图片测试用程序生成 PNG 写入 MediaStore `content://` URI。增加空库、首人格事务回滚、重启保留、停止启动窗口、URL 与凭据快照/草稿，以及编辑人格后下一轮快照回归。

### 复验

- `:app:assembleAcceptance :app:assembleAcceptanceAndroidTest :app:assembleDebug` 构建通过。中间构建的 acceptance 测试包安装曾被设备拒绝 `INSTALL_FAILED_USER_RESTRICTED`；没有重试或修改设备设置。随后恢复标准 debug 测试入口。
- 标准 debug 产物命令 `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。MEIZU 20 Pro / Android 16（序列号 `391QYGCM225VG`）用普通 `adb install -r` 安装 `app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 均返回 `Success`。
- 设备操作：`adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.MainActivityIntegrationTest,com.elymbot.android.AppSessionIntegrationTest,com.elymbot.android.data.ElymBotStorageTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`。instrumentation 总计 25 项、2 项 Compose 层级发现失败；`ElymBotStorageTest` 全部 23/23 通过，包含本轮新增的空库、凭据快照、停止竞态和最新人格测试。此前凭据用例失败来自测试把临时连接 ID 交给另一个 Provider 实例；现已让服务和 Gateway 共用同一 Provider 并通过设备复验。
- 本轮结果由 instrumentation 终端输出记录，未生成匹配的 XML。当前工作区的 `app/build/outputs/androidTest-results/connected/debug/TEST-MEIZU 20 Pro - 16.xml` 是旧的空报告（0 项），不作为本轮证据引用。中间 acceptance 测试包安装曾被设备拒绝 `INSTALL_FAILED_USER_RESTRICTED`；随后标准 debug APK 与测试 APK 安装、定向运行均成功。
- 同窗口另外两项 Compose 入口测试失败为 `No compose hierarchies found`，不属于 `ElymBotStorageTest`；交 W01 继续诊断。本轮不据此勾选正式入口或 UI 项。

Checklist 未修改。T01.05/T01.06 的存储服务测试覆盖已更新，但需要总控按 Checklist 逐项核对本轮及既有有效证据后再更新勾选状态。正式入口和用户验收仍待相应证据。

## 最后磁盘重开组合回归

- 扩展 `ElymBotStorageTest.diskRestartPreservesSessionHistoryModelsCommandsAndPersonaImagesThenReusesCheckpointNumber`，使用文件型 Room 数据库写入主会话及两个专属会话历史、Text/Vision 模型用途、改名指令、人格封面路径与封面/头像位置；关闭数据库后重新打开并由新 Repository 初始化，再逐项读取核对。
- 在重开后的主会话回退到空检查点，确认仅该会话历史被移除、两个专属会话历史仍在；再创建新轮次和检查点，断言展示编号从 1、2 连续，边界为 0、3，当前检查点指向新记录。
- 此用例刚完成源码修改，尚未重新编译或执行；设备由 W01 占用，等待统一构建和定向回归。此前 23/23 是扩展前的真机证据，不作为本次新增重开组合的通过证据。
- 当前生产 `initialize()` 只读取已有 persona，不再插入九个预设。它会保留既有数据库中的 persona 行；旧设备样例清理未授权，因此没有执行或加入自动删除迁移。
- 本轮只做静态核对，没有占用 Gradle 或设备；Feedback UTF-8 guard、测试源码与 Feedback 的 UTF-8/尾随空白检查通过。新增用例待统一构建后验证。

### 真机定向复验

- W01 在 W04 源码冻结后通过 `:app:assembleDebug :app:assembleDebugAndroidTest` 构建并将两个 APK 更新至 MEIZU 20 Pro（Android 16，`391QYGCM225VG`），详见 [W01 无内置预设 APK 与最终合成复验](W01-总控与集成-feedback.md#无内置预设-apk-与最终合成复验)。
- 在该设备运行 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.data.ElymBotStorageTest#diskRestartPreservesSessionHistoryModelsCommandsAndPersonaImagesThenReusesCheckpointNumber' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，返回 `OK (1 test)`，耗时 0.424 秒。该结果覆盖扩展后的磁盘重开、主会话回退和重新建点。早前同机 23/23 执行后，W04 对存储测试只扩展了这一用例。

## 2026-11-14 检查点界面服务接线

### 实际改动

- `ElymBotSessionService` 实现 `checkpoints(sessionId)`、`saveCheckpoint(sessionId)` 和 `restoreCheckpoint(sessionId, displayNumber)`，直接映射 W05 的 `ChatCheckpoint`。
- Room 按检查点建点顺序发出列表；消息数通过会话内 `HISTORY` 用户/助手消息按轮聚合，再按各检查点边界累计。指令回执、助手工具反馈和界面临时提示不计入数量。
- UI 建点与 Slash Command `/mark` 共用同一会话互斥锁及建点事务；均要求末轮已完整结束并将新点设为当前点。UI 回退与 `/checkpoint N` 共用删尾事务，保留所选检查点、删除后续检查点及摘要，清除失效附件并设定当前点。生成中回退或建点返回 `Busy`；没有完整边界的 UI 建点返回 `NoCheckpointBoundary`。
- UI 回退通过现有会话消息流追加助手纯文本回执，不合成 Slash Command 用户消息。新增 `typedCheckpointApiListsRetainedHistoryRestoresAndBlocksMutationsWhileGenerating`，覆盖固定空点、当前标记、消息计数排除项、回退回执、编号复用和生成期间拒绝变更。

### 验证

- 静态核对本次差异；`git diff --check` 通过，Feedback 严格 UTF-8 读取通过，本地链接 1/1 可达，代码围栏配对（0 组）。Git 仅提示工作区 LF 将在后续操作时转为 CRLF。
- 本轮未运行 Gradle、仪器测试或设备操作；新测试待 W01 统一构建并由验收负责人执行。编译及运行结果以该次统一验收记录为准。

#### 真机定向复验

- 2026-11-14（北京时间），在 MEIZU 20 Pro（Android 16，序列号 `391QYGCM225VG`）已安装的 `com.elymbot.android.debug.test` 上运行 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.data.ElymBotStorageTest#typedCheckpointApiListsRetainedHistoryRestoresAndBlocksMutationsWhileGenerating' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，返回 `OK (1 test)`，耗时 0.297 秒。
- 用例使用 Android 仪器环境和内存型 Room fixture，覆盖 API 检查点的固定空点、保留消息计数、当前点、回退回执、编号复用及生成中拒绝变更；没有访问 `.debug` 用户数据库、模型配置或凭据。此次运行未重建 APK，也未改设备设置。

## 2026-11-14 非流式视觉与 HistoryRead 组合增量

### 实际改动

- `ElymBotStorageTest.nonStreamingVisionRoutesDirectAndThroughConfiguredFallback` 使用内存型 Room、独立的临时模型分配和本机合成 OpenAI 兼容端点。第一轮配置主 Text 模型视觉支持并验证图片直达；第二轮将主模型视觉状态改为不支持，沿已配置 Vision 模型转述，再将描述送回主 Text 模型。请求断言覆盖模型顺序、`stream=false`、图片数据 URI 和转述请求不再附带图片。
- `ElymBotStorageTest.historyReadCanReachCompressedTurnsPersistsFeedbackAndRollbackRemovesLaterHistory` 在内存型 Room 写入八轮合成历史及检查点后的哨兵，以 ReAct 与 RoomHistoryStore 编译并持久化压缩摘要。同一模型响应批次请求合法和缺少 `toTurn` 的 HistoryRead，断言工具结果按原 `call_id` 回填、压缩前历史仍可回读、失败文本持久化为 `FEEDBACK`，且后续助手答复保存为 `HISTORY`。回退后再次执行 HistoryRead，断言检查点后的哨兵不可见。
- 两个用例不读取或修改 `.debug` 的用户模型分配、会话或凭据；视觉用例仅使用生成并在 `finally` 删除的 2×2 PNG。HistoryRead 用例仅使用内存型 Room 和脚本化 `ModelGateway`。

### 构建与设备状态

- 环境：Windows PowerShell、Gradle Wrapper 9.9.1。只为 Gradle 命令临时设置 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT`。MEIZU 20 Pro / Android 16 序列号 `391QYGCM225VG` 用于安装限制诊断；最终仪器测试在总控交接的隔离 MuMu Android 15 实例（型号 `ABR-AL80`，序列号 `127.0.0.1:16416`）执行。
- 最终视觉能力状态已核对为主模型 `model-text` 首轮 `VISION=SUPPORTED`、`NATIVE_STREAMING=UNSUPPORTED`；切换后主模型 `VISION=UNSUPPORTED`，Vision assignment 指向 `model-vision`，其 `VISION=SUPPORTED`、`NATIVE_STREAMING=UNSUPPORTED`。最终代码断言端点请求依次为 `model-text`、`model-vision`、`model-text`。
- Gradle `connectedDebugAndroidTest` 在 MEIZU 上试图自动卸载 `.debug` 和 `.debug.test`，日志分别返回 `DELETE_FAILED_INTERNAL_ERROR`；随后只读包列表未发现这两个包，基础 `com.elymbot.android` 仍在。未运行 `pm clear`、未改系统限制。总控授权恢复 `.debug` 后，MEIZU 上普通 `adb install -r app-debug.apk` 返回 `Success`；恢复前包路径缺失，因此不能确认该包原私有数据是否保留。基础包未触碰。
- MEIZU 上测试 APK 普通 `adb install -r app-debug-androidTest.apk` 与一次 `adb install -r -t app-debug-androidTest.apk` 均返回 `INSTALL_FAILED_USER_RESTRICTED`。`aapt` 未在测试 APK Manifest 发现 `android:testOnly` 标记；未发现 PackageInstaller 确认 Activity，包管理器筛选日志没有可脱敏的拒绝原因。MEIZU 最终保留 `.debug`、没有 `.debug.test`；后续仪器测试全部改在隔离 MuMu 序列上进行。
- 只读 MuMuManager 6.8.8.0 帮助和 `info --vmindex all` 显示现存用户实例为 index 0、Android 15 且未启动；总控另行交接的测试实例为 `127.0.0.1:16416`。安装前该测试序列没有 `.debug` 和 `.debug.test` 包。仅在此序列用普通 `adb install -r` 安装 debug 与 androidTest APK，均返回 `Success`；之后只更新测试 APK，不接触 MEIZU 或 MuMu index 0。
- 最终 `:app:assembleDebugAndroidTest` 通过，AndroidTest Kotlin、Dex 与 APK 打包成功；包含本节两个 W04 用例及当时冻结的 W05 测试源码。最后一次该构建也成功编译 W05 新增的 stop 测试；该 stop 测试本轮没有执行。
- 初次视觉试跑使用 `Fixture.service()` 默认假 `AgentRuntime`，断言得到 `reply`，未证明模型适配器。修正为 `ReActAgentRuntime + ContextCompiler + OpenAiCompatibleModelGateway` 后，在 MuMu 定向运行 `nonStreamingVisionRoutesDirectAndThroughConfiguredFallback`，通过；同批的早期 HistoryRead 方法失败，不能把该批总体记为通过。
- HistoryRead 首次有诊断的 MuMu 运行得到终态 `CONTEXT_LIMIT`，`responseRequests=1`、`summaryRequests=3`、`summaryRows=1`，已写入 `HISTORY,FEEDBACK,HISTORY`。缩短 turn 1 合成文本后该终态问题消失；随后检查发现编译器为满足请求上限移除了已持久化摘要。最终将测试上下文窗口调至 3000、压缩触发比例调至 0.4，并保留其他较长历史轮次，使摘要出现在模型请求中。
- 最终执行 `adb -s 127.0.0.1:16416 shell am instrument -w -e class 'com.elymbot.android.data.ElymBotStorageTest#historyReadCanReachCompressedTurnsPersistsFeedbackAndRollbackRemovesLaterHistory' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，返回 `OK (1 test)`，耗时 0.396 秒。用例证明模型请求拿到压缩摘要；同批合法 HistoryRead 读取摘要覆盖范围中的原始 marker，缺少 `toTurn` 的调用按原 call ID 回填拒绝结果，`FEEDBACK` 文本先于最终 assistant `HISTORY` 保存。回退后第二次真实 App service 请求仍读到保留 turn 1；读取被删的 turn 9 返回空记录且无哨兵。
- 本轮没有从用户配置的 MEIZU App 模型设置页读取模型 ID 或既有探测状态；未触发真实服务网络请求，也未打开密钥编辑界面。Checklist 的 T01.08 保持未勾选。

## 2026-11-15 检查点确认防陈旧状态

### 实际改动

- `ChatCheckpoint.id` 暴露现有 `CheckpointEntity.id`；`ElymBotUiService.restoreCheckpoint` 使用 `restoreCheckpoint(sessionId, displayNumber, expectedCheckpointId)` typed 契约。
- UI 恢复沿用原会话互斥锁和删尾 Room transaction。事务内先检查会话及生成状态，再按会话和展示编号读取检查点，并在任何删除前比较预期 ID；编号对应的检查点已替换或不存在时返回现有 `UiIssue.InvalidInput`，生成中仍优先返回 `UiIssue.Busy`。`/checkpoint` 和 `/reset` 继续走不带预期 ID 的私有恢复路径。
- 扩展 `ElymBotStorageTest.typedCheckpointApiListsRetainedHistoryRestoresAndBlocksMutationsWhileGenerating`：记录旧编号 2 的 ID，回退后重新建立编号 2，再以旧 ID 发起恢复，断言受控拒绝、检查点/current ID 和最新轮次保持不变。同步更新所有 typed restore 测试调用方。

### 验证与待验

- 静态核对服务契约、Room 事务中的比较顺序和全部 Kotlin 调用点；没有发现遗漏的旧签名调用。
- 本轮按集成安排未运行 Gradle、Android 测试或设备操作。编译及上述定向测试等待 W01/W05 统一验证；当前不据源码改动宣称构建或测试通过。

## 2026-11-15 用户反馈 #2：模型 Key 与能力缓存

### 原因核查与改动

- `ElymBotSessionService.saveModelService` 按服务 ID 保存配置，原 DAO 使用 `@Insert(REPLACE)`。SQLite 替换父服务行会触发 `service_credentials` 和 `model_probe_results` 的 `ON DELETE CASCADE`，即使服务地址与 Key 未变也会清除凭据和探测结果；空 Key 且地址不变时保存分支又跳过凭据写入，因此无法恢复被级联删除的 Key。
- 服务更新改用 Room `@Upsert` 保留服务行和子记录。保存入口对比已保存地址及 Key：同地址空 Key 保留原 Key；同值 Key 不重新加密；地址变化或 Key 实际变化时同事务清空探测结果；地址变化且未提供新 Key 时删除旧凭据。模型用途 assignment 继续保留。
- 新增 `ElymBotStorageTest.savingModelServicePreservesAndInvalidatesProbeCacheWithCredentialIdentity`，检查空 Key 保存与同值 Key 保存保留凭据、密文与探测结果，Key 轮换和地址变化使探测失效，并核对地址变化时 Key 按既有规则删除、模型用途 assignment 保留。

### 验证

- Windows PowerShell，Gradle Wrapper 9.9.1：`.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --quiet`，通过。
- 使用 MuMu `ElymBot-T01-Clean`（ABR-AL80，Android 15/API 35，`127.0.0.1:16416`）安装同批 debug 与 AndroidTest APK，运行 `ElymBotUiIntegrationTest#savingAnUnchangedModelServiceKeepsItsApiKey`、`ElymBotStorageTest#savingModelServicePreservesAndInvalidatesProbeCacheWithCredentialIdentity`、`ElymBotStorageTest#modelServiceUrlAndCredentialUpdatesStayAtomicAcrossSnapshotsAndDrafts`，结果 `OK (3 tests)`，耗时 3.593 秒。debug APK SHA-256 前缀 `F04499BDB9AA`。仅使用隔离 Room 数据和合成凭据；未访问已配置的真实模型服务或用户数据库。
- W05 的界面回归与本节 W04 数据回归共用该次构建和 MuMu 测试证据，完整命令见 [W05 Feedback](W05-Android界面-feedback.md#保存模型服务后的-api-key)。

## 2026-11-15 首次图片发送的视觉路由复验

### 实际改动

- `ElymBotSessionService` 在首张图片的执行快照中读取主模型、Vision assignment、能力记录、服务地址和加密凭据身份。若主模型 VISION 为 UNKNOWN，在启动 Agent 和读取实际图片前调用 Core 的 targeted `probeVision`；只探测该模型的视觉能力。只有主模型明确 UNSUPPORTED 才检查已配置 Vision 模型；Vision 模型为 UNKNOWN 时才探测它。相同模型引用复用主模型的判定，不重复探测。
- 合成视觉探测结果仅写入 VISION 缓存列，保留 SSE/工具能力记录；UNKNOWN 不写入缓存，避免把暂时失败或并发的明确结果改成 UNKNOWN。探测后的缓存写入在短 Room 事务内核对相同服务 ID、地址和加密凭据快照；网络失败、超时、认证失败、取消或限流不会被解释为 UNSUPPORTED，也不会触发 fallback。缓存写入的陈旧身份或存储异常不改变当前有效请求。
- 人工模型探测使用同一保存身份核对；保存前手工输入的模型仍可探测，不要求先进入模型目录。明确 Key 草稿可使用新 Key 发起探测，即使旧的已保存 Key 无法解密也不会阻断草稿请求；该草稿不写入已保存缓存。
- 新增 `transientPrimaryVisionProbeFailureDoesNotUseFallbackModel`，确认 HTTP 429 时仅探测主模型、不启动 Agent、不探测 Vision 模型，且会话保留可重试的 `MODEL_REJECTED` 终态。扩展 `nonStreamingVisionRoutesDirectAndThroughConfiguredFallback`：首轮 UNKNOWN 主模型经自动探测后将实际图片直传；后续明确不支持时只探测配置的 Vision 模型，再沿既有视觉转述路径处理同一实际图片。断言五个端点请求的模型顺序、synthetic probe image 与实际图片不同、实际图片在直传与 fallback 路由中一致，且转述后回到文本模型的请求不含图片。

### 构建与定向设备验证

- Windows PowerShell，Gradle Wrapper 9.9.1：`.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core:test --continue --console=plain --quiet`，App、AndroidTest APK 和 Core 测试通过；Core 模型适配测试 XML 报告 23 项、0 failures、0 errors。
- MuMu `ElymBot-T01-Clean`（ABR-AL80，Android 15/API 35，`127.0.0.1:16416`）安装同批 APK 后运行：`adb -s 127.0.0.1:16416 shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest#savingAnUnchangedModelServiceKeepsItsApiKey,com.elymbot.android.data.ElymBotStorageTest#nonStreamingVisionRoutesDirectAndThroughConfiguredFallback,com.elymbot.android.data.ElymBotStorageTest#transientPrimaryVisionProbeFailureDoesNotUseFallbackModel' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (3 tests)`，耗时 3.759 秒。
- debug APK SHA-256：`DEC51C34945EC82F48928675046BCB6522A96071928DFC4D82AE6758B912C7CC`；AndroidTest APK SHA-256：`B40529BAF8E0F68E385CE65FDEE999711E3D7A4A8AA847A501F37D2940001F61`。仪器用例只使用隔离 Room fixture、生成 PNG、合成 Key 和本机模拟端点；没有读取用户数据库或连接真实模型服务。

## 2026-11-16 用户纠正：人格标签目录与编辑选择

### 实际改动

- 按“选择是在人格编辑处选”调整服务契约：`ElymBotUiService` 暴露 `personaTags: Flow<List<String>>` 与 `createPersonaTag(tag)`，删除只给旧批量添加界面使用的 `addPersonaTag(tag, personaIds)`。创建仅写标签目录，不修改人格归属。
- Room 升至版本 2，新增 `persona_tags(name, sortOrder)` 目录表。v1→v2 迁移依次读取人格排序和原 `tagsValue` 分隔顺序，对非空精确标签名去重并种入稳定目录顺序；保留原人格成员数据。目录独立于当前成员，因此移除所有人格上的标签不会删除目录项。
- `createPersonaTag` 去除首尾空白、拒绝空值和 U+001F 分隔符，在 Room 事务内按精确名称幂等插入；普通标点与界面保留字由新选择界面处理，不扩展数据层约束。

### 验证与待验

- 静态核对契约、唯一生产实现和数据库打开入口。未增加迁移测试夹具；升级验证使用现有 v1 设备数据库，在覆盖安装新版前由 UI/W01 协调执行并检查已迁移目录及人格标签归属。
- 本轮未运行 Gradle、Android 测试或设备操作；UI 调用接线、构建与 v1→v2 实际升级结果待 UI/W01 记录。

## 2026-11-17 临时指令回执标记

### 实际改动

- `ChatMessage` 末尾新增默认 `localReceipt = false` 字段，兼容原有构造调用。
- `recordCommand` 仅为本地临时助手回执设置 `localReceipt = true`，并保留 `assistant = true`；命令输入、持久化历史映射和 Core 工具反馈不变。

### 验证与待验

- 静态搜索确认只有 `recordCommand` 显式设置 `localReceipt = true`，聊天 UI 可按该字段渲染本地回执。
- 未运行 Gradle、Android 测试或设备操作。新 DTO 的编译与 UI 回归由 W05 使用新构建验证。

## 2026-11-17 程序化人格保存与标签目录一致性

### 实际改动

- 保留 `savePersona` 对 `PersonaCard.tags` 的既有修剪、去空和分隔存储行为。保存事务按写入后实际可解码的标签 token 补齐目录中缺少的精确名称，顺序沿用标签 token 顺序；已有名称不产生目录写入。
- `savePersona` 与 `createPersonaTag` 共用事务内的幂等目录插入入口。人格标签及其余保存字段仍在同一 Room 事务内提交。

### 验证与待验

- 静态核对两条入口均在 Room 事务中调用目录补齐；未运行构建、测试或设备操作。W05 最终构建应使用本次更新后的源码。

## 2026-11-17 人格编辑设为全局

### 实际改动

- `savePersona(persona, makeGlobal = false)` 保持原保存默认行为；显式设为全局时，在主会话互斥锁和同一 Room 事务中保存人格并更新主会话的 `personaId`。生成期间在任何人格或目录写入前返回 `Busy`。主会话 ID、历史和检查点保留，专属会话归属不变；首个人格仍自动成为全局人格，`activatePersona` 保留。
- 事务失败或取消时，对本次导入封面候选在 `NonCancellable` 中调用引用检查后清理；清理异常作为原始失败的 suppressed 异常保留，避免覆盖原始失败或删除已提交引用。

### 验证与待验

- 静态核对服务默认参数、主会话锁、Busy 检查顺序和仅更新主会话人格字段；未运行 Gradle、测试或设备操作。UI/W01 负责本次源码的最终构建和已编写 UI 回归。

## 2026-11-16 Gallery 自定义标签持久化

### 实际改动

- `ElymBotUiService` 增加 `addPersonaTag(tag, personaIds)` 契约；`ElymBotSessionService` 负责规范化标签并校验非空标签、PersonaForm 分隔符和非空所选人格 ID。
- 在单个 Room 事务中确认所有所选 ID 仍有效后才更新。按精确标签名做幂等并集；只在需要新增标签的人格上调用 DAO `update`，通过 `PersonaEntity.copy` 保留名称、描述、提示词、封面位置、头像位置、排序及其他字段。未改 schema、目录编码或 `REPLACE` 写入。
- 未增加 W04 测试；UI 集成回归由 W05 负责。

### 验证与待验

- 静态核对唯一的 `ElymBotUiService` 实现和调用签名；本次两个 Kotlin 文件的 `git diff --check` 通过。
- 未运行 Gradle、Android 测试或设备操作。编译和 UI 集成验证交由 W05/W01 统一执行，结果待其反馈。

## 2026-11-17 App 数据与会话大文件拆分

### 实际改动

- 将 `ElymBotDatabase.kt` 中实体、投影和 DAO 按人格、会话、历史、模型配置/凭据、指令名称分组到五个同包文件；数据库版本、全部实体字段和索引、DAO 查询与方法、Migration 1→2、数据库打开入口保持原样。
- 将模型配置观察流、服务保存/删除、模型列表和能力探测、模型用途分配及探测结果映射移入 `ElymBotModelConfigurationService`。`ElymBotSessionService` 保留既有 UI 服务入口并委托配置操作；本轮模型快照、凭据与连接身份事务、视觉路由、执行 lease、活动执行和会话锁仍由原会话服务持有。

### 验证

- 静态比对基线与拆分后的 Room 声明：29 项声明全部一一对应且文本一致。
- Windows PowerShell，`ANDROID_HOME=C:\Users\93445\AppData\Local\Android\Sdk`，Gradle Wrapper 9.9.1：`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -Pkotlin.compiler.execution.strategy=in-process --console=plain`。App 与 androidTest APK 构建成功；`testDebugUnitTest` 为 `NO-SOURCE`。APK 指纹前缀分别为 `71AE9C7DE403` 和 `FA768040488F`。
- 在 MuMu index 1（Android 15/API 35，`127.0.0.1:16416`）安装对应 debug 与 androidTest APK 后，定向 Android instrumentation 共 8 项全部通过，耗时 19.656 秒。覆盖模型服务 URL 与凭据快照原子性、视觉直达与配置回退路由。终端证据：`C:\Users\93445\Documents\Codex\ElymBot-W05-2026-11-17\config-settings-instrumentation.txt`。
