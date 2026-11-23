# W02 · Android 会话与投递 Feedback

## 实际结果

- Room 数据库升级至 v3，新增 `MIGRATION_2_3`，为既有 Turn 增加成功静默标记，并新增 Reply 提交、逐目标投递、回退执行身份和脱离消息内容的投递回执表。
- `RoomReplySubmitter` 按稳定执行、尝试、轮次、工具步骤及调用序号幂等受理；一次提交中的各条 Reply 在同一 Room 事务中分别建立唯一 App 消息和每目标投递行，事务成功后才返回消息 ID。
- App transcript 目标在受理时保持 `PENDING`。到期释放前，Reply 正文不进入会话渲染、HistoryRead 或摘要预览；到期释放写为 `DELIVERED` 后，App transcript 可显示正文，Core HistoryRead 仍遵守该 Reply 的历史可见目标。未知、失败、取消和失效状态按目标分别保存。
- 停止、下一轮输入、重开恢复和检查点回退处理待发与在途状态；回退撤销执行身份，删除规范消息及附件关系，并只保留已送达或未知外部目标的最小脱离回执。整轮重试会阻止已显示的 App Reply 及已送达、未知或在途的平台效果；`canRetry` 使用相同判定。单目标重试仅接受已确认 `FAILED` 状态。
- `ElymBotUiService` 增加逐目标状态、隐藏消息对应的待投递状态、脱离回执只读数据和单目标失败重试动作。Core 的 ReplySubmitter 尚未接入 T01 的 AgentRequest；当前默认运行仍保留原 T01 路径。
- 新增 Android Room 覆盖：v2 迁移、原子失败回滚、重复受理、历史可见门、成功静默标记、重开恢复、检查点回执、App 延时释放、无活跃生成时取消及整轮重试门控。

## 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `A03–A05` | `$env:ANDROID_HOME='<Android SDK>'; .\gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` | Gradle Wrapper 9.9.1；Android SDK；Windows PowerShell | `BUILD SUCCESSFUL`；71 个任务可执行项中 6 项执行、65 项为最新；App 与 Android-test APK 完成打包，Room KSP 和新增 Android-test 源码编译通过。后续仪器测试结果见 2026-11-23 复验记录。 | `app/src/main/kotlin/com/elymbot/android/data/ReplyDeliveryStorage.kt`；`app/src/main/kotlin/com/elymbot/android/session/RoomReplySubmitter.kt`；`app/src/androidTest/kotlin/com/elymbot/android/session/RoomReplySubmitterTest.kt` |
| `A04、A15` | `RoomReplySubmitterTest` 仪器测试 | MuMu `T01-Clean`；测试由总控执行 | 7 项测试运行，3 项失败。对应断言和测试夹具已修正，等待总控复验；尚无全套通过证据。 | `app/src/main/kotlin/com/elymbot/android/session/ElymBotSessionService.kt`；`app/src/main/kotlin/com/elymbot/android/data/ElymBotDataRepository.kt`；`app/src/androidTest/kotlin/com/elymbot/android/session/RoomReplySubmitterTest.kt` |

## 限制与待验

- MuMu `T01-Clean` 上已运行 7 项 `RoomReplySubmitterTest`，其中 3 项因测试期望或夹具状态失败；对应修正已写入，等待总控复跑，尚无仪器测试通过结论。
- T02.04 持有平台发送分发、系统调度、授权复核及到期触发。当前交付提供 Room 状态转换和到期 App transcript 释放入口，尚未启动投递调度器。
- 正式 Core `AgentRequest.replySubmitter` 接线及平台目标快照由后续交接完成；本包未启用 Reply 新运行时。

## 2026-11-23 总控补充验证

