# T02 — Tasks

[任务书](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md)

| Worker | 模型 | 组内顺序与依赖 |
| --- | --- | --- |
| [W01 Core 工具与 Reply](prompt/W01-Core工具与Reply-prompt.md) | GPT-6 Luna · Max | T02.01 → T02.02，最先发布 Core 契约 |
| [W02 Android 会话与投递](prompt/W02-Android会话与投递-prompt.md) | GPT-6 Luna · Max | T02.03 → T02.04 正式装配；T02.04 的平台、调度、确认与 UI 接口可提前发布 |
| [W03 插件底座与分发](prompt/W03-插件底座与分发-prompt.md) | GPT-6 Luna · Max | T02.05 → T02.06，依赖 Core 契约及 W02 早期接口，先将制品构建交 W04/W05 |
| [W04 QQ 平台接入](prompt/W04-QQ平台接入-prompt.md) | GPT-6 Luna · Max | T02.07 → T02.08，按 W02/W03 的交接接入 |
| [W05 主会话定时任务](prompt/W05-主会话定时任务-prompt.md) | GPT-6 Luna · Max | T02.09，按 W01/W02/W03 的契约接入，可与 W04 并行 |
| [W06 Android 页面与引导](prompt/W06-Android页面与引导-prompt.md) | GPT-6.2 Sol · Medium | T02.10 → T02.11，按提前交接的 UI 状态和动作开展，实际装配完成后联调 |
| [W07 集成与验收](prompt/W07-集成与验收-prompt.md) | GPT-6 Luna · Max | T02.12，收到所有前序交付后串行集成 |
| [W08 独立审核](prompt/W08-独立审核-prompt.md) | GPT-6.1 Sol · High | 各 Worker 交付按变更审查；T02.12 做包级只读审核 |

## 共享文件与交接

| 所有者 | 写入范围 | 接收方能开始工作的条件 |
| --- | --- | --- |
| W01 | `core/` 及其测试 | 基础消息与稳定执行/尝试标识、工具可见和撤销、Reply 持久提交、历史投影的契约及合成验证可供使用。与 W02 共同确定插件制品必需的纯 Kotlin 可调用契约，由 W01 在 `core/contract` 维护。 |
| W02 | `app/data`、`app/session`、`ElymBotApplication.kt`、App Manifest、`ElymBotUiService.kt`，以及宿主平台/调度桥接和 Android 数据测试 | 在真实装配前交付平台注册、触发/确认及 UI 状态动作接口，W03–W06 可用合成适配器推进。App 持有会话与平台投递授权，插件不得直接碰 Room。 |
| W03 | `settings.gradle.kts`、版本目录、App 与共享打包构建、最小启动二进制、App 的插件管理、分发、版本检查目录 | 宿主接口/安装身份、受信任包及兼容规则、原生启动入口可供 W04/W05 使用。插件专属构建文件在 W03 交接后由各插件 Worker 分别持有。 |
| W04、W05 | 各自 `plugins/qq/`、`plugins/scheduled-tasks/` 的代码、资源和测试 | 对应的真实制品、入口状态及适配动作已交付给 W02/W06；变更共享契约由原所有者串行处理。 |
| W06 | App UI、界面资源、`MainActivity.kt` 的呈现接线和专属 UI 测试 | 复用 W02 维护的 `ElymBotUiService.kt`，App 装配和路由权限由 W02 管理。 |
| W07、W08 | W07 在前序退出共享文件后串行集成并维护端到端测试及包级文档；W08 仅有审核记录 | 前序验证、实现版本及共享文件的所有权完成交接。 |

插件编译引用宿主提供的稳定契约，制品不得重复打包宿主 API 类型。W02 提供 App 端注册、触发和调度实现，W03 管理编译依赖和运行期类加载。宿主 API 变动由契约所有者先更新并交接，再修改消费端。

参考入口：[Core 工具](../../../core/src/main/kotlin/com/elymbot/core/tool/ToolRegistry.kt) · [ReAct](../../../core/src/main/kotlin/com/elymbot/core/agent/ReActAgentRuntime.kt) · [Room](../../../app/src/main/kotlin/com/elymbot/android/data/ElymBotDatabase.kt) · [会话服务](../../../app/src/main/kotlin/com/elymbot/android/session/ElymBotSessionService.kt) · [App 装配](../../../app/src/main/kotlin/com/elymbot/android/ElymBotApplication.kt) · [构建边界](../../../settings.gradle.kts) · [原型](ElymBot_T02.html) · [界面约束](../../rules/界面约束.md)。

