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

素材验证使用 Python、FontTools 4.62.1 和 Pillow 11.2.1。Android 构建、渲染和种子持久化留待正式接入后验证，T01.07 验收项保持待验证。

## T01.07 Compose 界面交接

- `ElymBotApp(service: ElymBotUiService)` 是 W01 的挂载入口。W05 界面经 ViewModel 调用 App 服务，以生命周期感知方式收集人格、会话、模型和执行状态。
- 按 v0.6.3 实现人格库通栏和双列封面、搜索和标签筛选、底部导航、人格表单、主会话和专属会话、聊天菜单和输入栏、模型服务和模型用途、模型目录与探测弹窗。图标路径取自原型。对话、指令回执和工具反馈使用纯文本气泡。
- 按任务书补入指令名称设置、封面和头像各自的位置预览及编辑、当前人格内批量选择删除、会话生成状态和新消息提示。会话页使用自己的发送和停止入口，离页后的执行与未读语义由 App 会话服务提供。模型探测显示可用性、视觉支持和原生流式三项。
- Windows 环境使用仓库 Gradle Wrapper、Android SDK Platform 37 执行 `./gradlew.bat :app:compileDebugKotlin --console=plain`。首次编译时新增的两个字符串赶在资源任务完成后才写入，Kotlin 当次未找到对应 R 项。资源生成后复跑通过。加入键盘发送、页面状态和发送防重复后的编译再次通过。
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
- Gradle Wrapper 9.9.1、Android SDK Platform 37 下，`:app:compileDebugAndroidTestKotlin` 和 `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。使用对应 debug App 和 androidTest APK，在 MEIZU 20 Pro（Android 16）执行 `adb shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (2 tests)`，耗时 12.355 秒。
- T01.07 验收 1–6 已取得正式界面进入人格聊天、模型服务编辑和文本用途、帮助指令改名与纯文本回执、IME 发送的证据。主会话实际切人格、两个人格的专属会话归属与搜索/筛选、空库创建、系统选图后的图片附件、模型服务录入保存与目录/三项能力探测、工具反馈的实际界面呈现仍需复验，保留未勾选。
- T01.07 验收 9–11 已取得正式界面调整并写入独立封面和头像位置、两条专属会话批删、打开后清除对应未读的真机证据。本轮从合成未读状态验证清除，离页生成未读和双会话并行见 W01 Feedback。W04 磁盘重启用例证明位置字段持久化，本轮未做保存后重启的视觉对照，也未核对名下会话阻止删除人格、未选会话与其他人格会话仍可打开、编辑提示词后进入实际请求的完整流程，保留未勾选。

### 人格切换与会话归属复验（2026-11-14）

- 在设置页改名人格切换指令，从原人格主会话尝试删除人格，看到名下会话拦截并进入该人格的列表。打开其专属会话，经键盘发送改名后的切换指令，验证收到纯文本无效指令回执，Room 中专属会话的人格归属不变。返回主会话发送同一指令，验证切到第二人格且主会话 ID 不变。第二人格列表显示其名下会话，两个列表均未显示另一人格的会话。
- 使用成功构建并安装的 debug App 和 androidTest APK，在 MEIZU 20 Pro（Android 16）运行 `adb shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest#renamedPersonaCommandSwitchesMainAndDedicatedSessionsRetainTheirOwner' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (1 test)`，耗时 8.123 秒。前两条测试源码保留，使用上一轮 2/2 真机证据。
- 本轮补齐上一轮的人格切换、双人格专属会话归属、名下会话阻止删除人格。人格搜索与标签筛选、空库首次创建、系统选图及发送附件、屏幕与原型的视觉对照、重启后裁切视觉核对，以及批删后打开未选及其他人格会话仍待验证。模型端点和工具反馈由 W01 的独立正式入口验证记录承接。T01.07 两条复合验收维持待核验。

### 检查点弹层返工

- 原型 v0.6.3 的 `openCheckpoints` 从聊天菜单打开检查点弹层，顶部保存按钮，下方从新到旧列出检查点及保留消息数。原 Compose 菜单仅执行检查点列表指令。现接入同一弹层和列表组件，按当前会话读取结构化检查点，包括固定空点、当前标记和保留消息数。保存保持弹层，成功回退后关闭并显示聊天回执；当前会话生成时禁用入口及操作。已有 Slash Command 保持纯文本。
- 菜单保存、回退经类型化服务调用现有完整轮次建点与删尾逻辑。新增 `checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint` 从正式入口打开专属会话，核对初始空点、保存后检查点和消息计数、回退后聊天回执、Room 轮次删除以及重新打开时的当前标记。测试输入为隔离专属会话的合成已完成轮次。
- MEIZU 20 Pro（Android 16）使用同批 debug App 和 androidTest APK，执行 `adb shell am instrument -w -e class 'com.elymbot.android.ElymBotUiIntegrationTest#checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (1 test)`，耗时 5.412 秒。

### 聊天软键盘打开闪动

