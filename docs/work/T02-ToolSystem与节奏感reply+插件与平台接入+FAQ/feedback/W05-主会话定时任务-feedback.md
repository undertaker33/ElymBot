# W05 · 主会话定时任务 Feedback

## 实际结果

- 新增插件公开 entrypoint `com.elymbot.android.plugins.tasks.ScheduledTasksPluginEntrypoint`，实现 Core `PluginEntrypoint` 并提供 typed `ScheduledTasksPluginHandle`。插件构建以 Core 和 JSON 为 `compileOnly`，不把 Core 或宿主实现打进模块。
- 插件仓库使用 W03 提供的插件 ID 私有 `dataDirectory`。构造仓库和候选 `prepare()` 不读写业务数据；正式调用后才惰性访问文件。任务、运行记录和 mutation receipt 以带版本的二进制格式持久化，使用文件锁、校验条目限制和原子文件替换。数据目录跨代码版本稳定，安装身份写入运行记录并单独校验。
- 任务操作共用同一任务记录与校验，支持创建、更新、暂停、恢复、删除及手动立即运行。更新与状态操作使用 `expectedRevision`；手动运行产生独立 QUEUED 记录并保留计划。相同 `requestId` 和操作内容重放返回原回执，内容不同时受控拒绝。
- 工具仅接受宿主验证的 App 主会话 `app/local-user` 来源和 MAIN_SESSION scope。模型写操作返回限时 `ConfirmationRequired`，附带原始动作、发起 execution ID 和标题摘要；批准时复核会话、用户、执行及活动安装身份，拒绝、过期和进程关闭不写入任务库。
- 插件仅维护到期记录，不自建 Android 系统调度或 Agent 队列。`collectPendingRuns` 记录错过及待执行 occurrence，并重复枚举当前安装身份尚未领取的 QUEUED 项；只补最近且迟到不超过 15 分钟的一次。运行入口通过原子 QUEUED→RUNNING 抢占检查任务 revision、启用状态和安装身份，重复领取不会启动第二轮；旧安装身份的 QUEUED/RUNNING 项失效。
- 定向用例覆盖持久化往返、时区及日/周/间隔计算、恢复边界和去重、安装替换、领取幂等、确认权限/拒绝/过期、失效 revision、共享数据、QQ 来源拒绝、暂停任务立即运行，以及创建和立即运行操作重放。

## 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.09 A07/A10/A17`（插件领域部分） | `gradlew.bat :plugins:scheduled-tasks:testDebugUnitTest :plugins:scheduled-tasks:assembleDebug --offline` | 总控执行；JDK 17；Gradle Wrapper 9.9.1；AGP 9.5.1；Android SDK 37 | JVM 单元测试 16/16 通过、0 skip。合成测试覆盖共享存储、模型确认与身份校验、拒绝/过期不写、路由越权拒绝、手动运行分离及 mutation 重放；不等同于 App 页面和正式会话入口验收 | `plugins/scheduled-tasks/src/test/kotlin/com/elymbot/android/plugins/tasks/ScheduledTasksHandleTest.kt`；`FileScheduledTaskRepositoryTest.kt` |
| `T02.09 A11`（插件调度领域部分） | 同上，运行 `ScheduledTaskSchedulerTest` 与 `ScheduleCalculatorTest` | JVM；合成时钟与内存仓库 | 7 项相关测试通过。覆盖保存时区下的周期计算、恢复只补最近一次、15 分钟边界、过窗记 MISSED、同 occurrence 去重、安装替换失效及新领取原子检查；未验证真实系统回调、宿主忙碌队列或进程/系统实际重启 | `plugins/scheduled-tasks/src/test/kotlin/com/elymbot/android/plugins/tasks/ScheduleCalculatorTest.kt`；`ScheduledTaskSchedulerTest.kt` |
| `T02.09` 模块 AAR 与 DEX 打包切片 | 同上；D8 命令见下 | JDK 17；Gradle Wrapper 9.9.1；Android SDK 37；Build Tools 37.0.0 | AAR `scheduled-tasks-debug.aar`，147,821 bytes，SHA-256 `FDE69CC4705F94E3EB65C5150D7D92DD016D09D1E0187B363F50D866D1490B9D`。AAR `classes.jar` 含 entrypoint class。D8 输出 `plugin.jar` 仅含 `classes.dex`，SHA-256 `1445F88F7EB88134BFBE822F2E7D83B6265DE057E63E5EC8CD4D71FA2829AE45`；属于模块 DEX 打包证据，不代表正式签名分发或宿主装载 | `plugins/scheduled-tasks/build.gradle.kts`；`plugins/scheduled-tasks/src/main/kotlin/com/elymbot/android/plugins/tasks/ScheduledTasksPluginHandleImpl.kt` |

