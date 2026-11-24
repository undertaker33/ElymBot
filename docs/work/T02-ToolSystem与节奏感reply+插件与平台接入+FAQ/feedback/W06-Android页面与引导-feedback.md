# W06 · Android 页面与引导 Feedback

## 实际结果

- T02.10 现有 App 页面内容区采用原型的 440 ms 横向位移与渐显曲线，起始透明度为 0.35。一级页前进入场偏移 6 dp，详情页前进入场偏移 17 dp，返回偏移 −13 dp。切换时标题和搜索入口直接就位，标签与正文一起过渡。底部导航沿用自身的动画。 [T02 页面原型](../ElymBot_T02.html) · [App 导航](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt) · [一级页共用布局](../../../../app/src/main/kotlin/com/elymbot/android/ui/PrimaryPage.kt)
- 离场页保留组合直至过渡完成，停止放置其界面，以保留清理搜索状态的时机。仍由现有目的地、返回逻辑和页面保存状态控制导航，沿用 Compose 动画时长缩放。设置的既有定向测试增加从「关于」返回后搜索编辑框已关闭的断言。 [设置与关于测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/AboutSettingsTest.kt)

## 验证

| Checklist 项 | 方法 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- |
| T02.10 正式页面与动效 | 静态对照 HTML 的 `.content`、`page-in`、`openRoute`、`popstate` 和原生页的内容层 | 一级页标签及内容、关于页滚动区、聊天消息与输入区、会话列表和空状态均独立于各页头部参加入场。代码差异通过 `git diff --check`。 | [原型](../ElymBot_T02.html) · [App 导航](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt) |

## 限制与待验

- Android UI 定向测试及普通、减少动态效果两种设置下的导航实测待补。最短验收路径为底栏切换一级页，进入设置并搜索「关于」，打开页面后分别用页面和系统返回，观察入场过程中标题与搜索入口的位置，再在关闭系统动画时复验导航、搜索和最终落点。

## 2026-11-23 总控验证

- 独立源码复审确认入场作用于实际内容区，标题、搜索与底栏的位置以及离场页面的动作门禁得以保留。
- `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` 通过，App 与仪器测试 APK 已生成。App debug APK 的 SHA-256 为 `5d525e4d893546e7f770e2589d2d28bda402ceaf4cff38b7f7681ebb50e1b22a`。本轮没有连接 Android 测试设备，未执行 `AboutSettingsTest` 或实测系统减少动画。
- Reply、官方插件及 QQ 平台对接，任务弹层、FAQ 和首次引导仍待对应正式服务与页面，T02.10 和 T02.11 的 Checklist 本轮保持待验。

## 2026-11-23 MuMu 导航与既有功能回归