- 主 Activity 的窗口配置显式启用 edge-to-edge 与 `adjustResize`，Compose 根布局通过 `safeDrawingPadding()` 同时接收系统栏与键盘 Insets。去除根布局额外声明的 `imePadding()`。Compose 会消费已处理的 Insets，源码核对无法确认重复声明导致了闪动。聊天输入区布局与底部弹层自己的键盘处理未变。
- 在 MEIZU 20 Pro（Android 16）的同批 debug APK 上，从主会话点击输入栏打开实体软键盘，按返回收起，共两轮。以 `adb shell screenrecord --size 720x1600 --time-limit 8` 录制并逐帧查看两次展开和收起的画面。输入栏随键盘移动且全程可见，未观察到闪白、键盘重现或输入栏跳回。没有在设备重装旧 APK 对照，因此根因仍未确认。 [Android 窗口配置](https://developer.android.com/develop/ui/compose/system/setup-e2e) · [Compose Insets 消费](https://developer.android.com/develop/ui/compose/system/insets-ui)

### T01.07 验收 9–11 证据复用

- 复用 [正式入口定向测试](#t0107-正式入口定向测试2026-11-14) 的真机 2/2 结果。人格编辑页分别显示封面和头像预览，测试操作各自的水平滑块并核对保存后的独立位置。专属会话列表一次删除两条所选会话，打开带新消息标记的会话并返回后，界面徽标消失，Room 未读状态清零。
- 复用 [W01 正式入口与双会话合成复验](W01-总控与集成-feedback.md#android-正式入口与双会话合成复验)，确认回复在离开会话后完成时显示对应会话的新消息徽标。与 W05 打开后清除徽标的结果共同覆盖未读提示。勾选 [Checklist](../T01-Core和基础app重构-checklist.md#t0107-原型界面资源与-viewmodel) 的验收 9–11。验收 1–6 仍待正式聊天中助手工具反馈的纯文本证据。

### T01.07 正式聊天工具反馈与人格筛选补证

- `AppFailureIntegrationTest#rejectedHistoryReadShowsAssistantTextAndContinuesWithCompleteJsonReply` 复用正式 MainActivity 和本地合成端点。首个 JSON 响应发出缺少 `toTurn` 的 HistoryRead 调用。测试断言后续请求按同一调用 ID 回填完整拒绝文本，聊天显示助手纯文本回执，放行最终 JSON 后显示完整回复，并核对两条助手文本落盘。最终响应的闸门延长至 30 秒，超过 Compose 观察窗口，测试退出与端点关闭时均释放闸门。
- `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 从正式人格库创建带标签和提示词的人格，按名称搜索并验证匹配与不匹配标签。根据新建前后的 ID 差异识别用例创建的人格，只按实际新增的 ID 清理。
- 最终 AndroidTest 构建通过。MEIZU 20 Pro（Android 16）拒绝安装更新的测试 APK。两个用例均未运行，其现有 MainActivity 夹具连接目标 App 的持久化 `elymbot.db` 并涉及写入主会话或模型设置，后续在独立测试环境执行。T01.07 的验收 1–6 仍待取得正式聊天助手文本证据。

#### MuMu 独立环境复验

- 在 MuMu（Android 15）使用合批 `:app:assembleDebugAndroidTest` 生成的测试 APK。`AppFailureIntegrationTest#rejectedHistoryReadShowsAssistantTextAndContinuesWithCompleteJsonReply` 首轮通过。正式聊天显示助手纯文本拒绝回执，合成端点收到带原调用 ID 的拒绝结果，端点放行最终 JSON 前界面未出现最终回复，放行后显示完整回复并核对两条助手文本落盘。
- `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 首轮因搜索输入框和人格卡片同时匹配而失败。按可点击且不具备文本编辑动作收紧卡片选择器，修复后定向复验通过，覆盖正式人格创建、名称搜索、标签过滤及组合过滤。
- T01.07 验收 1–6 继续待核验。人工最短路径使用文本对话模型已探测为不支持视觉、视觉理解模型为「未选择」的测试配置。进入聊天点「添加图片」，从系统选图器选一张测试图，确认「图片附件」及缩略图后发送，核对「请配置可用的视觉理解模型」与「模型配置」入口。该路径同时验证选图结果回填和缺少可用视觉模型时的提示。

#### MuMu 停止生成、空库首建与系统选图

- MuMu Android 15 使用已有的 debug App 和 androidTest APK。运行 `AppFailureIntegrationTest#stopKeepsVisibleFirstDeltaAndDiscardsReleasedTail` 得到 `OK (1 test)`，耗时 2.795 秒。首段在聊天可见后点击停止，界面显示已停止，随后允许合成端点尝试继续发送，确认聊天和 Room 仅保留首段且轮次为 `STOPPED`。
- 在独立 MuMu 上清除测试 App 数据后打开，确认初始人格库为空。通过「创建人格」输入测试名与提示词，使用系统选图器选择自建双色 PNG 作为封面。保存后人格显示「已激活」，点击卡片进入该人格的空主会话。
- 在主会话再次打开系统选图器，选取同一张测试图。返回后正式聊天显示「图片附件」、缩略图和「移除图片」。随后点发送时，此空库未配置文本模型，App 显示文本模型配置提示。
### 人格标签按压与聊天等待动画

- 人格标签保留 48 dp 的触区，按压反馈使用同一交互源绘制在裁剪后的 32 dp 胶囊内。水波颜色在选择状态切换时保持稳定，避免替换动画节点。底部导航本身已限定按压形状。
- 聊天等待三点使用原型 v0.6.3 的透明度脉冲，单向 1.2 秒，逐点延迟 150 毫秒。延用原等待状态的出现条件、尺寸、间距和无障碍标记。动效使用 Compose 动画时钟，遵循系统动画时长设置。 [Compose 动画](https://developer.android.com/develop/ui/compose/animation/value-based)
- `:app:assembleDebug :app:assembleDebugAndroidTest` 构建通过。MuMu Android 15 运行人格创建及筛选、停止生成两个定向用例，共 2/2 通过。水波颜色稳定化后重新构建并安装 App，录制「学习」「全部」短按与长按，逐帧检查按压色块限制在胶囊内，标签切换生效。等待三点的节奏由原型和实现静态核对，设备上的 SSE 用例通过，仪器测试录屏未捕获足够的等待帧来独立核对脉冲节奏。

### 页面导航过渡与返回

- 根页使用 220 ms 交叉淡化，下钻与返回使用 300 ms 横移。上层进出移动整页宽度，下层移动 30% 页宽并在盖住时加最多 10% 遮罩。底栏沿自身高度滑入滑出，同时淡化。人格筛选的填色、描边和文字颜色使用 150 ms 过渡，胶囊内水波纹继续可用。转场按原页面渐变绘制。
- 当前目的地作为一个可保存快照，聊天仍按会话 ID 保存输入草稿。导航切换清除输入焦点，退场页面隐藏操作弹层并停止处理返回，提交系统返回与顶栏返回共用目的地逻辑。ModalBottomSheet 继续由 Material3 管理。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 成功。MuMu Android 15 首轮执行 `ElymBotUiIntegrationTest,AppSessionIntegrationTest` 共 6/6 通过。调整渐变、焦点与返回遮罩后，两包构建成功，更新 App 后定向复跑设置与会话导航测试和双会话并行测试，结果 2/2。人工在聊天输入测试草稿，进入模型配置后按系统返回，确认草稿仍在聊天输入栏。快速按两次返回从会话列表回到人格库。

### 头像和封面直接裁剪

- 人格表单中有图片时可点封面或头像预览，打开各自固定裁剪框并单指拖图。封面编辑框沿用 1.79 比例，头像为正方形。人格库通栏与双列卡片继续分别使用 1.79 和 1.06，同一归一化封面位置按各自显示比例计算。现有 `ImagePosition` 和持久化格式沿用。
- 裁剪绘制与拖动共用铺满裁剪框后的实际像素溢出，拖动只改变有溢出的轴并限制到图片边缘。选择「完成」后位置进入表单草稿，取消或系统返回均丢弃本次调整。裁剪框提供对应可移动轴的无障碍移动动作，替代原滑条。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 成功。MuMu Android 15 更新两包后执行 `ElymBotUiIntegrationTest#personaEditorSavesIndependentCoverAndAvatarPositions`，结果 `OK (1 test)`，耗时 7.568 秒。用宽图核对水平无障碍动作、拖动后取消与系统返回、完成后分别保存两种裁剪位置，以及垂直位置保持原值。未在实体手机上安装本轮 APK。 [Compose 拖动检测](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-composables)

### 检查点回退确认

- 从聊天检查点列表点选检查点时显示确认弹窗。标题显示编号，提示永久删除且说明保留范围，固定空点说明会清空当前会话。取消和系统返回只关闭弹窗，确认才交给原有回退流程。服务在既有事务内核对编号与稳定 ID；编号复用时保留新检查点及其历史。
- `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。在独立 MuMu Android 15 运行 `ElymBotUiIntegrationTest#checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint` 与 `ElymBotStorageTest#typedCheckpointApiListsRetainedHistoryRestoresAndBlocksMutationsWhileGenerating`，结果 `OK (2 tests)`，耗时 6.437 秒。覆盖普通点与空点文案、取消和返回保留轮次、确认产生一条回执并删尾、过期 ID 与生成中回退拒绝。

### 页面切换与编辑开销优化

- 页面切换时遮罩由独立图层处理透明度，固定大小的整页转场关闭尺寸插值。会话列表按会话 ID 使用 LazyColumn，保留圆角、单分隔线和多选操作。编辑人格时封面、头像与裁剪共用该表单内同一份已解码图片，更换图片时立即丢弃旧位图引用。
- 基线 `9dd7db5` 与本轮候选均在独立 MuMu Android 15 取样。每条入口热身一次后进行三次进入，使用 `dumpsys gfxinfo ... framestats`，按 IntendedVsync 取每次新增的有效帧，耗时用 FrameCompleted − IntendedVsync，慢帧以 16.67 ms 为比较阈值。编辑路径使用测试人格已有的本地封面。

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
- `:app:assembleDebugAndroidTest` 通过。在独立 MuMu Android 15 安装基线 `4fba0e0` 的 debug APK 和测试 APK，运行该用例。首次保存成功，重开出现已填入提示，再次保存后凭据记录存在性断言失败。生成的 Room SQL 对 `model_services` 执行 `INSERT OR REPLACE`，删除旧服务行时触发 `service_credentials` 的外键级联删除。编辑页把空 Key 交给保存入口，保存逻辑跳过写入新凭据。
- Room 改用更新现有服务行后，`:app:assembleDebug :app:assembleDebugAndroidTest` 通过。候选 debug APK（SHA-256 前缀 `DC73D9DE794F`）在同一 MuMu 上运行上述用例，以及 `ElymBotStorageTest#savingModelServicePreservesAndInvalidatesProbeCacheWithCredentialIdentity` 和 `#modelServiceUrlAndCredentialUpdatesStayAtomicAcrossSnapshotsAndDrafts`，得到 `OK (3 tests)`，耗时 3.657 秒。再次保存后凭据记录仍在，重开仍显示原提示，覆盖 T01.07 模型配置的该反馈路径。仅使用合成凭据和隔离模拟器。
- 同地址再次输入同一 Key 时沿用原密文。调整后重建两包，在新版 debug APK（SHA-256 前缀 `F04499BDB9AA`）上复跑同一组用例，结果 `OK (3 tests)`，耗时 3.593 秒。

### 2026-11-16 聊天添加菜单、人格标签与系统深色

- 聊天输入框左侧的添加菜单合并图片入口与当前指令名。用例从正式专属会话选改名后的帮助指令、附图、切换至检查点指令，核对已有参数和附件保留。对比会话服务返回的消息与 Room 轮次，指令选择期间无新增回执或模型轮次。选图结果由仪器测试模拟返回。
- 人格库保留「全部」「常用」入口，新增标签后可选人格并保存，筛选与搜索使用保存后的标签。人格编辑取消了标签合计 60 字截断，避免继续编辑时裁掉持久化标签。底部导航选中态只保留图标胶囊，整项按压使用同一区域绘制反馈。
- 系统深色使用原型 v0.6.3 第 35–38 行的暖色页面、表面、轮廓、强调与成功色。补充危险操作前景、弹层遮罩和缺省封面底色。已有图片继续使用固定遮罩与图上前景色。静态计算深色正文与底色的对比度为 14.55:1，次级文字与表面为 7.90:1，成功徽标为 7.62:1，危险按钮为 7.53:1。 [高保真原型](../ElymBot_高保真原型.html)
- 以 `bf4136e` 加本轮工作区改动运行 `:app:assembleDebug :app:assembleDebugAndroidTest`，两包构建通过。debug APK 与测试 APK 的 SHA-256 前缀分别为 `8BE05EDF746A`、`8CB0CC709D0B`。在 MuMu（Android 15/API 35）安装两包，使用 `adb shell am instrument -w -e class 'com.elymbot.android.ComposerAddMenuTest,com.elymbot.android.ElymBotUiIntegrationTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (7 tests)`，耗时 21.2 秒。覆盖 T01.07 的人格新建与标签、聊天输入与添图，并复验导航、模型服务保存、专属会话、检查点和图片位置。
- 在同一模拟器用 `cmd uimode night yes` 开启系统深色，实际打开人格、聊天添加菜单、配置与添加模型服务表单。截图核对页面和弹层层次、输入框及文字、图片区可读性和状态栏图标。从人格切至配置后只见配置图标带胶囊，静态截图未覆盖按下瞬间。结束后恢复原系统浅色与自由旋转设置，另保存浅色人格页截图。本轮未在实体手机安装或观察深色效果。

### 2026-11-16 人格标签栏加号尺寸与收藏用词

- 标签栏最右侧加号与普通标签共用胶囊边框、32 dp 高度、最小宽度和水平内边距，点击区域仍为 48 dp 高。横向滑动可到达加号，空库时加号禁用。内置筛选显示「全部」「收藏」，人格编辑显示「加入收藏」。收藏筛选仍读取已有收藏标记。
- 在 `b312068` 基线上运行 `:app:assembleDebug :app:assembleDebugAndroidTest` 通过，debug APK 的 SHA-256 前缀为 `54224115AC40`。在 MuMu（Android 15/API 35）运行 `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag`，结果 `OK (1 test)`，耗时 3.983 秒。正式人格页同屏截图显示「全部」「收藏」和加号三枚胶囊等宽等高。

### 2026-11-16 外观设置与聊天指令补全

- 设置页新增「外观」，提供「跟随系统」「浅色」「深色」单选及当前项提示，指令名称编辑仍可从同页进入。默认跟随系统，使用独立的本地偏好保存选择。模式切换即时更新 App 配色、主窗口和底部弹层窗口的系统栏图标。首轮截图发现系统浅色时，打开中的弹层切到深色后仍保留深色状态栏图标。共享弹层改为在配色变化后同步实际窗口的系统栏外观。
- 聊天输入框输入 `/` 或部分指令名时，按现有指令别名提示候选。加号菜单保留添加图片和指令入口。候选按主会话与专属会话的现有可用范围显示，选择后保留已有参数和图片，等待用户发出。定向用例覆盖别名、输入替换、附件保留、指令候选切换及会话无新增回执或轮次。
- 以 `49987ca` 加本轮改动运行 `:app:assembleDebug :app:assembleDebugAndroidTest` 通过。debug APK 与测试 APK 的 SHA-256 前缀分别为 `5AD09EB4ABC0`、`2824463D2A13`。在 MuMu（Android 15/API 35）运行 `adb shell am instrument -w -e class 'com.elymbot.android.AppearanceSettingsTest,com.elymbot.android.ComposerAddMenuTest' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (2 tests)`，耗时 6.071 秒。外观用例覆盖三项单选、Activity 重开后保留深色和主窗口两类系统栏标志。
- 模拟器系统保持浅色时，在同一外观弹层从跟随系统切到深色，再切回跟随系统，截图确认页面与弹层随即更新、弹层状态栏图标相应变亮及变深。手动强制停止并冷启动也保留了已选深色。模拟器恢复跟随系统和自由旋转。本轮使用合成附件，实体手机和 Android 26–34 未验证。

### 2026-11-17 从人格编辑选择标签

- 人格库标签栏右端的加号只创建标签。保存后可立即筛选该标签，空标签会保留。新建及编辑人格的表单从标签目录勾选，已有标签默认勾选，保存时一次提交当前选择。取消最后一个人格的关联后，标签仍可筛选。
- 以 `03a97e1` 加工作区改动执行 `:app:assembleDebug :app:assembleDebugAndroidTest`。在 MuMu（Android 15/API 35）从旧版正式入口创建 `LegacyTagPersona`，给它填入 `LegacyV1Tag`。用 `sqlite3 -readonly` 确认数据库 `user_version` 为 1。以 `adb install -r` 覆盖安装 APK（SHA-256 前缀 `7E49649E351A`），打开正式入口后再读，版本为 2，原人格及标签关联保留，`persona_tags` 中已有 `LegacyV1Tag`。选中该标签后，只显示 `LegacyTagPersona`。升级前后。
- 定向用例 `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 首次在全部人格下尝试直接定位未加载的卡片，测试改为先进入该人格已知的标签筛选。重建两包后（SHA-256 前缀 `E50B7DCC3F3C`、`C5C0761D8CA8`），再次运行得到 `OK (1 test)`，耗时 6.067 秒。覆盖创建空标签、创建人格时勾选、编辑时保留原有选择、新增选择在保存后生效，以及清除最后一个关联后标签仍在。
### 2026-11-17 聊天浮动输入、发送后定位与本地回执

- 输入框悬浮于聊天底部。聊天列表在同一布局测量中为输入框实际高度留白，随多行草稿、图片附件和斜杠指令候选变化。短视口优先分配输入行高度，附件与候选使用剩余空间。
- 成功发送后的定位以正式会话流出现新增的用户气泡为条件，末尾定位项目覆盖末条长消息。向上阅览时外部新增消息沿用原跟随状态。点击发送和键盘发送使用同一入口，指令回执通过实际会话流一起呈现。
- 本地生成的简短回执显示为居中状态胶囊，多行结果显示为可选取的独立信息面板。回执样式由会话消息的 `localReceipt` 标记决定。
- Windows PowerShell 使用 Gradle Wrapper 执行 `:app:assembleDebug :app:assembleDebugAndroidTest --console=plain --quiet`，两包构建通过。debug APK 与测试 APK 的 SHA-256 前缀分别为 `DEEE19EE9628`、`C5C0761D8CA8`。
- 在 MuMu（Android 15/API 35）运行 `adb shell am instrument -w -e class 'com.elymbot.android.ComposerAddMenuTest,com.elymbot.android.ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag' com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (3 tests)`，耗时 11.423 秒。聊天用例覆盖上滑查历史后收到外部会话更新、再次点击发送及触发 IME 发送、末尾消息与回执的底边位于浮动输入区域之上，以及加号入口、斜杠补全、合成附件与草稿保留。键盘发送通过 Compose IME 动作触发，实体设备与实体键盘未验证。

### 2026-11-17 人格卡片导航与全局人格设置

- 人格库使用主会话所属人格判断入口。全局人格打开同一主会话，其余人格直接进入该人格的专属会话列表。两类卡片都只导航。专属会话从模型配置返回聊天，再按原路径返回列表与人格库或主聊天。
- 从非全局人格的会话列表右上角「人格菜单」可编辑或删除该人格。编辑页勾选「设为全局人格」后保存才会替换主会话所属人格，当前全局人格显示为已勾选。普通编辑和关闭表单保留当前全局人格。聊天头像不再提供设置入口，显式 `/persona` 指令仍可切换主会话所属人格。
- 聊天补全的触发条件、匹配范围及斜杠配置集中到 `ElymBotComposerCompletion.kt`，保留现有补全行为。
- 以 `18ae52c` 加本轮工作区改动执行 `:app:assembleDebug :app:assembleDebugAndroidTest`，首次遇到 Kotlin 编译守护进程启动失败。临时加 `-Pkotlin.compiler.execution.strategy=in-process` 后两包通过，debug APK 与测试 APK 的 SHA-256 前缀为 `9D47DEAF2433`、`D072FF0A4AFD`。
- 在 MuMu（Android 15/API 35）以两包运行 `ElymBotUiIntegrationTest#openingOtherPersonaKeepsMainOwnerUntilGlobalEditIsSaved`、`#personaCreatedThroughGalleryCanBeFoundByNameAndTag`、`#renamedPersonaCommandSwitchesMainAndDedicatedSessionsRetainTheirOwner` 和 `ComposerAddMenuTest`，得到 `OK (5 tests)`，耗时 17.468 秒。覆盖专属会话及模型配置返回路径、点击人格保持主会话所有者、编辑勾选与取消、保存后沿用同一主会话 ID、专属会话保留所属人格、标签编辑、指令别名和斜杠补全。

### 2026-11-17 配置分类、设置分组与界面拆分

- 配置页从底栏和聊天菜单使用同一入口。页首与人格库共用横向可滚动的标签栏及选中样式，固定显示「模型」「上下文」「工具」。模型服务、用途分配和能力探测保留在「模型」；「上下文」和「工具」当前显示「暂无配置项」。分类是页面界面状态，模型内容滚动位置在本页切换分类时保留。
- 设置页将「外观」「指令名称」分为各自的圆角卡片，整行可点，使用统一高度和图标、文字、箭头顺序。两组当前各一行，组内多行分隔线按标题起点内缩。外观选项和指令名称仍由既有弹层与保存入口处理。
- 从应用导航分离人格库与共用标签栏，从人格编辑分离专属会话列表，从模型配置分离设置页。聊天按页面编排、输入框、检查点和消息渲染分文件，发送、回退和消息渲染沿用原调用链。
- 在 `a5bfbc4` 基线上集成 App 和 Core 文件拆分后，Windows PowerShell 以 Android SDK 37.0 运行 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -Pkotlin.compiler.execution.strategy=in-process --console=plain`，两包构建通过，App JVM 测试任务为 `NO-SOURCE`。debug APK 和测试 APK 的 SHA-256 前缀分别为 `71AE9C7DE403`、`FA768040488F`。
- 在 MuMu（Android 15/API 35）使用 `adb install -r` 覆盖安装两包，保留已有测试 App 数据。用 `adb shell am instrument -w -e class <定向选择器> com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 得到 `OK (8 tests)`，耗时 19.656 秒。覆盖 `AppearanceSettingsTest`、`ElymBotUiIntegrationTest` 的设置与配置导航、人格标签和检查点用例、`ComposerAddMenuTest`，以及 `ElymBotStorageTest` 的模型连接凭据原子性与非流式视觉两路用例。设置与导航用例覆盖底栏分类切换、聊天配置默认分类、回到原聊天、指令改名后执行及会话操作。
- 在同一模拟器浅色横屏从正式入口查看两页。配置顶端三枚标签的选中态和空状态可见，设置两行同高且各自置于圆角卡片，保留既有主题配色。本轮未在实体设备验证，T01.07 用户验收保持待完成。
- 在同一模拟器补查浅色竖屏，配置三枚分类标签完整显示，设置两组卡片左右留白一致且标签和箭头可见。

### 2026-11-17 主页面随手势变化的悬浮搜索

- 人格、配置、平台、插件和设置共用默认字体的 28 sp 粗体标题与搜索框。聊天内打开的配置使用同一标题和组件。平台和插件可从底栏进入，显示各自的空状态。搜索匹配当前页面及分类的真实内容，关闭后恢复浏览位置和选中标签。系统返回优先关闭搜索。
- 收起和展开按内容实际消耗的纵向拖动距离逐帧变化，手指反向时立即反转，松手后以 440 ms 的 App 动效收尾。点击放大镜使用同一形变，导航和标签淡变使用 300 ms。列表首尾为控件保留可达空间，滚动范围不随标题动效变化。悬浮搜索与聊天单行输入外框同为 62 dp 高、左右各留 10 dp。搜索和底栏以半透明面板覆盖内容。竖屏旋转保留已有列表位置时，单层页首背景保护展开的标题和标签区域，收起时与标签同步淡出。
- 在 `bd1e021` 加工作区改动的基线上执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，两包通过。最终 debug APK 与测试 APK 的 SHA-256 前缀为 `DA500C6F950E`、`866ACF9BFA12`。
- 在 MuMu（Android 15/API 35）安装两包。首轮改用等待键盘退场后定位底栏、只定位实际设置操作行、返回已填充的人格库后重新搜索目标。加入随手势进度和悬浮层后，运行 `PrimaryPageSearchTest` 及 `ElymBotUiIntegrationTest` 的人格名称与标签、设置与配置导航、专属会话配置返回用例，结果 `OK (6 tests)`，耗时 19.459 秒。修正竖屏页首背景后以最终产物复跑 `adb shell am instrument -w -e class com.elymbot.android.PrimaryPageSearchTest com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，得到 `OK (3 tests)`，耗时 6.008 秒。覆盖抬手前的中间进度和反向、搜索位置和宽高、关闭后的相同滚动位置、空平台和插件、设置短内容零滚动范围、设置筛选及返回优先级。
- 从正式入口查看同一已填充的人格库。1600×900 横屏收起态的搜索控件边界为 `[15,45][1585,138]`，两个悬浮面板均可见下方内容。900×1600 竖屏旋转后，已滚动卡片在标题和标签区域的下沿通过，标题与标签完整且状态栏区域保留。已恢复模拟器自由旋转。实体设备及用户验收待完成。

### 2026-11-17 应用图标和原生启动动效

- 桌面图标和系统启动页共用用户提供的 `app/src/main/res/mipmap-nodpi/icon.png`。资源与原图的 SHA-256 均为 `927914835C604DE3E6BB742ED503B5E9F66B6C6395213CA419DA6B67CC3079A3`。启动页为图标留出系统遮罩范围，底色跟随系统的浅色或深色模式。
- 主 Activity 在创建时安装 AndroidX SplashScreen，系统交出启动页后，图标轻微放大并随覆盖层在 440 ms 内淡出到实际页面。系统关闭动画时直接移除覆盖层。App 内切换外观和导航沿用现有入口。
- 从 `4aee483` 加工作区改动运行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`。两包通过，debug APK 与测试 APK 的 SHA-256 前缀分别为 `A08CCA0CB5BC`、`602F8BB40C02`。`aapt dump badging` 的应用图标均指向 `res/mipmap-nodpi-v4/icon.png`，`aapt dump resources` 可定位两种启动底色和共享图标。
- 在 MuMu（Android 15/API 35）覆盖安装两包。使用 `am force-stop` 后从 Launcher Intent 启动并录屏，浅色且动画倍率为 1 时可见完整图标和淡出，深色且动画倍率为 0 时显示深色系统启动页并直接进入应用。桌面可见新图标。以关闭动画的设置运行 `AppearanceSettingsTest` 和 `MainActivityIntegrationTest`，结果为 `OK (2 tests)`，耗时 2.977 秒。复验后恢复模拟器系统浅色与动画倍率 0.5。
- 覆盖 T01.07 启动入口、外观和资源回归。实体设备、Android API 26–34 及用户验收待完成。

### 2026-11-17 可拖动底栏和关于页

- 对照 FlClash v0.9.3 提交 `be84736605527034689c95bf27828ff27d912c5e` 的手机底栏入口及锁定的 Prism UI 0.1.2 子模块。Prism UI 提交 `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185` 的 `navigation_dock.dart` 分别处理指针、镜片和区域内外染色。 [FlClash 手机入口](https://github.com/chen08209/FlClash/blob/be84736605527034689c95bf27828ff27d912c5e/lib/pages/home.dart#L98-L111) · [Prism UI 指针处理](https://github.com/chen08209/prism_ui/blob/63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185/lib/navigation_dock.dart#L1122-L1245) · [Prism UI 染色裁切](https://github.com/chen08209/prism_ui/blob/63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185/lib/navigation_dock.dart#L2026-L2083)
- ElymBot 底栏只画一枚暖色半透明镜片。横向拖动时镜片随手指连续移动并按速度拉伸，内部图标和文字局部染色。松手吸附最近项并切换页面，取消恢复原选中项。普通点击和标签访问语义仍有效。Compose 仅裁切本地绘制的底栏内容。
- 已滚动的人格库在旋转为竖屏后可复现页首背景硬边。页首背景底部增加 10 dp 渐隐，标题、标签和列表滚动范围沿用原布局。模拟器浅色内容上另可见底栏内部的淡色条带，本轮未确认来源。
- 「设置 → 关于」展示应用图标、安装版本、MIT 许可证和项目资料入口，包含源代码、发布记录和开源许可。页首和系统返回均回到设置，关于页本身不显示底栏。
- 在 `638b59e` 加本轮改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，两包通过，SHA-256 前缀为 `567D0933AFD2`、`6212014AA03A`。在 MuMu（Android 15/API 35）使用 `adb shell am instrument -w -e class com.elymbot.android.FloatingNavigationDockTest,com.elymbot.android.AboutSettingsTest,com.elymbot.android.PrimaryPageSearchTest com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 得到 `OK (5 tests)`，耗时 8.002 秒。覆盖底栏抬手前保持原页、反向后松手切换、取消和点按，关于页实际版本与返回，及既有搜索回归。
- 横屏以系统输入事件按住底栏拖至配置与平台分界，截图可见两侧图标和文字的局部染色。旋转前后记录页首背景硬边与渐隐结果，随后恢复自由旋转。实体设备与用户验收待完成。

### 2026-11-18 人格编辑弹层和模型配置入口

- 创建和编辑人格共用底部弹层。标题与关闭按钮固定，基本信息、提示词、图片、使用方式和保存入口在内容区滚动。标签支持多选，收藏和全局人格使用开关。切换全局人格在保存时生效，当前全局人格的开关显示开启并禁用。提示词输入框初始三行，随内容长到八行后在框内滚动，保存沿用 1200 字符限制。
- 模型配置从主界面底部的「配置」进入。聊天菜单保留会话、人格或会话编辑及检查点操作。聊天中的文本、视觉模型提示指明返回首页后的入口。
- 在 `1c32c64` 加本轮工作区改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`。两包通过，SHA-256 前缀为 `F4901F84B661`、`D126F07F307B`。随后只调整测试选择器和底栏出现时机的等待，重建测试包，最终测试包 SHA-256 前缀为 `EE963F71B2C3`。
- 在 MuMu（Android 15/API 35）通过 `adb shell am instrument -w -e class <定向选择器> com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 运行 `ElymBotUiIntegrationTest` 的六项用例。首轮四项通过，覆盖提示词高度、长度限制与收藏保存，全局人格开关及保存语义，聊天菜单及专属会话导航，封面和头像位置独立保存。区分弹层选择项与人格库筛选标签，并等待从聊天返回后底栏可见，定向重跑标签和缺模型用例得到 `OK (2 tests)`，覆盖按标签创建和编辑、缺模型提示、底栏进入配置及凭据保留。
- 正式入口上，创建人格弹层在横屏 1600×900 从 y=106 开始，竖屏 900×1600 从 y=190 开始。用系统输入事件填入 20 行，框内拖动后可见内容由第 14–20 行移至第 10–17 行，提示词框和外层分组的位置一致。两种方向请求显示模拟器的输入法时系统都报告零高度输入法区域，软键盘占据画面时的适配尚未验证。验证后恢复自由旋转及输入法设置。实体设备及用户验收待完成。
- 根据本轮界面反馈，提示词区单独显示标题和输入框，移除分组外框。以同一构建参数执行 `gradlew.bat :app:assembleDebug`，新调试包 SHA-256 前缀为 `9D28563C39CF`。在同一 MuMu 实例使用原测试包复跑 `ElymBotUiIntegrationTest#personaPromptGrowsToItsLimitAndSavesWithFavorite`，结果 `OK (1 test)`，耗时 2.396 秒。通过创建人格入口核对仅有输入框边框。

### 2026-11-18 多标签选择和搜索收起动效

- 人格表单中 8 个以内的标签直接展示。超过 8 个时，从「选择标签」打开可搜索、滚动和只看已选的弹窗，按标签名称匹配并显示已选数量。点「完成」应用到表单草稿，保存人格后写入。人格库新增标签后保留当前筛选，新标签进入标签列表。
- 点击搜索入口与滚动跟随使用同一收缩进度。标题和标签逐渐上移并淡出，标题保持文本排版并随搜索框展开裁切，搜索结果顶部空间随标签收起逐步缩小。关闭搜索回到之前的浏览位置。
- 在 `1c32c64` 加本轮工作区改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，两包构建通过，SHA-256 前缀为 `CA677425E6C0`、`AED867814FEC`。
- 在同一 MuMu 实例使用 `adb shell am instrument -w -e class <定向选择器> com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 运行 `PrimaryPageSearchTest` 和 `ElymBotUiIntegrationTest` 的 `personaCreatedThroughGalleryCanBeFoundByNameAndTag`、`manyPersonaTagsCanBeSearchedScrolledAndSaved`，结果 `OK (6 tests)`，耗时 15.786 秒。覆盖全部、收藏、自定义标签筛选的保留，24 个合成标签的搜索、滚动、多选、取消和保存，以及点按后第 192 毫秒的标题和标签位移、搜索结束状态、反向拖动、空页和关闭搜索后的位置与返回层级。实体设备、软键盘占据画面时的布局及用户验收待完成。

### 2026-11-18 检查点弹层高度和下拉手势

- 检查点和人格表单弹层的内容区最多使用可用高度的 88%。标题和关闭按钮固定，超出的内容在弹层内滚动。两处使用 Material 3 原生拖动，收起位置阈值为 112 dp，速度阈值为 900 dp/s。较小幅度的拖动松手后回位，明确下拉可收起。
- 在 `1c32c64` 加本轮工作区改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，两包构建通过，SHA-256 前缀为 `796500F43033`、`C5F85D8E5827`。
- 在同一 MuMu 实例使用 `adb shell am instrument -w -e class <定向选择器> com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 运行 `ElymBotUiIntegrationTest` 的 `checkpointSheetCapsOverflowAndRequiresADeliberateSwipe`、`personaEditorRequiresADeliberateSwipe`、`checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint`、`personaPromptGrowsToItsLimitAndSavesWithFavorite`，结果 `OK (4 tests)`，耗时 8.452 秒。覆盖 28 个合成检查点的显示高度、最早项滚动可达和标题位置，两个弹层的短滑回位和 180 dp 下拉收起，检查点弹层系统返回、保存和回退确认，以及人格表单保存。实体设备触感和用户验收待完成。

### 2026-11-18 点击搜索时的人格封面闪烁

- 在 MuMu 使用前一轮调试包 `CA677425E6C0` 从人格库按下搜索入口。录屏第 902 毫秒时封面变为占位图，下一帧恢复，搜索框从第 936 毫秒开始变形，底栏稍后退场。封面中央区域在占位和恢复两次变化中各有 99.6% 像素的通道差超过 18。对应源码会在点按时立即将浏览列表切换成空字符串结果列表，重新加载可见封面。
- 人格、配置、平台、插件和设置共用的搜索在空白输入时保留原内容、顶部留白及浏览滚动状态，输入有效关键词后才使用独立的结果列表。清空关键词恢复原浏览位置，标题和标签继续随同一个搜索变形进度收缩。
- 在 `1c32c64` 加本轮工作区改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，应用与测试 APK 的 SHA-256 前缀分别为 `A2ACA28A4851`、`7E9D4189A7D6`。同一 MuMu 实例重新录制点按，在所录帧中封面全程可见，封面中央区域没有通道差超过 18 的像素。运行 `PrimaryPageSearchTest` 和 `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 得到 `OK (5 tests)`，耗时 12.948 秒，新增滚动后点开空搜索及清除搜索文字时的滚动位置断言。
- 该模拟器的输入法报告显示时区域高度为零。实体设备与实际软键盘占位待验证。

### 2026-11-18 点击展开搜索时内容上浮

- 点按展开搜索时，标签区让出的空间从内容顶部移到底部。空白搜索继续显示原浏览内容、封面和滚动状态，输入关键词后进入原有结果布局。已滚动且标签完全收起时点按搜索，当前卡片保持原位。关闭搜索时逐步恢复原留白。
- 在 `1c32c64` 加本轮工作区改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，应用和首轮测试 APK 的 SHA-256 前缀为 `3706F9557DB3`、`294A8BCBB94A`。同一 MuMu 实例的 `PrimaryPageSearchTest` 和 `ElymBotUiIntegrationTest#personaCreatedThroughGalleryCanBeFoundByNameAndTag` 首轮四项通过，覆盖同一卡片的中间位移和终点、关闭复位、清空关键词后的紧凑间距、空页、设置及人格标签搜索。滚动用例首轮定位被裁切的卡片失败，改用卡片名称和未裁切坐标后重建测试 APK `E3E7E8443E9ED`，仅复验受影响用例，结果 `OK (1 test)`，耗时 2.970 秒，核对已收起态点按后的同一卡片位置和浏览滚动值。
- 同一模拟器横屏 1600×900 从人格库点按搜索。上轮录屏中封面上沿一直为 y=240，本轮经过 y=192 后到达 y=148，关闭后回到 y=240。录屏中封面始终可见。实体设备和实际软键盘占位待验证。

### 2026-11-18 删除人格自定义标签

- 人格库自定义标签可长按并确认删除。删除当前筛选标签后回到「全部」，删除其他标签时保留当前筛选。内置筛选仍以点按切换。
- Room 在同一事务中按完整标签名清理标签目录与所有人格的关联。表单保存时在写事务内重读目录，避免较旧表单写回已删除的标签。沿用现有数据库结构。
- 在 `1c32c64` 加本轮工作区改动执行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，两包构建通过，SHA-256 前缀为 `235C44E2D51A`、`F1A0431714E0`。
- 在 MuMu（Android 15/API 35）通过 `adb shell am instrument -w -e class <定向选择器> com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` 运行 `ElymBotUiIntegrationTest` 的 `deletingGalleryTagsRemovesOnlyTheirAssignmentsAndKeepsTheAppropriateFilter`、`personaCreatedThroughGalleryCanBeFoundByNameAndTag` 和 `manyPersonaTagsCanBeSearchedScrolledAndSaved`，结果 `OK (3 tests)`，耗时 12.087 秒。覆盖取消、删除使用中和未分配的标签、两个合成人格的数据保留、同前缀标签、保持原筛选和回到全部、旧草稿保存、同名重建后无旧关联，以及标签添加和多选保存。实体设备与用户验收待完成。
- 删除确认标题最多显示两行，长标签在视觉上省略并保留完整无障碍文本。用相同命令重建得到调试包 `0D4EE56559AA` 和测试包 `9B8DC6FED1FA`。同一 MuMu 实例复跑删除用例，加入超过 700 字符的合成标签，结果 `OK (1 test)`，耗时 2.982 秒。用例读取完整确认标题并点击删除。

### 2026-11-22 修复人格表单和检查点弹层快速上滑抖动

- MuMu 横屏 1600×900 从主聊天菜单打开人格编辑或检查点。修复前通过 `adb shell input swipe` 连续注入 70 毫秒上滑，逐帧匹配关闭图标。人格编辑内容区上滑后，匹配的图标区域顶部在 y=1–151 之间移动。检查点录屏中在 y=555–628 之间移动，单独从标题处上滑也复现。定位到 Material 3 1.4.0 用剩余向上速度启动收尾弹簧，整个弹层以展开锚点为目标仍会越过该位置。
- 人格表单和检查点的内容区保留惯性滚动，边界处仅消耗剩余向上速度。弹层完全展开且静止时，标题和拖动条拦住从该处新开始的上拉。下拉仍由 Material 3 处理，沿用关闭阈值和系统返回。
- 在 `1c32c64` 加本轮工作区改动运行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain --quiet`，构建通过。放弃在滚动过程中暂停 Compose 时钟的断言，复用调试包并重建测试包，最终 SHA-256 前缀为 `92C30ED2322E`、`7EE03715A472`。
- 同一 MuMu 实例重录正式入口上滑。人格编辑关闭图标在 84 帧中均为 y=151，从内容底部立刻反向时封面预览上缘从 y=241 移到 y=292，关闭图标保持原位，随后可点击关闭。检查点在内容区、标题和拖动条上滑期间输出 3 帧，关闭图标均为 y=628。
- 在 MuMu Android 15 模拟器上用原 AndroidJUnitRunner 选择 `ElymBotUiIntegrationTest` 的 `checkpointSheetCapsOverflowAndRequiresADeliberateSwipe`、`personaEditorRequiresADeliberateSwipe`、`checkpointMenuSavesCompletedTurnAndRestoresTheEmptyPoint`、`personaPromptGrowsToItsLimitAndSavesWithFavorite`，得到 `OK (4 tests)`，耗时 8.732 秒。覆盖快速上滑后标题位置、短下拉回位、长下拉关闭、检查点滚动与恢复确认、系统返回和人格表单保存。实体设备与用户验收待完成。