- 在 SQLite 3.51.2 的内存最小 v2 结构上执行 `MIGRATION_2_3` 中 13 条原始 DDL。验证既有会话轮次和消息保留、静默字段默认值、Reply 条目唯一约束、轮次删除时逐目标投递级联删除，以及脱离回执保留。外键检查通过。该结果属于合成 SQLite 冒烟，Android Room 适配仍以仪器测试为准。
- GPT-6.1 Sol High 源码复审关闭 App 入队提前可见、生成后停止及已交付后整轮重试三项问题。见 [W08 Feedback](W08-独立审核-feedback.md)。

## 2026-11-23 仪器测试复验记录

- 总控在 MuMu Android 15 运行 `RoomReplySubmitterTest`，7 项测试中 3 项失败。该轮使用已校验的 App 与 AndroidTest APK。
- `submissionsAreAtomicIdempotentAndHistoryWaitsForTheGate` 原先只断言单条 Reply 取消一个目标；测试输入配置了 `origin` 与 `mirror` 两个待投递目标。现改为核对该条 Reply 的两个目标都为 `CANCELLED`。
- `stopWithoutActiveExecutionCancelsPendingAppReleaseAndKeepsItOutOfHistory` 原断言比较数据层和 UI 层的不同枚举；现改为断言实际 UI 值 `ChatDeliveryStatus.Cancelled`。
- `checkpointRollbackRetiresExecutionAndDetachesOnlyExternalEffects` 的尾轮仍为 `RUNNING`，触发既有检查点回退 `Busy` 保护。任务书没有要求回退运行中的轮次；现将夹具尾轮设为终态，同时保留已送达、未知、失败和待发目标状态。
- 以上均为测试断言或夹具修正，没有产品行为变更。等待总控复跑；Checklist 尚未据此判定通过。

UTF-8 guard:
- files checked: `docs/work/T02-ToolSystem与节奏感reply+插件与平台接入+FAQ/feedback/W02-Android会话与投递-feedback.md`
- result: passed; UTF-8 decoding, mojibake scan, and Markdown fence balance checks succeeded
- repaired encoding issues: none

## 2026-11-23 总控 MuMu 复跑

