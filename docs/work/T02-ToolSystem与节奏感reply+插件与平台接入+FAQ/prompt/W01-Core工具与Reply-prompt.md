# W01 Core 工具与 Reply — Worker Prompt

**模型** GPT-6 Luna · Max

**任务与顺序** [T02.01 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0201) · [T02.01 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0201) · [T02.02 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0202) · [T02.02 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0202)

## 前置与必读

以任务书基线启动。T02.01 完成后先把跨模块契约和示例交给 W02/W03，T02.02 可并行于其合成接线。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [工具注册表](../../../../core/src/main/kotlin/com/elymbot/core/tool/ToolRegistry.kt) · [ReAct 循环](../../../../core/src/main/kotlin/com/elymbot/core/agent/ReActAgentRuntime.kt) · [Core 历史契约](../../../../core/src/main/kotlin/com/elymbot/core/contract/HistoryContracts.kt)

## 写入范围

只写 `core/` 及本 Prompt 的 Feedback。App、插件、Android 框架、真实平台与 Android 调度由相应 Worker 管理。共享语义变化先与 W02/W03 核对影响。

## 执行与验证

1. T02.01 按真实 App、Reply、QQ 和定时任务调用定义消息、执行与尝试标识、工具范围、安装身份与撤销。复用 ToolRegistry，按序执行并按调用 ID 返回受控结果。将 Reply 提交作为宿主能力，成功仅表示持久受理，由 App 实现。
2. T02.02 从模型网关和 ReAct 正式路径切换完整响应。Reply 是非终止工具，普通正文和原始工具失败内容不得进入用户输出。保留可用视觉、预算、摘要、取消和限制。用历史状态区分成功静默、未发出有效 Reply、失败及已交付内容。
3. 用 `gradlew.bat :core:test` 和本地可控 HTTP 复验非流式参数、成功静默、顺序、取消及拒绝。记录 A01、A02、A03、A05、A15 对应的 Core 证据。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W01-Core工具与Reply-feedback.md`。不记录凭据、设备标识或机器路径。

向 W02 交付回复与历史语义、工具调用标识和受理失败语义。与 W02 确认插件确实需要的纯 Kotlin 调用契约，由 W01 维护契约类型并交给 W03 构建复用。由 W07 使用真实模型验证。
