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

## 2026-11-23 T02.04 官方分发宿主与任务插件重激活复验

- `ElymBotApplication` 现在从 `OfficialPluginDistributionConfiguration.load` 读取独立公钥资产，并用官方目录仓库的已验证本地快照恢复插件。缺少或无效的信任配置保留为 `pluginManagement` 可观察状态；目录刷新和 App 更新检查在 Application 作用域异步启动，不等待网络完成后才打开 Room。
- `ElymBotUiService` 发布正式 DTO 与宿主动作：目录/安装运行状态、经验证发布条目、用户请求的插件安装/启用/停用/卸载、App 更新检查/准备/完成 ticket，以及 `ScheduledTasksUiHandle` 的主会话状态观察与首次任务引导 skip。模型的 `official_plugin_catalog` 仅读取已验证目录，不提供安装或更改入口。
- App 更新检查由一个服务共享启动自动检查与 About 手动刷新；只有 UI 显式确认后才准备下载。安装器 Intent 使用已验证 APK 的 FileProvider URI；ticket 保存到 ActivityResult 完成后清理 Verified 文件，并保留用于 Activity 重建的 ticket 元信息。
- scheduled-tasks 插件安装、启用、停用或卸载成功后，SessionService 在宿主侧刷新唯一 WorkManager wake，再按当前 active installation 重采集插件持久队列、提交 Room 幂等 Inbox 并 drain；停用/卸载会取消旧 wake。UI 不直接操作调度器。
- 总控在 MuMu Android 15 / x86_64 上报告 `ScheduledTaskHostIntegrationTest` 的 3 项全部通过、0 skipped：恢复 readiness barrier、签名 W05 typed UI capability 与 skip 持久化、以及 RunNow busy queue 场景扩展的 disable→enable 新身份、nextWakeAt 重登记和 queued run drain。
- 联编 App APK SHA-256：`C0B64124510595193FB0FEE60C5BD93B110D063C13F7C1B95E1C53ABB8B4203F`；AndroidTest APK SHA-256：`EF136CF7CDE91A259430EDD02BBE3CD774ABF84C4EE0FACED6B64726B1BC8513`。本轮 App + AndroidTest 编译和上述定向 MuMu 仪器测试通过；平台运行/QQ 登录与消息端到端仍待 W04 的 App runtime handoff。

## 2026-11-24 平台宿主编译与路由回归修正

- 修复第二轮宿主编译诊断：Provider 使用本地 route actor/account 值避免跨模块 smart cast；QQ 入站授权改为有效 route helper；Room 锁结果统一为 `InboundAcceptResult`；平台账号 StateFlow 映射和 `withActivePluginHandle` 结果显式类型化。`ElymBotComponents` 的 `ReadOnlyConversation` 映射由 W06 单独修复。
- 群触发 origin 保留 `group:<群>:member:<成员>`，Reply target 校验为同账号下已持久化的纯群 conversation，并在快照和发送前重查该成员及当前 D07 群授权。私聊 target 仍绑定原发送者。引用校验与出站 `MessageReference` 统一使用目标 route；获准群公共上下文在 `target=null` 时仅允许与共享 group route 匹配的引用。
- Owner DM 设置写入后若插件在返回 `Saved` 前取消，宿主在 `NonCancellable` 下重读实际设置状态和 revision，最多按当前值重试移除 App 自动增加的窄私聊 grant。只有确认 grant 已不存在才删除 pending provenance；无法确认时保留 `ownerBound=false` 的自动授权标记，入站和投递均按失效拒绝。
- PRoot guest launch 将新进程先登记为宿主持有资源，再交回 QQ 插件。切换回调前取消或身份失效时，宿主通过 `NonCancellable` 停止并释放 guest。新增 fake-hand-off AndroidTest、owner 写入后取消与失败补偿 AndroidTest、群 origin/target/引用 AndroidTest，以及 Core supplemental context 字符上限相邻消息回归源码。
- 本轮未运行 Gradle、Android 仪器测试或设备操作；以上测试源码待总控执行。App/Core 当前可进入下一轮联编，命令解析保持下一增量。

### 三轮联编窄修

- `handoffPlatformResource` 的取消清理改为在 `NonCancellable` suspend 块中显式取出并清理 pending guest，避免把 suspend lambda 传给普通 `let`。
- `acceptPlatformInbound` 在检查引用 route 前显式读取 `message.target`，保持目标/公共上下文引用校验与已确认 route 语义一致。
- 总控第三轮日志只有以上两处宿主编译错误；修复后工作者冻结源码并等待总控复编。工作者未运行 Gradle、ADB 或 Git。

