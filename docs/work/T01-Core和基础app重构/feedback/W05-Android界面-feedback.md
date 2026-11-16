# W05 Android 界面 Feedback

## T01.07 原型资源与默认人格交接

标题字体、许可和封面图集提取自 [ElymBot 高保真原型 v0.6.3](../ElymBot_高保真原型.html)。

- `app/src/main/res/font/gallery_song.otf` 为 Noto Serif CJK SC Bold 的 19 字子集。由原型内嵌 WOFF 转为 OTF，核对 CFF、字体名称表、字形顺序和字符映射与原型一致。`app/src/main/res/raw/gallery_song_license.txt` 保留原型内嵌的 SIL Open Font License 1.1 和版权声明。
- `app/src/main/res/drawable-nodpi/persona_cover_atlas.webp` 是原型内嵌的 1050 × 1050 WebP，已核对字节一致并解码验证。图集每行三张，每格 350 × 350 像素。列索引为素材索引除以三的余数，行索引为素材索引除以三的整数商。
- 首张通栏卡片宽高比为 1.79，普通卡片为 1.06。各素材单格按容器长边等比放大并裁切，普通卡片和头像居中，首张通栏图片中心位于卡片高度的 61%。筛选后第一张可见卡片使用通栏版式。

### 默认人格种子

保持下表顺序。首次初始化激活夜璃，所有人格提示词为空，主会话从空历史开始。

| 素材索引 | 原型 ID | 名称 | 简述 | 标签 | 常用 |
| --- | --- | --- | --- | --- | --- |
| 0 | `p0` | 夜璃 | 温柔、深邃、善于倾听 | 陪伴 | 是 |
| 1 | `p1` | 澪 | 理性、冷静、清晰 | 学习 | 是 |
| 2 | `p2` | 星野 | 好奇、探索、博学 | 学习 | 否 |
| 3 | `p3` | 白露 | 清雅、细腻、治愈 | 陪伴 | 是 |
| 4 | `p4` | 苍海 | 沉稳、包容、可靠 | 陪伴 | 否 |
| 5 | `p5` | 枫窗 | 专注、耐心、有条理 | 学习 | 否 |
| 6 | `p6` | 岚川 | 敏锐、开阔、自由 | 创作 | 否 |
| 7 | `p7` | 花间 | 轻盈、浪漫、灵动 | 创作 | 否 |
| 8 | `p8` | 墨语 | 细读、推敲、落笔 | 创作 | 否 |

素材验证使用本机 Python、FontTools 4.62.1 和 Pillow 11.2.1。Android 构建、渲染和种子持久化留待正式接入后验证，T01.07 验收项保持待验证。

## T01.07 Compose 界面交接

- `ElymBotApp(service: ElymBotUiService)` 是 W01 的挂载入口。W05 界面经 ViewModel 调用 App 服务，以生命周期感知方式收集人格、会话、模型和执行状态。
- 按 v0.6.3 实现人格库通栏和双列封面、搜索和标签筛选、底部导航、人格表单、主会话和专属会话、聊天菜单和输入栏、模型服务和模型用途、模型目录与探测弹窗。图标路径取自原型。对话、指令回执和工具反馈使用纯文本气泡。
- 按任务书补入指令名称设置、封面和头像各自的位置预览及编辑、当前人格内批量选择删除、会话生成状态和新消息提示。会话页使用自己的发送和停止入口，离页后的执行与未读语义由 App 会话服务提供。模型探测显示可用性、视觉支持和原生流式三项。
- 本机 Windows 使用仓库 Gradle Wrapper、Android SDK Platform 37 执行 `./gradlew.bat :app:compileDebugKotlin --console=plain`。首次编译时新增的两个字符串赶在资源任务完成后才写入，Kotlin 当次未找到对应 R 项。资源生成后复跑通过。加入键盘发送、页面状态和发送防重复后的编译再次通过。
- 静态检查核对 162 个 Android 资源引用均可解析，原型图标 XML 均可解析。通过 `git diff --check`，Git 仅提示并行工作所改构建文件的行尾转换。
- ADB 列出在线 MEIZU 20 Pro。该轮未安装或操作设备。正式入口与会话服务装配后，再在设备上对照原型并验证附图、键盘、独立裁切保存、批量删除、并行回复和未读清除。Checklist 的两个 T01.07 条目保留待核验。

