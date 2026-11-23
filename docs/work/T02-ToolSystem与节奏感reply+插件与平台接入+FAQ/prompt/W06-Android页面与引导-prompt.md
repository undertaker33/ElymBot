# W06 Android 页面与引导 — Worker Prompt

**模型** GPT-6.2 Sol · Medium

**任务与顺序** [T02.10 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0210) · [T02.10 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0210) · [T02.11 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0211) · [T02.11 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0211)

## 前置与必读

按 W02 提前交付的 UI 只读状态、动作和导航接线启动。W02/W03 合成接口可用于布局和交互。W02–W05 交付后在真实服务上联调。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [页面原型](../ElymBot_T02.html) · [界面约束](../../../rules/界面约束.md) · [App 页面入口](../../../../app/src/main/kotlin/com/elymbot/android/ui/ElymBotApp.kt)

## 写入范围

写 App UI、界面资源、MainActivity 呈现接线和 UI 定向测试。ElymBotUiService.kt、Application、Manifest、授权、插件和后台服务由原所有者修改。原型是参考资料，任务书确定权限和数据。

## 执行与验证

1. T02.10 接正式聊天 Reply 状态，保留发送、停止、按条/目标重试和检查点，展示独立消息与必要状态。实现官方插件目录、装卸和平台/QQ 页面，从现有导航可达。
2. T02.11 只在 App 主会话接入任务、创建、确认、编辑与立即运行。FAQ 提供搜索、分类、展开、按状态跳转以及重开首次配置。应用/QQ/任务引导按配置或成功动作完成，处理跳过、升级、重装和主动清理。「关于」接手动更新。
3. 按原型复现导航切换、搜索展开收起、任务弹层、FAQ 展开及状态过渡。复用 App 现有搜索与滚动行为，系统减少动态效果时保留最终状态、焦点和操作。用正式入口比较普通与减少动态效果两种体验。
4. 正式显示来自实际状态和资源字符串。遵循 Compose、无障碍和删除说明性文案约束。定向验证 A02–A04、A06–A08、A10、A12、A14、A16、A17，覆盖相应正常与关键失败路径。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W06-Android页面与引导-feedback.md`。不记录凭据、设备标识或机器路径。

W07 获得真实 UI 验收入口、复现步骤和定向证据；控件无权时不会暴露任务操作，旧流式界面由正式 Reply 状态接管。