<a id="t0201"></a>

## T02.01 工具权限与宿主契约

- **负责人** W01。**前置** T02 基线。
- **改动范围** `core/contract`、`ToolRegistry`、`HistoryReadToolSource` 与定向测试。定义最小统一输入、消息段、执行/尝试标识、工具作用域、可撤销安装身份、观察结果与 Reply 提交边界。用同一入口授权、顺序执行和按 ID 回填。
- **完成与验证** 交接给 W02/W03 的 Core 契约包含消息、Reply 持久受理所需标识、授权失败语义与历史投影。JVM 覆盖重复或未知工具、非法入参、过期实例、超时和取消。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0201) · A01、A05

<a id="t0202"></a>

## T02.02 非流式 Reply 与上下文

- **负责人** W01。**前置** T02.01。
- **改动范围** `ReActAgentRuntime`、`core/model`、`ContextCompiler` 与 Core 测试。复用原有完整响应适配，Reply 进入提交接口，移除普通正文和工具失败原样进入聊天的运行链；保留视觉、压缩与成功静默。
- **完成与验证** 交接 W02 成功静默、无有效 Reply、模型失败、取消及投递反馈语义。Mock HTTP 和 Core 测试覆盖非流式、多轮 Reply、普通文本受控处理、成功静默可进入历史及旧行为回归。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0202) · A02、A03、A15

<a id="t0203"></a>

## T02.03 会话持久化与投递队列

- **负责人** W02。**前置** T02.01，实际接线使用 T02.02。
- **改动范围** `app/data`、`app/session` 与对应数据测试。以现有 Room 与会话串行边界承载投递队列和 T01 数据迁移，保留历史、摘要、检查点和附件关系，记录稳定投递身份与逐目标状态。
- **完成与验证** 检查提交后 ACK、同会话 FIFO、重开恢复、未知回执、撤权、回退失效、按目标重试，以及旧数据库与并行会话。对 UI 提供只读状态和发送动作。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0203) · A03、A04、A05、A15

<a id="t0204"></a>

## T02.04 宿主接线、平台路由与调度入口

- **负责人** W02。**前置** T02.01 可先交接接口；实际装配依赖 T02.02、T02.03、T02.05、T02.06。
- **改动范围** `app/session`、`ElymBotApplication.kt`、`app/src/main/AndroidManifest.xml`、`ElymBotUiService.kt`。发布 App 宿主的平台注册/撤销、入站权限和绑定、系统调度/触发、操作确认及 UI 状态动作。利用合成实现及早验证；装配时接 W03 真实服务。
- **完成与验证** 向 W03–W06 先交接窄接口和状态。合成入口验证 QQ 路由、会话隔离、首条 Reply 固定目标、复核后发送、多目标独立状态、定时任务仅 App 主会话及一次触发一次生成。与 W04 通过正式适配接口核对五类媒体的稳定引用与模型投影，验证不支持的类型有受控状态。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0204) · A05、A07、A09、A10、A11、A14

<a id="t0205"></a>

## T02.05 插件制品、运行时与生命周期

- **负责人** W03。**前置** T02.01 与 T02.04 的早期接口。
- **改动范围** 根构建设置、版本目录、App 构建、最小原生组件和 App 插件底座。以第一方签名文件包定义身份、宿主 API、版本/ABI、安装实例、依赖和只读加载。负责发布制品构建、原子激活、撤销、失败回退与可观察状态。
- **完成与验证** 先从实际基础 APK 加载一个独立签名合成制品并验证更新失败保旧与旧实例失效。用依赖图、APK 内容和受支持 ARM64 上最小启动验证基础包只含所需原生启动依赖。把固定构建/能力契约交 W04/W05。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0205) · A01、A06、A07、A17

<a id="t0206"></a>

## T02.06 官方目录、双源下载与 App 更新

- **负责人** W03。**前置** T02.05 的签名与制品描述约定。
- **改动范围** App 目录、下载及发布检查实现，复用现有网络边界。提供用户安装入口、目录查询 ToolSource、GitHub 权威目录以及 Gitee 的同制品文件备用；正式 App 版本按数值和签名身份匹配。
- **完成与验证** 向 W02/W06 交接目录、下载、更新状态与用户动作。合成网络验证截断、主源故障、同名异包、无更新和检查失败，双源真实发布证据在包级阶段补齐。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0206) · A12、A13、A14

<a id="t0207"></a>