### 视觉路由提示与探测结果交接

- W04 将 Core 的 `VISION_MODEL_MISSING` 映射为 `UiIssue.VisionModelMissing`，将 `VISION_CAPABILITY_UNKNOWN` 映射为 `UiIssue.VisionCapabilityUnknown`。聊天分别提示配置视觉理解模型或探测文本对话模型的视觉支持，并提供模型配置入口。
- W04 分别将 `AVAILABILITY`、`VISION`、`NATIVE_STREAMING` 映射到 `ProbeTarget.Availability`、`ProbeTarget.Vision`、`ProbeTarget.Streaming`。三项能力状态分别写入 `ModelProbe` 的状态字段，三项失败详情按目标放入 `failuresByTarget`。结构化工具调用探测供 Core 使用。
- `ProbeFailure` 包含本地化错误类别与可选 HTTP 状态码。W04 将 `NETWORK`、`AUTHENTICATION`、`HTTP`、`API_REJECTED`、`PROTOCOL`、`TIMED_OUT` 依次映射为 `Connection`、`Authentication`、`ServiceResponse`、`ServiceRejected`、`ServiceProtocol`、`TimedOut`，并保留适用的 HTTP 状态码。`ModelProbe.issue` 用于整个操作的错误。弹窗在每个目标的同一卡片内显示其失败详情与能力状态。
- 使用同一 Windows SDK 运行 `./gradlew.bat :app:compileDebugKotlin --console=plain`，Core Kotlin、App 资源和 App Kotlin 均重新执行，构建成功。探测端点及真机弹窗仍待服务接入后验证。

### 聊天工具进度

- 聊天读取当前会话的 `Conversation.toolProgressActive`。工具运行时在 v0.6.3 的等待气泡位置使用同一助手文本气泡显示「正在调用工具…」。已收到的回复保留可见，工具运行期间只显示一个临时状态气泡。工具阶段结束后按当前回复状态恢复等待气泡或移除临时气泡。
- 状态文本来自 App 字符串资源，临时气泡由 Compose 生成。静态核对了当前会话状态、原型等待气泡、字符串引用和 XML。Android 最新构建由 W04 串行复验，T01.07 的设备操作验收仍待执行。

### 返工与交接（2026-11-14）

- 聊天发送遇到 `MainModelMissing` 时，在现有弹窗样式中提供「模型配置」与「关闭」。配置操作进入聊天原有的模型配置页面，返回后可继续编辑并发送原文字及图片草稿。其他提交错误继续由全局提示处理。
- 增加「图片会话」字符串资源 `elym_session_image_title`，已与 W04 确认由首条消息为纯图片时的专属会话标题逻辑引用。
- 完成 UTF-8 与资源 XML 检查。构建和设备验证交总控串行执行。T01.07 复验时从未配置主模型的聊天发送图文草稿，打开模型配置并返回，核对文字和图片均仍可发送。

### 最终 APK 人格封面与头像

- 删除 App 主资源中的示例封面图集。新建人格使用默认空表单。未选图片时，封面和聊天头像使用现有暖色与人格图标组成的占位。选图后展示自定义图片，可分别预览和调节封面、头像位置。
- 去除 UI 契约中的图集索引。W04 同步调整持久层映射及仪器测试图片来源。原型 HTML 和标题字体仍供原有界面与设计对照使用。
- 源码与主资源扫描确认 UI 已无图集引用，主资源目录已移除图集文件。正式 APK 内容及空库创建、选图、裁切行为由总控在最终版本复验。

### T01.07 正式入口定向测试（2026-11-14）