### 第四轮联编与宿主复验

- `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` 通过。App APK SHA-256 `1C3D53993B2C631F6CF238A3571AC78F3F5F077D227A8B864909B201D2F43726`，AndroidTest APK SHA-256 `F3EC14756F2DBA91DE5A8685683E27EE16720FC36A251D78FA1BA58F7C62E045`。上一轮已运行的 `ContextCompilerTest` 14/14 通过，0 跳过。
- MuMu Android 15 / x86_64 跑定向仪器测试 11/11，0 跳过。四个 [PlatformHostSafetyTest](../../../../app/src/androidTest/kotlin/com/elymbot/android/session/PlatformHostSafetyTest.kt) 覆盖群 origin/target 和引用、设置写入后取消补偿、保留待清理授权标记、guest 交接取消清理。另通过三项签名定时任务宿主测试、三项设置与任务入口测试及真实中英文 Reply。
- 同一 MuMu App 覆盖安装并打开后，Room 从 v5 迁移至 v6。迁移前后人格、轮次、消息、检查点、模型服务及文本分配的记录数相同。
- 此切片提供平台文本入站、会话隔离、Room 出站和所有当前可见已授权目标的快照。平台媒体入口、剩余管理命令、合成多目标实际送达和 ARM64 真实 QQ 往返留作后续验收。

## 2026-11-24 平台媒体入站与账号复核

- Room v7 保留入站段顺序、稳定资源标识和投影状态。普通图片经当前账号、安装身份、D07 与会话授权后，从插件读入 App 私有目录，写入受单项和单消息限制，实际文件签名决定能否进入视觉路径。文字、图像仍可投影，音频、视频、文件及不支持的图像保留类型与状态。切换到不同账号后停止消费旧入站或发送旧目标，临时缺失账号时保留队列等待复核。
- `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` 通过。MuMu Android 15 / x86_64 的 [PlatformHostSafetyTest](../../../../app/src/androidTest/kotlin/com/elymbot/android/session/PlatformHostSafetyTest.kt) 10/10 通过，无跳过，覆盖前置撤权不读、读取中撤权清理、文件签名、字节上限、取消清理、账号边界和保留旧入站记录。与签名定时任务宿主用例联跑共 13/13。App SHA-256 `F32F012C74F391B2AFDCE47D843C4F7ABD3ECE18BC1AA060C4E2C505197F1A54`，AndroidTest SHA-256 `484C5CEBBFE208FAE7F65DFA507D7EBA551E7AAE61D16F3019F9BFD908E0549F`。
- 同一模拟器覆盖安装媒体候选 App 后从 v6 冷启动迁移至 v7，迁移前后的会话、检查点、模型服务、模型用途记录数量相同。合成旧库迁移另验证保留既有入站记录。合成媒体读取调用生产 reader，QQ 实际账号收发与群媒体端到端另行验收。

## 2026-11-24 高审 P2 媒体清理与授权快照修复

- `PlatformInboundMediaReader` 在成功导入后暂时持有文件所有权；后置授权拒绝、取消或图片签名不支持时，在 `NonCancellable` 清理未交给调用方的文件。调用插件复制前，在导入器的复制回调内紧邻 `handle.copyMedia` 再做授权复核。
- `acceptPlatformInbound` 使用局部 `stillAuthorized`：Room 路由授权读取前同步检查安装实例、账号和设置 revision，读取后同步复核；重复记录查询后及最终持久化前均复用该检查。暂存资产的引用判定清理保留原有数据库查询，并在 `NonCancellable` 中完成。
- `PlatformHostSafetyTest` 增加复制完成后阻塞在后置授权挂起点并取消的用例，断言暂存目录清空；原 reader 用例补充复制回调授权失效时不调用插件句柄的断言。
- 本轮未运行 Gradle、仪器测试或设备操作。新增源码和用例待总控联编与定向复验；此前 2026-11-24 的 13/13 MuMu 结果不覆盖本轮改动。

- 总控联编 App 和 AndroidTest，通过 MuMu Android 15 / x86_64 定向联跑 16/16，无跳过。11 项平台测试覆盖新增复制后取消、复制前二次撤权，另通过三项定时任务宿主测试、一项已完成轮次待发恢复和中断轮次/已发送状态校验，以及一项自动和手动版本检查。App SHA-256 `18BE0F03285F6FC7EC1C3F8C5B2E6BA200EE8F31DC6EE9DE2846AB4630FD0269`，AndroidTest SHA-256 `BF6ABD450788A066FBF8F5001CCD3AA978A18158093AEB7344CE68C0A61CED35`。