## T02.07 QQ 容器、登录与平台接入

- **负责人** W04。**前置** T02.04 与 T02.05 的平台接口和插件构建交接。
- **改动范围** `plugins/qq/`。复用已验证 ARM64 在线准备链路，将容器输入和 QQ 运行资产放在按需路径；通过宿主原生启动入口，覆盖进度、超时、更新后路径修复、二维码和 OneBot 生命周期。
- **完成与验证** 交接平台身份、登录/连接状态、扫码/重连动作、入站与发送语义。先做插件定向检查，使用正确的测试账号与 ARM64 设备时与 W07 归并真实扫码/收发验证。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0207) · A06、A07、A08、A17

<a id="t0208"></a>

## T02.08 QQ 入站策略与断线恢复

- **负责人** W04。**前置** T02.07，与 W02 T02.04 的宿主路由契约联调。
- **改动范围** `plugins/qq/`。保留 D07 准入、管理/命令、唤醒/屏蔽、限频、隔离及公共群历史，转换统一消息与发送回执；重复平台事件只转交一次，停用后停止收发。宿主负责主会话绑定与最终授权。
- **完成与验证** 覆盖重复事件、公开材料范围、用户/群/组合规则、管理员、限频丢弃/重放、停用与重连。用五类合成 OneBot 消息经过正式适配入口，验证归一化及渠道不支持时的投递结果。真实平台失败和补偿策略交接 W02/W07。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0208) · A05、A08、A09

<a id="t0209"></a>

## T02.09 主会话定时任务

- **负责人** W05。**前置** T02.01 及 T02.04/T02.05 的触发、确认和装载接口。
- **改动范围** `plugins/scheduled-tasks/` 持有任务及运行记录。统一编辑和工具入口，归属 App 主会话；经确认后执行模型写入。支持立即运行、持久时区与重复规则、唯一计划触发、15 分钟恢复窗口和停用终止。系统调度声明与跨平台投递由 W02 持有。
- **完成与验证** 发布任务操作及状态，合成验证确认、越权、时区、重启恢复、忙碌/重复和过窗。交接 W06 面板、W07 真实调度，向 W02 提交稳定触发身份和主会话任务描述。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0209) · A07、A10、A11、A17

<a id="t0210"></a>

## T02.10 聊天与插件、平台页面

- **负责人** W06。**前置** T02.04 预先提供 UI 契约；真实联调依赖 T02.03、T02.06–T02.08。
- **改动范围** `app/ui`、App 界面资源及定向 UI 测试。以现有聊天、平台和插件入口呈现 Reply、逐目标待发及失败，接官方目录、装卸、QQ 登录和平台状态。
- **完成与验证** 在测试 UI 数据和正式入口核验顺序回复、状态、插件首次启用、对应平台/账号和 QQ 登录动作。复用现有导航与滚动联动，按原型实现导航、搜索与可见状态过渡，核对系统减少动态效果下的行为。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0210) · A02、A03、A04、A06、A07、A08、A12

<a id="t0211"></a>

## T02.11 定时任务页面、FAQ 与首次引导

- **负责人** W06。**前置** T02.10；T02.04、T02.06、T02.07、T02.09 提供状态与动作。
- **改动范围** `app/ui`、App 界面资源及定向 UI 测试。实现 App 主会话任务列表、详情、确认和立即运行，FAQ 与首次模型配置、QQ/任务首次引导以及关于页更新入口。按任务书收敛原型示例。
- **完成与验证** 主会话才暴露任务入口，FAQ 搜索、展开、跳转可达，引导按真实数据判断，升级、跳过、续接、禁用、故障和清理有可观察状态。核对 FAQ 展开、任务弹层和引导切换的过渡与静态可用性。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0211) · A07、A10、A14、A16、A17

<a id="t0212"></a>

## T02.12 正式入口集成与交付

- **负责人** W07、W08 审核。**前置** T02.01–T02.11 已交接并完成相应定向验证。
- **改动范围** W07 在前序写入结束后串行完成正式入口接线、旧流式直出清理、端到端测试和包级说明。W07 收口必要的非页面接线，页面改动由 W06 处理，缺陷回到所属 Worker。W08 对交付只读审核。
- **完成与验证** 真实模型，ARM64 QQ，实际 Android 后台调度、合成多平台、真实双源、App 签名更新、干净及 T01 数据升级，逐项记录 A01–A17 证据后交用户。发布与 Git 写单独遵循用户授权。 [Checklist](T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0212) · A01–A17