- 新增 `ElymBotUiIntegrationTest`，复用 W01 的 `AndroidTestAppFixtureRule` 启动正式 MainActivity。测试人格、会话、测试图片由用例持有并清理，指令名恢复至测试前保存的配置。
- 设置及会话用例从设置页改名帮助指令，从主会话进入模型服务编辑和文本模型用途后返回聊天，再从所属人格的列表打开标记未读的专属会话。用 Compose IME 发送改名后的指令，检查纯文本帮助回执，返回核对徽标移除和 Room 未读清零，然后选择两条会话一次删除。
- 人格编辑用例使用临时 PNG，操作封面、头像各自的水平滑块并点保存，核对各自的位置值及独立的垂直默认值。图片通过测试夹具装配，系统选图器仍需补验。
- 原型源码中的聊天菜单、人像切换、专属列表和聊天返回路径与当前界面入口已静态核对。该轮生产界面沿用 `27bdf4f` 的实现。
- Gradle Wrapper 9.9.1、Android SDK Platform 37 下，`:app:compileDebugAndroidTestKotlin` 和 `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。使用对应 debug App 和 androidTest APK，在 MEIZU 20 Pro（Android 16，`391QYGCM225VG`）执行 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (2 tests)`，耗时 12.355 秒。
- T01.07 验收 1–6 已取得正式界面进入人格聊天、模型服务编辑和文本用途、帮助指令改名与纯文本回执、IME 发送的证据。主会话实际切人格、两个人格的专属会话归属与搜索/筛选、空库创建、系统选图后的图片附件、模型服务录入保存与目录/三项能力探测、工具反馈的实际界面呈现仍需复验，保留未勾选。
- T01.07 验收 9–11 已取得正式界面调整并写入独立封面和头像位置、两条专属会话批删、打开后清除对应未读的真机证据。本轮从合成未读状态验证清除，离页生成未读和双会话并行见 W01 Feedback。W04 磁盘重启用例证明位置字段持久化，本轮未做保存后重启的视觉对照，也未核对名下会话阻止删除人格、未选会话与其他人格会话仍可打开、编辑提示词后进入实际请求的完整流程，保留未勾选。

### 人格切换与会话归属复验（2026-11-14）

- 在设置页改名人格切换指令，从原人格主会话尝试删除人格，看到名下会话拦截并进入该人格的列表。打开其专属会话，经键盘发送改名后的切换指令，验证收到纯文本无效指令回执，Room 中专属会话的人格归属不变。返回主会话发送同一指令，验证切到第二人格且主会话 ID 不变。第二人格列表显示其名下会话，两个列表均未显示另一人格的会话。
- 使用成功构建并安装的 debug App 和 androidTest APK，在 MEIZU 20 Pro（Android 16，`391QYGCM225VG`）运行 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest#renamedPersonaCommandSwitchesMainAndDedicatedSessionsRetainTheirOwner' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (1 test)`，耗时 8.123 秒。前两条测试源码保留，使用上一轮 2/2 真机证据。
- 本轮补齐上一轮的人格切换、双人格专属会话归属、名下会话阻止删除人格。人格搜索与标签筛选、空库首次创建、系统选图及发送附件、屏幕与原型的视觉对照、重启后裁切视觉核对，以及批删后打开未选及其他人格会话仍待验证。模型端点和工具反馈由 W01 的独立正式入口验证记录承接。T01.07 两条复合验收维持待核验。

### 检查点弹层返工

- 原型 v0.6.3 的 `openCheckpoints` 从聊天菜单打开检查点弹层，顶部保存按钮，下方从新到旧列出检查点及保留消息数。原 Compose 菜单仅执行检查点列表指令。现接入同一弹层和列表组件，按当前会话读取结构化检查点，包括固定空点、当前标记和保留消息数。保存保持弹层，成功回退后关闭并显示聊天回执；当前会话生成时禁用入口及操作。已有 Slash Command 保持纯文本。
- 菜单保存、回退经类型化服务调用现有完整轮次建点与删尾逻辑。新增 `checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint` 从正式入口打开专属会话，核对初始空点、保存后检查点和消息计数、回退后聊天回执、Room 轮次删除以及重新打开时的当前标记。测试输入为隔离专属会话的合成已完成轮次。
- MEIZU 20 Pro（Android 16，`391QYGCM225VG`）使用同批 debug App 和 androidTest APK，执行 `adb -s 391QYGCM225VG shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest#checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (1 test)`，耗时 5.412 秒。

### 聊天软键盘打开闪动

