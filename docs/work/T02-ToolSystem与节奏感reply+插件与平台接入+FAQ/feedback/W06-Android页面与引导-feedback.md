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

## 2026-11-23 MuMu 设置帮助入口复验

- `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` 通过。Android 15 / API 35 MuMu 使用 App SHA-256 `46A8CC4DFBE6485D36E82DDEDE6B364F26468B28FBFC53DEFA2C30ECD96B42EF`、AndroidTest SHA-256 `FCB103B160C478313738E51FCC34C76C46B730EEBE957F5E0582C148F54FE1AA`，覆盖安装后冷启动成功。
- 正常动画下 `FaqSettingsTest` 和 `AboutSettingsTest` 通过。验证 FAQ 和关于两行同卡相邻、搜索可单独筛选、FAQ 只显示可跳转条目、返回保留 FAQ 内容状态、再次打开模型配置仍进入模型分类，以及底栏独立保留分类。覆盖首次配置从 FAQ 打开、跳过和重新打开。
- 关闭动画后 `FaqSettingsTest` 同一用例再次通过。测试后模拟器动画缩放恢复为原值。插件、平台、任务页面及相关 FAQ 入口继续随正式能力接入验收。
