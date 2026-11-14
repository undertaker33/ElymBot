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
