# W05 主会话定时任务 — Worker Prompt

**模型** GPT-6 Luna · Max

**任务与顺序** [T02.09 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0209) · [T02.09 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0209)

## 前置与必读

收到 W01 工具契约、W02 调度/确认/触发接口和 W03 插件制品交接。用合成宿主完成确定性操作及时间边界。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [定时任务模块构建](../../../../plugins/scheduled-tasks/build.gradle.kts) · [Task 调度验收](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0209)

## 写入范围

只写 `plugins/scheduled-tasks/` 和本 Prompt Feedback。插件持有任务和运行历史；App 系统组件、会话路由、跨平台发送及页面各由原所有者维护。

## 执行与验证

1. 实现仅 App 主会话授权的任务操作。手动表单、自然语言、立即运行沿同一存储与校验。模型写操作经确认，重新检查会话、用户及安装身份，拒绝/失效不生效。
2. 保存时区、重复与结束规则，同任务同计划时间保持唯一。手动立即运行使用独立记录并保留计划。过窗记为错过，只补仍在 15 分钟内的最近一次，停用或移除使旧触发失效。
3. 到期向 W02 提交一次主会话触发及稳定身份。回复和目标由宿主负责。验证 A07、A10、A11、A17 的时区、拒绝、越权、重复、忙碌、停用和恢复场景。真实系统回调和多平台完整链交 W07。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W05-主会话定时任务-feedback.md`。不记录凭据、设备标识或机器路径。

W02 取得触发和任务动作契约，W06 取得任务、确认、首次创建及运行状态，W07 取得实际制品和测试证据。