- MuMu Android 15 / API 35 模拟器，测试 App SHA-256 为 `748486d1a3cf24d35af2c7e043ed80b7c74938f9d8dd48c07d089bfc65fc622b`。初始 AndroidTest APK SHA-256 为 `ae3427931e28e3a3a3357f34a85a686872c0ef5f9a513769b536b573ee4e320d`。
- 通过 `adb shell am instrument -w -e class <classes> com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 定向运行。系统动画缩放 1 下，`AboutSettingsTest`、`PrimaryPageSearchTest`、`AppSessionIntegrationTest`、`AppFailureIntegrationTest` 共 9 项全部通过。系统动画缩放 0 下，`AboutSettingsTest` 1 项通过，验证关闭动画后的导航、搜索清理和两种返回操作。测试后恢复模拟器原始动画缩放。
- 另跑 `ElymBotStorageTest`、`ElymBotUiIntegrationTest`、`AppearanceSettingsTest`、`ComposerAddMenuTest`、`FloatingNavigationDockTest`、`MainActivityIntegrationTest`，共 45 项通过。设备证据覆盖实际导航与既有界面交互，原型过渡逐帧对照待验。

## 2026-11-23 Chat Reply 界面

- W06 在正式 Chat 入口消费会话消息、待发布投递与历史保留回执。消息按服务给出的实际顺序逐条显示，可跟随新气泡。未发布项只有目标与投递状态，生成中使用独立提示行。普通 App 消息送达时直接显示气泡，跨平台及异常状态可按目标查看。 [聊天页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotChat.kt) · [气泡与投递状态](../../../../app/src/main/kotlin/com/elymbot/android/ui/ChatMessages.kt)
- 生成中提供停止。生成结束但仍有待发或发送中目标时，发送旁提供停止投递；输入新消息仍通过同一发送入口。确认失败目标可单独重试，历史保留回执为只读，整轮重试继续使用服务的 `canRetry`。普通和减少动态效果共用 Compose 的动画时长缩放。
- T02.10 A02–A04 源码核对覆盖已发布消息、无正文的待发记录、目标级重试、停止和整轮重试门禁。`git diff --check`、12 个中英文资源键及占位符、Feedback UTF-8、围栏和相对链接检查通过。定向构建与实际 Reply 模型、部分失败、未知回执和停止的仪器测试由总控联调，本轮的源码证据暂不用于勾选 Checklist。

## 2026-11-23 常见问题界面

- 「设置 → 常见问题」可通过设置搜索进入。FAQ 包含原型中的五条问答和四类筛选，搜索覆盖标题、回答与分类，一次展开一条。清除筛选可恢复全部。正文和列表内动效遵循 Compose 时长缩放，折叠过程中已关闭回答的操作立即失效。 [FAQ](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotHelp.kt)
- 「打开模型配置」使用现有模型页，页面及系统返回回到 FAQ 并恢复筛选与阅读状态。FAQ 的首次配置、插件、QQ 和定时任务操作分别等待实际服务状态与可达页面。 [导航](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt)
- T02.11 A16 新增 `FaqSettingsTest`，从 MainActivity 走设置搜索、展开、分类、按答文搜索和模型配置往返。通过 `git diff --check` 静态核对，APK 与 UI 执行结果待总控按正常和关闭系统动画两种设置记录。 [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/FaqSettingsTest.kt)

## 2026-11-23 FAQ 夹具修正与首次配置

- 首次 FAQ 测试在动画缩放 1 和 0 均于设置搜索后中断。Compose 将搜索输入与结果按钮同时识别为带点击操作且同名的语义节点。夹具已排除可编辑节点，与已有关于页测试的选取一致，并增加 FAQ 打开首次配置、点击「稍后」和再次打开的操作。原 FAQ 与 AndroidTest APK 已构建，修正后的两种动画缩放复验待总控。 [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/FaqSettingsTest.kt)
- 首次配置通过实际 `appGuide` 状态决定人格页入口。FAQ 始终可手动重开，引导通过同一动作持久跳过。模型步骤复用正式添加服务、分配文本模型和探针，能力结果对应实际已分配模型；无主会话时可用既有创建或启用人格操作。只有模型能力和主会话都满足才进入真实主会话，首次聊天的实际 Reply 留作独立验收。 [首次配置界面](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotHelp.kt) · [App 入口](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt)
- 现阶段证据为源码核对。等待 W02 完成首次配置状态流和能力探针接线后统一构建，再从干净安装、已有有效配置和跳过后 FAQ 重开三条入口复验。平台、QQ 与任务链接仍等待相应真实页面。

## 2026-11-23 设置分组与实际入口

- 按用户确认的页面分组，将「常见问题」和「关于」放到同一张圆角设置卡，保有各自的页面入口和设置搜索结果。沿用现有分隔线、行高与搜索动效。`FaqSettingsTest` 新增相邻行间距和逐行过滤断言，关于页往返沿用 `AboutSettingsTest`。 [设置页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotSettings.kt) · [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/FaqSettingsTest.kt)
- FAQ 问答和分类由已提供的真实导航动作决定。当前提供首次配置与文本模型切换，文案按真实操作更新。官方插件、QQ 与任务页面有正式入口后，按实际启用或安装状态向 FAQ 提供导航动作。首次配置的验证按已分配模型定位保存的模型服务，适配可无密钥连接的本地服务。
- 源码差异、中英文资源键及文档检查通过。W02 状态流与能力探针已接入，待总控统一构建并定向验证两种系统动画设置。

## 2026-11-23 FAQ 模型入口复核

- FAQ 的「打开模型配置」每次进入「模型」分类，底栏「配置」延续用户上次选择。`FaqSettingsTest` 覆盖从底栏选择「工具」、从 FAQ 进入「模型」、在 FAQ 路径切换到「工具」并返回后重新打开，以及再次进入底栏确认原先分类。源码差异检查通过，待复验。 [分类导航](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt) · [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/FaqSettingsTest.kt)

## 2026-11-23 定时任务界面组件

- 已编写任务列表、详情、编辑与确认弹层。编辑传递实际标题、提示词、起始日期时间、时区、重复规则和结束条件，更新携带已有任务的 revision。聊天增加待确认状态和真正确认操作所需的组件，实际展示依赖主会话获授权的运行时数据。 [任务页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotScheduledTasks.kt) · [编辑表单](../../../../app/src/main/kotlin/com/elymbot/android/ui/ScheduledTaskEditor.kt) · [聊天页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotChat.kt)
- W02、W05 和 W03 继续对齐主会话可用性、待确认列表、首次引导持久状态和快照变更。插件详情按任务 ID 读取运行记录，现有 App 快照接口需透传该参数。待授权状态来源接通后再打开主会话和 FAQ 入口，T02.11 A07、A10、A17 待联调。
- 源码、资源键和 Java 17 时间格式探针已核对。严格 `HH:mm` 解析接受 `08:15`，拒绝 `24:00`。编译和真实插件页面验收待总控。

## 2026-11-23 主会话任务、插件与更新接线

- App 主会话菜单及 FAQ 的任务动作以宿主可用性流为准。任务页按选中的任务 ID 订阅，展示真实计划和运行记录。首次引导使用插件持久状态和跳过动作，聊天通过同一状态流查看待确认操作，批准或拒绝交由宿主复核。新增 `ScheduledTasksAvailabilityTest` 从真实 MainActivity 覆盖主会话按可用性开入口、专属会话隐藏入口。 [导航](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt) · [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/ScheduledTasksAvailabilityTest.kt)
- 插件页读取官方目录和已安装实例，显示兼容性、版本、启停和清理状态。通过用户确认提交目录中的指定制品，管理操作交由宿主处理；FAQ 从「已安装」打开。 [插件页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotPlugins.kt)
- 「关于」提供手动检查更新。启动自动检查由宿主触发，App 观察真实版本状态。确认后下载并交系统安装，按系统返回清理安装票据。安装权限设置与验证失败均有对应状态。 [关于](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotAbout.kt) · [下载与安装](../../../../app/src/main/kotlin/com/elymbot/android/ui/AppUpdatePrompt.kt)
- 当前证据为源码、资源和文档检查。任务数据、官方目录、真实安装或卸载、系统更新和全部相应验收项由总控联编和定向验证。QQ 平台等待 App 服务公开对应状态与操作。

## 2026-11-23 联编与 W08 返工

- App 与 AndroidTest APK 联编通过，对应 SHA-256 分别为 `B6DB5FE203B383190E111F4B90A1E060C35B2B524F2700104BC1960EA79541BA`、`D27B4BCB44EB707EB614BB1A62E0D79567A06945FD90DB5C380EA13B644BE5AA`。MuMu 通过 FAQ、关于、任务入口三项定向测试。该环境未配置官方目录，未覆盖已启用插件的主会话任务入口。
- 按插件实际 `RunNow` 语义，已暂停的任务仍可手动运行。更新下载在当前界面的协程中执行，界面退出或启动器不可用时回收未交给系统的票据，系统已接收的票据由 ActivityResult 处理。 [任务详情](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotScheduledTasks.kt) · [安装界面](../../../../app/src/main/kotlin/com/elymbot/android/ui/AppUpdatePrompt.kt)
- `TaskAndUpdateLifecycleTest` 覆盖暂停任务按钮和合成下载时 Activity 重建。重建测试在旧界面销毁后交回合成票据，验证清理、未走启动成功分支。真实签名更新及插件已启用时主会话正路径仍待相应制品与环境。返工后的定向构建和复验由总控执行。 [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/TaskAndUpdateLifecycleTest.kt)

## 2026-11-23 Activity 重建测试复核

- App 与测试 APK 联编通过。MuMu 中 `pausedTaskCanRunManually` 通过，`activityRecreationCleansAnUnhandedUpdateTicket` 超时。原结果只到协程超时栈，无法从日志判断卡在下载进入还是清理回执。
- 重建夹具使用 Compose 测试等候器驱动调度，分别等待下载进入、旧 composition 释放和未交系统票据回收。同步核对所有权，宿主清理准备过程中取消的文件，界面在收到票据后只负责清理未交给安装器的票据。测试调整后的复验由总控进行。 [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/TaskAndUpdateLifecycleTest.kt)
- 总控在同一 App APK `0DEC37D7CF4A539A70777879813F3BA66267BB0D1AC30D8E8F5074BF6456D116` 上重编测试 APK，并用 MuMu 定向复验 `TaskAndUpdateLifecycleTest#activityRecreationCleansAnUnhandedUpdateTicket`，1/1 通过且无跳过，测试 APK 为 `9972A3D4A582B0EAB5C83DE2D59F88B368529AE083975D08327FDA735CDB4545`。沿用先前测试 APK `F3B8CFD8548C06890878504FDAB603B9E2D9548D2BAED9CAC4B51BCCF28BF3BF` 的 `pausedTaskCanRunManually` 通过结果。两项测试分别覆盖合成下载票据回收及暂停态任务按钮，真实签名更新、系统安装和实际插件的任务端到端仍待验。