以上是插件 JVM、模块 AAR 和总控合成 D8 输入转换证据。W05 未运行 Android instrumentation、模拟器或真机；未执行 Git 写或发布操作。T02.09 Checklist 未由本 Feedback 勾选。

总控 D8 合成打包命令形态（路径通过调用环境解析）：

```powershell
java -cp <SDK>/build-tools/37.0.0/lib/d8.jar com.android.tools.r8.D8 --min-api 26 --lib <SDK>/platforms/android-37.0/android.jar --classpath <core.jar> --classpath <kotlin-stdlib-2.4.21.jar> --classpath <kotlinx-serialization-json-jvm-1.13.0.jar> --classpath <kotlinx-serialization-core-jvm-1.13.0.jar> --classpath <kotlinx-coroutines-core-jvm-1.11.0.jar> --output <plugin.jar> <classes.jar extracted from AAR>
jar --list --file <plugin.jar>
```

`jar --list` 输出只有 `classes.dex`；AAR 内 `classes.jar` 包含 `com/elymbot/android/plugins/tasks/ScheduledTasksPluginEntrypoint.class`。

## 限制与待验

- App 页面与任务查询、确认交互、系统调度入口、主会话忙碌排队、宿主队列幂等、平台快照及实际投递由 W02/W06/W07 接线和验证；本次没有正式 App 到插件的端到端操作。
- 此处只测试 W05 的确定性时间、存储和领域行为。真实 Android 回调/重启、插件签名包安装与正式 DexClassLoader 生命周期仍需宿主组合验收；用户删除时是否清理插件数据由宿主按选择处理。

## 2026-11-23 UI 状态与首次任务引导补充

