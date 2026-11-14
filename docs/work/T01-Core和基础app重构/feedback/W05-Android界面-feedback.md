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