## 2026-11-24 QQ 平台与会话

- 底栏「平台」显示已安装 QQ 的真实可用性、账号与连接状态。详情操作由当前 App 主会话授权及插件状态门禁控制，支持启动、停止、刷新登录码、重连、退出和绑定用户指定的私聊。FAQ 在有真实 QQ 详情时提供入口。 [平台页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotPlatforms.kt) · [App 导航](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt)
- 登录采用 QQ 插件本地生成的只读模块矩阵。App 按整数像素绘制并留四格静区，随当前页面、运行状态和挑战更新移除，若提供有效期则到期移除。接入规则编辑使用真实快照和 revision，覆盖准入、唤醒与触发、公开群聊历史、命令别名及限频。 [规则编辑](../../../../app/src/main/kotlin/com/elymbot/android/ui/QqPolicyEditor.kt)
- App 可从专属会话列表进入外部会话并查看已有消息，来自宿主的只读标记关闭输入、会话编辑和 Reply 重试。`QqPlatformUiTest` 覆盖合成登录挑战在连接状态变化后移除，以及外部对话历史可显示且写入口关闭。 [聊天页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotChat.kt) · [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/QqPlatformUiTest.kt)
- 源码差异、66 个 QQ/平台双语资源键、145 个界面资源引用、UTF-8、围栏和 35 个文档链接通过。合成页面测试待总控联编后执行。受支持 ARM64 真机的插件安装、实际二维码扫描、OneBot 私聊与群聊、断线重连及引导恢复仍按 A06–A09、A17 实测记录。