- 在 Core 增加独立 `ScheduledTasksUiHandle`，不扩展旧 `ScheduledTasksPluginHandle` 或既有 DTO 构造签名。主会话 UI 可订阅带 task/run snapshot、当前待确认动作、`firstTaskCreated`、`guideSkipped` 和 `dataRevision` 的状态；详情订阅可按 task ID 读取运行历史。只接受宿主验证的 APP_UI_MAIN、MAIN_SESSION、同 session/user/安装身份。确认仍调用原 `confirmAction`。
- 状态 Flow 由插件仓库变更修订号和内存待确认修订号驱动。模型动作进入或退出待确认、拒绝、到期时更新流；到期可在没有后续操作时自动清除，关闭或替换插件句柄会发出 `INACTIVE_INSTALLATION` 并结束流。请宿主在活动句柄锁外收集长生命周期 Flow，避免阻止插件停用。
- 创建动作成功时在同一持久事务设置 `firstTaskCreated`，删除最后一项不清除；显式跳过通过 APP_UI_MAIN 写入 `guideSkipped`。仓库格式升为 v2；v1 读取时用现有任务或 CREATED mutation receipt 回退判断首次创建，跳过默认为 false；下次写入原子迁移到 v2。清除插件数据后两项重置。

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.09 A07/A10/A17`（插件领域补充） | `gradlew.bat :plugins:scheduled-tasks:testDebugUnitTest :plugins:scheduled-tasks:assembleDebug --offline` | 总控执行；JDK 17；Gradle Wrapper 9.9.1；AGP 9.5.1；Android SDK 37 | JVM 单元测试 20/20 通过，0 skip；2 个仓库测试、3 个周期计算测试、4 个调度测试及 11 个 handle 测试。新增覆盖状态 Flow 的当前确认/批准/到期/关闭、非 UI caller 拒绝、跳过持久化、创建位在删除后保留及 v1 store 迁移 | `plugins/scheduled-tasks/src/test/kotlin/com/elymbot/android/plugins/tasks/ScheduledTasksHandleTest.kt`；`FileScheduledTaskRepositoryTest.kt` |
| `T02.09` 最终 AAR 编译切片 | 同上；检查 AAR ZIP 项和 `classes.jar` 中的 entrypoint class | JDK 17；Gradle Wrapper 9.9.1；Android SDK 37 | 最终 AAR `scheduled-tasks-debug.aar`，168,388 bytes，SHA-256 `6E51B49DBDAF88ED3D5DA85B6F233FE9BB2B7FA6BDC969781165078923C8AA8D`；包含 `classes.jar`，且包含 `com/elymbot/android/plugins/tasks/ScheduledTasksPluginEntrypoint.class` | `plugins/scheduled-tasks/build.gradle.kts`；`plugins/scheduled-tasks/src/main/kotlin/com/elymbot/android/plugins/tasks/ScheduledTasksPluginHandleImpl.kt` |

首阶段 AAR 的 D8 结果仅适用于当时的 AAR 摘要 `FDE69CC4705F94E3EB65C5150D7D92DD016D09D1E0187B363F50D866D1490B9D`。此次 Core UI capability 和插件实现更新后的最终 AAR 尚待总控重建 DEX fixture；不能沿用首阶段 `plugin.jar` 摘要作为最终产物证据。新版 Core 下旧 DEX fixture 的兼容性也待总控复验。App UI 主会话状态订阅、任务页面、首次引导实际入口与系统调度未在本轮端到端运行。

UTF-8 guard:
- files checked: `docs/work/T02-ToolSystem与节奏感reply+插件与平台接入+FAQ/feedback/W05-主会话定时任务-feedback.md`
- result: passed; UTF-8 decoding, mojibake scan, and Markdown fence balance checks succeeded
- repaired encoding issues: none

## 2026-11-23 签名 DEX 复验

- W08 复查此切片的旧契约二进制兼容、状态流关闭与取消、确认校验和 v1 存储迁移，无待处理项。
- 从提交 `c970a26` 使用 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline` 构建宿主和加入定向复验的测试 APK。基础 APK SHA-256 `EB2BB82813BF722EE8246F9A5B2C9504B0E58B6B3F5E84CEDC0229A3E8E453A5`，测试 APK SHA-256 `72346F6EA632E5F57885854F595CA53EEACCFF06E195D664B74279D2833811A8`。
- 最终 AAR 的 `classes.jar` 用同一 D8 命令重打包，DEX JAR SHA-256 `07FD458EDF00C4DCC360AA18D2990EC31E5C0F1CBC05E91C4B4E34D83B7E5DED`。本地测试发布者签名的制品 SHA-256 `E9F1392B9F72F436D8830C8356114C0277A58A14490E0DB7641ECD9E235E51D2`。此前旧版测试制品保持原样。
- 在 Android 15 x86_64 模拟器用 `am instrument -w -r -e t02ScheduleUiProbe true -e t02ScheduleProbe true` 定向运行两条 [Signed DEX 测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/session/ScheduledTaskHostIntegrationTest.kt)，2/2 通过，无跳过。新版通过宿主验证加载，跳过引导在停用和重新启用后保留。旧版在新版宿主下仍经忙碌主会话排队、去重和 App 回复路径。正式页面、系统后台调度和外部目标投递另行验证。
