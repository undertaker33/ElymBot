# W02 Android 会话与投递 — Worker Prompt

**模型** GPT-6 Luna · Max

**任务与顺序** [T02.03 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0203) · [T02.03 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0203) · [T02.04 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0204) · [T02.04 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0204)

## 前置与必读

接收 T02.01 后先交平台注册、触发和 UI 状态动作接口。持久化真实接线依赖 T02.02，T02.04 正式装配依赖 W03 插件生命周期及分发服务。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [Room 数据库](../../../../app/src/main/kotlin/com/elymbot/android/data/ElymBotDatabase.kt) · [会话服务](../../../../app/src/main/kotlin/com/elymbot/android/session/ElymBotSessionService.kt) · [UI 服务边界](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotUiService.kt)

## 写入范围

写 App 的 data/session、宿主桥接、ElymBotApplication、App Manifest、ElymBotUiService.kt、相关 Android 数据/会话测试及本 Prompt Feedback。页面、插件实现、共享 Gradle 和 Core 由原所有者修改。

## 执行与验证

1. T02.03 用现有 Room 和会话串行入口实现持久受理与逐目标队列。持久化后 ACK；检查点回退、停止、发起下一轮和重开遵从任务书。回执未知先核对，已送达不重发。保持 T01 数据与迁移、会话上界、附件及摘要一致。
2. T02.04 先发布平台适配注册/撤销、会话路由、调度触发、动作确认、App 读写状态接口。App 提供实现，插件直接调用的纯 Kotlin 契约类型由 W01 维护。随后用 T02.05/T02.06 接入真实插件、目录与发布检查服务。系统组件由宿主声明。授权校验覆盖 App 主会话、渠道、安装实例及失效确认。
3. 普通回复固定原始目标。定时任务一次执行占主会话一轮，第一条 Reply 入队时固定有效目标，每次发送重新核对绑定。App 只记一份，每个平台独立投递或失败。需要验证全部、部分与零外部接入及重开过程。
4. 用定向 Room、合成适配器和 Android 测试验证 A03–A05、A07、A09–A11、A14、A15 与正式 App 装配。与 W04 用五类媒体样本走正式宿主适配入口，核对引用、授权、模型投影及不支持类型的状态。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W02-Android会话与投递-feedback.md`。不记录凭据、设备标识或机器路径。

W03–W06 可通过已交接接口注册或观察状态；W07 取得数据迁移、路由、多目标、存储和取消证据。