### W08 文案复核

- 群聊开关改为「回复仅 @ 机器人的群消息」。它对应无正文且 @ 当前账号的触发条件，命令与唤醒仍遵循原规则。新增定向测试，点击并保存后核对仅 `replyOnAtOnlyEnabled` 从关闭变为开启，版本与其他设置保持原值。静态差异与双语资源检查通过，测试待总控执行。 [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/QqPlatformUiTest.kt)
- App 联编发现宿主新增的 `ReadOnlyConversation` 尚未映射。界面复用中英「只读会话」资源，此轮由总控待重编。合成页面测试及真实 QQ 验收仍待执行。 [错误文案映射](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotComponents.kt)
## 2026-11-23 MuMu 设置帮助入口复验

- `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` 通过。Android 15 / API 35 MuMu 使用 App SHA-256 `46A8CC4DFBE6485D36E82DDEDE6B364F26468B28FBFC53DEFA2C30ECD96B42EF`、AndroidTest SHA-256 `FCB103B160C478313738E51FCC34C76C46B730EEBE957F5E0582C148F54FE1AA`，覆盖安装后冷启动成功。
- 正常动画下 `FaqSettingsTest` 和 `AboutSettingsTest` 通过。验证 FAQ 和关于两行同卡相邻、搜索可单独筛选、FAQ 只显示可跳转条目、返回保留 FAQ 内容状态、再次打开模型配置仍进入模型分类，以及底栏独立保留分类。覆盖首次配置从 FAQ 打开、跳过和重新打开。
- 关闭动画后 `FaqSettingsTest` 同一用例再次通过。测试后模拟器动画缩放恢复为原值。插件、平台、任务页面及相关 FAQ 入口继续随正式能力接入验收。

## 2026-11-24 平台页面联编

- App 与 AndroidTest 联编通过，已包含 [QQ 合成页面用例](../../../../app/src/androidTest/kotlin/com/elymbot/android/QqPlatformUiTest.kt)。App APK SHA-256 `1C3D53993B2C631F6CF238A3571AC78F3F5F077D227A8B864909B201D2F43726`，AndroidTest APK SHA-256 `F3EC14756F2DBA91DE5A8685683E27EE16720FC36A251D78FA1BA58F7C62E045`。
- MuMu 的 FAQ、关于、主会话任务入口三项回归通过。QQ 专项页面用例已编译，扫码、连接、规则和外部会话视图的设备验收仍待执行。

## 2026-11-24 QQ 命令权限开关

- 「平台 → QQ → 接入规则」增加「仅管理员可用插件命令」和「发送拒绝提示」。两个开关在当前插件提供设置能力时显示，动作携带当前安装标识及命令设置版本。界面以插件快照为准，发生冲突时显示反馈，并从更新后的状态继续操作。 [平台页](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotPlatforms.kt)
- A08、T02.10 新增合成用例，覆盖旧能力缺失时隐藏、支持能力时显示、冲突后读取最新设置以及分别保存两项开关，验证命令设置保存沿用自己的版本并保留现有接入规则。两个界面字符串已完成中英文 XML 和源码引用静态核对。宿主联接、联编及用例执行待总控验证。 [定向测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/QqPlatformUiTest.kt)

- 总控接入宿主的命令设置状态流和写入入口。写入限 App 主会话，通过当前安装身份查找可选插件能力，并以独立 revision 更新。App 与 AndroidTest 联编通过，SHA-256 分别为 `13C8E6715644BC3F58A52303FBD1EE301283C3CFC6E02F1D2B44D8EEE7D2B49C` 和 `BF6ABD450788A066FBF8F5001CCD3AA978A18158093AEB7344CE68C0A61CED35`。QQ 专项 UI 用例已编入测试 APK，设备执行待验。[宿主接线](../../../../app/src/main/kotlin/com/elymbot/android/session/ElymBotSessionService.kt)