- `gradlew.bat :core:test :app:assembleDebugAndroidTest --offline --console=plain` 构建通过。App SHA-256 为 `748486d1a3cf24d35af2c7e043ed80b7c74938f9d8dd48c07d089bfc65fc622b`，更新后的 AndroidTest APK SHA-256 为 `5d878fde4b0d507f9ff752567c47f89b0a9cdc597408605a889aa2246828eee5`，已在模拟器核对。
- MuMu Android 15 / API 35 / x86_64 环境，App 安装选择了 arm64-v8a。用 `adb shell am instrument -w -e class com.elymbot.android.session.RoomReplySubmitterTest,com.elymbot.android.runtime.pluginpackage.PluginPackageStagerTest com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 复跑，合计 12 项全部通过，其中 `RoomReplySubmitterTest` 7 项，覆盖迁移、幂等、事务失败回滚、延时释放、生成结束后取消、重开及回退回执。
- 同一 App 的 45 项常规回归通过。其中 `ElymBotStorageTest` 28 项覆盖持久历史、资源、回退及会话运行。另有 App 入口聊天回归覆盖并行会话、流中断重试和停止。正式非流式 Reply 的 App 接线与调度在 T02.04/T02.12 验收。

## 2026-11-23 T02.04 Reply 宿主接线与 App 到期释放

- `ElymBotSessionService.runTurn` 现在向正式 `AgentRequest` 始终提供 Room `ReplySubmitter`。Core 对结构化工具能力未确认或不支持的配置走受控协议失败，不回退到生产 SSE/普通正文路径。
- Application 作用域启动 Room Flow 驱动的 App transcript 释放器。它按会话只处理最早待发消息，等待最近到期时间后重新查询队首，再通过 `releaseDueAppTranscript` 的条件更新释放；取消和检查点回退仍可与释放原子竞争。平台目标不由该本地释放器处理。
- 新增正式 `MainActivity` 合成模型集成源码，覆盖非流式 Reply Tool 调用、按序延迟气泡、生成完成后取消未释放条目及历史可见门；另覆盖最后一条延迟 Reply 在没有后续模型或用户写入时自动到期释放。更新旧 App 入口测试以使用生产 Reply 路径，并检查不支持结构化工具时不发送模型请求、不展示原始正文。
- 本轮仅修改 App 会话/Room 数据代码、Android 集成测试与本 Feedback。工作者未运行 Gradle、未安装或操作设备；构建与仪器测试由总控统一安排，以上 Android-test 仍是待运行源码，Checklist 未据此勾选。
- 交接边界：当前会话服务只新增 `AgentRequest.replySubmitter` 与本机 App transcript 释放；插件运行时调用、平台分发和系统调度等后续接线，待 W03/W05 的已确认宿主接口落地后再联调。

## 2026-11-23 高审 P2 修复与 Reply 回归补充

- App 到期释放现在在一个 Room 事务中重读该会话队首、核对 due 时间、CAS 更新 `APP_TRANSCRIPT` 为 `DELIVERED`，并仅在成功释放时更新 `conversations.unread` 与 `lastActivityAt`。unread 由当前 `viewedSessionId` 决定。定向 MainActivity 测试源码覆盖用户离开会话后的未读标记和最后活动时间；等待总控复验。
- 正式 `runTurn` 不再把 `AgentEvent.TextDelta` 当作草稿或正文。移除执行输出缓存、草稿渲染以及失败/恢复时的空白或原始助手正文写入。恢复中断时保留规范 Reply 正文状态；非 Reply 的既有助手行仍按旧兼容规则标记中断。
- `historyReadCanReachCompressedTurnsWithoutFeedbackAndRollbackRemovesLaterHistory` 已改为压缩上下文、HistoryRead、真实 `reply` Tool、空 STOP、检查点恢复后再次读取历史。无效 HistoryRead 的拒绝只存在于模型工具结果；测试断言没有持久 FEEDBACK 行或会话文案，同时继续核对压缩、读原文和回退边界。
- `ReplyHostIntegrationTest` 不再用 1500ms 窗口判断气泡先后。长延迟项在 generation 完成前后保持 Pending、不可见且不进入 HistoryRead，停止后取消；独立自然到期用例检查无后续请求或用户轮次时到期显示，并通过 MainActivity 会话页和 Room 验证后台投递 unread/lastActivity。
- 新增正式 App 入口 HTTP 重试用例：本地完整响应端点第一次请求返回 503，用户经 Retry 重跑后接收 Reply；断言同一 turn 的 attempt 更换、用户消息 ID 不变且只保留一行。当前 fixture URL 使用 `localhost` 匹配既有 debug 专用网络配置。此前总控在 MuMu Android 15 用 `127.0.0.1` 的 fixture 请求数为 0，Room 终态为 `FAILED/NETWORK`；localhost 版本尚待总控重跑确认。
- `ElymBotStorageTest` 中受影响的 fake AgentRuntime 已改为经 `AgentRequest.replySubmitter` 持久提交，或仅发出终态/取消事件。压缩模型自身的 `ModelStreamEvent.TextDelta` 仍只用于摘要生成，不经过 App 会话正文路径。
- 本轮未运行 Gradle、仪器测试或设备操作。新增源码和上述断言均待总控构建与定向复验；Checklist 未据此勾选。

## 2026-11-23 Reply 主入口 P2 定向复验

- 总控在 MuMu Android 15 使用同一 App APK `F7D0642BE646508DF3CAE3275EAA9D4577D30DEEEC916BBE01C8344CCD1E6C84` 和新 AndroidTest APK `A0645B27AFDF570FBD1D673C5C1100F86A0CDD452B047DEDFEE2413B50DB18F5`，运行本轮两个修正用例，结果 `OK (2 tests)`。
- 覆盖 `pendingReplyStaysHiddenAndStopCancelsItWithoutHistoryLeak` 与 `mainActivityRetryReusesTheUserMessageAndCompletesThroughReply`。前者使用 Core 最大合法单条延迟并点击 MainActivity「停止投递」；后者实际 HTTP 503 不含错误对象，`MODEL_REJECTED` 可重试，重试复用同一用户消息行。自然到期、未读更新、压缩 HistoryRead/回退及跨会话等四项在同 App 的此前四条用例中通过；总控报告这六条正式/历史入口用例现均通过。
- App 到期释放与 unread/lastActivity 断言、长 pending 隐藏及取消、HTTP Reply 重试共用本轮构建产物。接下来总控运行受影响的完整 `ElymBotStorageTest` 与 W05 单测；本 Feedback 不将该待跑组记为通过。

## 2026-11-23 App 入口 Reply 回归复验

- 总控在 MuMu Android 15 / API 35 / x86_64 上安装并运行 `ReplyHostIntegrationTest`、`AppSessionIntegrationTest`、`AppFailureIntegrationTest` 与 `RoomReplySubmitterTest`，结果 `OK (12 tests)`。
- 本次 App APK SHA-256：`46A8CC4DFBE6485D36E82DDEDE6B364F26468B28FBFC53DEFA2C30ECD96B42EF`；AndroidTest APK SHA-256：`FCB103B160C478313738E51FCC34C76C46B730EEBE957F5E0582C148F54FE1AA`。
- 该轮验证通过后，正式 App 入口的非流式 Reply、跨会话隔离、失败/重试、停止及 Room 幂等/历史可见门回归通过。T02.04 定时任务宿主测试仍待执行，不在本条结果范围内。

## 2026-11-23 T02.04 调度宿主与更新安装器接线

- App 服务现在用单一 readiness barrier 串行等待 Room 初始化和 scheduled-run 恢复；系统 wake 与队列 drain 在 barrier 完成前等待。进程启动扫描使用 `KEEP`，Worker 追加下一次 wake 使用 `APPEND_OR_REPLACE`，避免 lazy 初始化替换正在运行的唯一 Worker。
- 新增 `workerWakeWaitsForRepositoryRecovery` 受控 AndroidTest。总控报告该用例在 App APK `EAC9DD4578F8408BDA604A71711CB1B320549A503CB2AC322C4552469ABA8CBC`、AndroidTest APK `D09BE9267D0ED97E769BBD3E6DE51AEEB09249E97646CA3F1766DF67FB10EA0F` 中通过；它阻塞 `beforeInitialize` 并核对 wake 在恢复前未完成、无运行轮次。
- 签名 W05 探针包走实际 `ScheduledTaskHost`/App 主会话入口，测试重复 RunNow 返回同一 runId、主会话忙时只保留一个 queued Inbox 条目、最终完成后回执为 `SUCCEEDED`，且专属会话被 MAIN 授权门拒绝。夹具通过等待异步 startup `KEEP` 回调消除竞速，并断言空任务启动不提交新的替换工作。总控报告该用例 `OK (1 test)`；同一 App APK 对应 AndroidTest SHA-256 为 `6A29549B1FD81CD174632A4C1895266004A6B3529CB324FA3ED7646688FC4B6C`。
- `scheduledTasksSnapshot(sessionId, taskId)` 已将 taskId 透传至插件快照查询，仍先复核调用会话为唯一主会话；供任务详情读取对应运行记录。页面消费由 W06 接手。
- 为 W03 官方 APK 更新确认后的系统安装 Intent 增加 FileProvider：authority `${applicationId}.updates`，只授权 `cache/app-updates/`，并声明 `REQUEST_INSTALL_PACKAGES`。W03 已确认 Manifest 元数据与安装器服务一致；Intent/URI 端到端验证由 W03 安排。
- 本轮总控构建 `:app:assembleDebug` 与 AndroidTest 成功；Worker 未运行 Gradle 或操作设备。此交接不覆盖 W05 typed 任务 UI 状态流、平台出站、定时任务 UI 或最终整包验收。