- 主 Activity 的窗口配置显式启用 edge-to-edge 与 `adjustResize`，Compose 根布局通过 `safeDrawingPadding()` 同时接收系统栏与键盘 Insets。去除根布局额外声明的 `imePadding()`。Compose 会消费已处理的 Insets，源码核对无法确认重复声明导致了闪动。聊天输入区布局与底部弹层自己的键盘处理未变。
- 在 MEIZU 20 Pro（Android 16，`391QYGCM225VG`）的同批 debug APK 上，从主会话点击输入栏打开实体软键盘，按返回收起，共两轮。以 `adb shell screenrecord --size 720x1600 --time-limit 8` 录制并逐帧查看两次展开和收起的画面。输入栏随键盘移动且全程可见，未观察到闪白、键盘重现或输入栏跳回。没有在设备重装旧 APK 对照，因此根因仍未确认。 [Android 窗口配置](https://developer.android.com/develop/ui/compose/system/setup-e2e) · [Compose Insets 消费](https://developer.android.com/develop/ui/compose/system/insets-ui)

### T01.07 验收 9–11 证据复用

- 复用 [正式入口定向测试](#t0107-正式入口定向测试2026-11-14) 的真机 2/2 结果。人格编辑页分别显示封面和头像预览，测试操作各自的水平滑块并核对保存后的独立位置。专属会话列表一次删除两条所选会话，打开带新消息标记的会话并返回后，界面徽标消失，Room 未读状态清零。
- 复用 [W01 正式入口与双会话合成复验](W01-总控与集成-feedback.md#android-正式入口与双会话合成复验)，确认回复在离开会话后完成时显示对应会话的新消息徽标。与 W05 打开后清除徽标的结果共同覆盖未读提示。勾选 [Checklist](../T01-Core和基础app重构-checklist.md#t0107-原型界面资源与-viewmodel) 的验收 9–11。验收 1–6 仍待正式聊天中助手工具反馈的纯文本证据。

### T01.07 正式聊天工具反馈与人格筛选补证

- `AppFailureIntegrationTest#rejectedHistoryReadShowsAssistantTextAndContinuesWithCompleteJsonReply` 复用正式 MainActivity 和本地合成端点。首个 JSON 响应发出缺少 `toTurn` 的 HistoryRead 调用。测试断言后续请求按同一调用 ID 回填完整拒绝文本，聊天显示助手纯文本回执，放行最终 JSON 后显示完整回复，并核对两条助手文本落盘。最终响应的闸门延长至 30 秒，超过 Compose 观察窗口，测试退出与端点关闭时均释放闸门。
- `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 从正式人格库创建带标签和提示词的人格，按名称搜索并验证匹配与不匹配标签。根据新建前后的 ID 差异识别用例创建的人格，只按实际新增的 ID 清理。
- 最终 AndroidTest 构建通过。MEIZU 20 Pro（Android 16）拒绝安装更新的测试 APK。两个用例均未运行，其现有 MainActivity 夹具连接目标 App 的持久化 `elymbot.db` 并涉及写入主会话或模型设置，后续在独立测试环境执行。T01.07 的验收 1–6 仍待取得正式聊天助手文本证据。

#### MuMu 独立环境复验

- 在 MuMu（Android 15，`127.0.0.1:16416`）使用合批 `:app:assembleDebugAndroidTest` 生成的测试 APK。`AppFailureIntegrationTest#rejectedHistoryReadShowsAssistantTextAndContinuesWithCompleteJsonReply` 首轮通过。正式聊天显示助手纯文本拒绝回执，合成端点收到带原调用 ID 的拒绝结果，端点放行最终 JSON 前界面未出现最终回复，放行后显示完整回复并核对两条助手文本落盘。
- `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 首轮因搜索输入框和人格卡片同时匹配而失败。按可点击且不具备文本编辑动作收紧卡片选择器，修复后定向复验通过，覆盖正式人格创建、名称搜索、标签过滤及组合过滤。
- T01.07 验收 1–6 继续待核验。人工最短路径使用文本对话模型已探测为不支持视觉、视觉理解模型为「未选择」的测试配置。进入聊天点「添加图片」，从系统选图器选一张测试图，确认「图片附件」及缩略图后发送，核对「请配置可用的视觉理解模型」与「模型配置」入口。该路径同时验证选图结果回填和缺少可用视觉模型时的提示。

#### MuMu 停止生成、空库首建与系统选图

- MuMu Android 15（`127.0.0.1:16416`）使用已有的 debug App 和 androidTest APK。运行 `AppFailureIntegrationTest#stopKeepsVisibleFirstDeltaAndDiscardsReleasedTail` 得到 `OK (1 test)`，耗时 2.795 秒。首段在聊天可见后点击停止，界面显示已停止，随后允许合成端点尝试继续发送，确认聊天和 Room 仅保留首段且轮次为 `STOPPED`。
- 在独立 MuMu 上清除测试 App 数据后打开，确认初始人格库为空。通过「创建人格」输入测试名与提示词，使用系统选图器选择自建双色 PNG 作为封面。保存后人格显示「已激活」，点击卡片进入该人格的空主会话。
- 在主会话再次打开系统选图器，选取同一张测试图。返回后正式聊天显示「图片附件」、缩略图和「移除图片」。随后点发送时，此空库未配置文本模型，App 显示文本模型配置提示。
### 人格标签按压与聊天等待动画

- 人格标签保留 48 dp 的触区，按压反馈使用同一交互源绘制在裁剪后的 32 dp 胶囊内。水波颜色在选择状态切换时保持稳定，避免替换动画节点。底部导航本身已限定按压形状。
- 聊天等待三点使用原型 v0.6.3 的透明度脉冲，单向 1.2 秒，逐点延迟 150 毫秒。延用原等待状态的出现条件、尺寸、间距和无障碍标记。动效使用 Compose 动画时钟，遵循系统动画时长设置。 [Compose 动画](https://developer.android.com/develop/ui/compose/animation/value-based)
- `:app:assembleDebug :app:assembleDebugAndroidTest` 构建通过。MuMu Android 15（`127.0.0.1:16416`）运行人格创建及筛选、停止生成两个定向用例，共 2/2 通过。水波颜色稳定化后重新构建并安装 App，录制「学习」「全部」短按与长按，逐帧检查按压色块限制在胶囊内，标签切换生效。等待三点的节奏由原型和实现静态核对，设备上的 SSE 用例通过，仪器测试录屏未捕获足够的等待帧来独立核对脉冲节奏。

### 页面导航过渡与返回

- 根页使用 220 ms 交叉淡化，下钻与返回使用 300 ms 横移。上层进出移动整页宽度，下层移动 30% 页宽并在盖住时加最多 10% 遮罩。底栏沿自身高度滑入滑出，同时淡化。人格筛选的填色、描边和文字颜色使用 150 ms 过渡，胶囊内水波纹继续可用。转场按原页面渐变绘制。
- 当前目的地作为一个可保存快照，聊天仍按会话 ID 保存输入草稿。导航切换清除输入焦点，退场页面隐藏操作弹层并停止处理返回，提交系统返回与顶栏返回共用目的地逻辑。ModalBottomSheet 继续由 Material3 管理。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 成功。MuMu Android 15（`127.0.0.1:16416`）首轮执行 `ElymBotUiIntegrationTest,AppSessionIntegrationTest` 共 6/6 通过。调整渐变、焦点与返回遮罩后，两包构建成功，更新 App 后定向复跑设置与会话导航测试和双会话并行测试，结果 2/2。人工在聊天输入测试草稿，进入模型配置后按系统返回，确认草稿仍在聊天输入栏。快速按两次返回从会话列表回到人格库。

### 头像和封面直接裁剪

- 人格表单中有图片时可点封面或头像预览，打开各自固定裁剪框并单指拖图。封面编辑框沿用 1.79 比例，头像为正方形。人格库通栏与双列卡片继续分别使用 1.79 和 1.06，同一归一化封面位置按各自显示比例计算。现有 `ImagePosition` 和持久化格式沿用。
- 裁剪绘制与拖动共用铺满裁剪框后的实际像素溢出，拖动只改变有溢出的轴并限制到图片边缘。选择「完成」后位置进入表单草稿，取消或系统返回均丢弃本次调整。裁剪框提供对应可移动轴的无障碍移动动作，替代原滑条。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 成功。MuMu Android 15（`127.0.0.1:16416`）更新两包后执行 `ElymBotUiIntegrationTest#personaEditorSavesIndependentCoverAndAvatarPositions`，结果 `OK (1 test)`，耗时 7.568 秒。用宽图核对水平无障碍动作、拖动后取消与系统返回、完成后分别保存两种裁剪位置，以及垂直位置保持原值。未在实体手机上安装本轮 APK。 [Compose 拖动检测](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-composables)

### 检查点回退确认

- 从聊天检查点列表点选检查点时显示确认弹窗。标题显示编号，提示永久删除且说明保留范围，固定空点说明会清空当前会话。取消和系统返回只关闭弹窗，确认才交给原有回退流程。服务在既有事务内核对编号与稳定 ID；编号复用时保留新检查点及其历史。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。在独立 MuMu Android 15（`127.0.0.1:16416`）运行 `ElymBotUiIntegrationTest#checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint` 与 `ElymBotStorageTest#typedCheckpointApiListsRetainedHistoryRestoresAndBlocksMutationsWhileGenerating`，结果 `OK (2 tests)`，耗时 6.437 秒。覆盖普通点与空点文案、取消和返回保留轮次、确认产生一条回执并删尾、过期 ID 与生成中回退拒绝。

### 页面切换与编辑开销优化

- 页面切换时遮罩由独立图层处理透明度，固定大小的整页转场关闭尺寸插值。会话列表按会话 ID 使用 LazyColumn，保留圆角、单分隔线和多选操作。编辑人格时封面、头像与裁剪共用该表单内同一份已解码图片，更换图片时立即丢弃旧位图引用。
- 基线 `9dd7db5` 与本轮候选均在独立 MuMu Android 15（`127.0.0.1:16416`）取样。每条入口热身一次后进行三次进入，使用 `dumpsys gfxinfo ... framestats`，按 IntendedVsync 取每次新增的有效帧，耗时用 FrameCompleted − IntendedVsync，慢帧以 16.67 ms 为比较阈值。编辑路径使用测试人格已有的本地封面。

| 路径 | 基线帧数 / P95 / 慢帧 | 候选帧数 / P95 / 慢帧 |
| --- | --- | --- |
| 进入聊天 | 32 / 54.29 ms / 15 | 30 / 59.49 ms / 6 |
| 打开会话 | 38 / 34.32 ms / 17 | 40 / 34.27 ms / 36 |
| 打开编辑人格 | 57 / 52.08 ms / 3 | 57 / 14.49 ms / 2 |

- 模拟器小样本结果方向不一致。会话路径样本为空或少量会话，未测出长列表表现，也不能据此认定用户手机上的掉帧原因或改善幅度。
- 会话列表以候选、基线顺序重复同一点击路线，组内先热身一次再取三次。FrameCompleted 晚于 FrameDeadline 的帧数为候选 3/39、基线 3/38，原先顺序为基线 4/38、候选 3/40。反序的 16.67 ms 阈值计数为候选 5/39、基线 15/38，P95 为 42.38 ms、39.00 ms。首轮绝对阈值的慢帧增量未复现。复测后模拟器恢复候选 App。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。候选 APK 在同一 MuMu 复跑 `personaEditorSavesIndependentCoverAndAvatarPositions`、`settingsAndModelNavigationLeadBackToChatThenOpeningClearsUnreadAndAllowsBatchDelete` 和 `AppSessionIntegrationTest`，结果 3/3，耗时 10 秒。

### 保存模型服务后的 API Key

- `ElymBotUiIntegrationTest#savingAnUnchangedModelServiceKeepsItsApiKey` 从正式聊天菜单打开模型配置，新增带合成 Key 的服务，重开表单确认「已填入 · 留空沿用」，保持地址与字段不变直接保存。用例只检查凭据记录是否存在，并重开表单核对提示。
- `:app:assembleDebugAndroidTest` 通过。在独立 MuMu Android 15（`127.0.0.1:16416`）安装基线 `4fba0e0` 的 debug APK 和测试 APK，运行该用例。首次保存成功，重开出现已填入提示，再次保存后凭据记录存在性断言失败。生成的 Room SQL 对 `model_services` 执行 `INSERT OR REPLACE`，删除旧服务行时触发 `service_credentials` 的外键级联删除。编辑页把空 Key 交给保存入口，保存逻辑跳过写入新凭据。
- Room 改用更新现有服务行后，`:app:assembleDebug :app:assembleDebugAndroidTest` 通过。候选 debug APK（SHA-256 前缀 `DC73D9DE794F`）在同一 MuMu 上运行上述用例，以及 `ElymBotStorageTest#savingModelServicePreservesAndInvalidatesProbeCacheWithCredentialIdentity` 和 `#modelServiceUrlAndCredentialUpdatesStayAtomicAcrossSnapshotsAndDrafts`，得到 `OK (3 tests)`，耗时 3.657 秒。再次保存后凭据记录仍在，重开仍显示原提示，覆盖 T01.07 模型配置的该反馈路径。仅使用合成凭据和隔离模拟器。
- 同地址再次输入同一 Key 时沿用原密文。调整后重建两包，在新版 debug APK（SHA-256 前缀 `F04499BDB9AA`）上复跑同一组用例，结果 `OK (3 tests)`，耗时 3.593 秒。

### 2026-11-16 聊天添加菜单、人格标签与系统深色

- 聊天输入框左侧的添加菜单合并图片入口与当前指令名。用例从正式专属会话选改名后的帮助指令、附图、切换至检查点指令，核对已有参数和附件保留。对比会话服务返回的消息与 Room 轮次，指令选择期间无新增回执或模型轮次。选图结果由仪器测试模拟返回。
- 人格库保留「全部」「常用」入口，新增标签后可选人格并保存，筛选与搜索使用保存后的标签。人格编辑取消了标签合计 60 字截断，避免继续编辑时裁掉持久化标签。底部导航选中态只保留图标胶囊，整项按压使用同一区域绘制反馈。
- 系统深色使用原型 v0.6.3 第 35–38 行的暖色页面、表面、轮廓、强调与成功色。补充危险操作前景、弹层遮罩和缺省封面底色。已有图片继续使用固定遮罩与图上前景色。静态计算深色正文与底色的对比度为 14.55:1，次级文字与表面为 7.90:1，成功徽标为 7.62:1，危险按钮为 7.53:1。 [高保真原型](../ElymBot_高保真原型.html)
- 以 `bf4136e` 加本轮工作区改动运行 `:app:assembleDebug :app:assembleDebugAndroidTest`，两包构建通过。debug APK 与测试 APK 的 SHA-256 前缀分别为 `8BE05EDF746A`、`8CB0CC709D0B`。在 MuMu `ElymBot-T01-Clean`（index 1，Android 15/API 35，`127.0.0.1:16416`）安装两包，使用 `adb -s 127.0.0.1:16416 shell am instrument -w -e class 'com.elymbot.android.ComposerAddMenuTest,com.elymbot.android.ElymBotUiIntegrationTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (7 tests)`，耗时 21.2 秒。覆盖 T01.07 的人格新建与标签、聊天输入与添图，并复验导航、模型服务保存、专属会话、检查点和图片位置。
- 在同一模拟器用 `cmd uimode night yes` 开启系统深色，实际打开人格、聊天添加菜单、配置与添加模型服务表单。截图核对页面和弹层层次、输入框及文字、图片区可读性和状态栏图标。从人格切至配置后只见配置图标带胶囊，静态截图未覆盖按下瞬间。截图与仪器测试输出保存于本地 `C:\Users\93445\Documents\Codex\ElymBot-W05-2026-11-16`。结束后恢复原系统浅色与自由旋转设置，另保存浅色人格页截图。本轮未在实体手机安装或观察深色效果。

### 2026-11-16 人格标签栏加号尺寸与收藏用词

- 标签栏最右侧加号与普通标签共用胶囊边框、32 dp 高度、最小宽度和水平内边距，点击区域仍为 48 dp 高。横向滑动可到达加号，空库时加号禁用。内置筛选显示「全部」「收藏」，人格编辑显示「加入收藏」。收藏筛选仍读取已有收藏标记。
- 在 `b312068` 基线上运行 `:app:assembleDebug :app:assembleDebugAndroidTest` 通过，debug APK 的 SHA-256 前缀为 `54224115AC40`。在 MuMu `ElymBot-T01-Clean`（index 1，Android 15/API 35，`127.0.0.1:16416`）运行 `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag`，结果 `OK (1 test)`，耗时 3.983 秒。正式人格页同屏截图显示「全部」「收藏」和加号三枚胶囊等宽等高，截图与仪器输出分别为本地 `C:\Users\93445\Documents\Codex\ElymBot-W05-2026-11-16\gallery-tag-chips.png` 和同目录的 `gallery-tag-chips-instrumentation.txt`。
