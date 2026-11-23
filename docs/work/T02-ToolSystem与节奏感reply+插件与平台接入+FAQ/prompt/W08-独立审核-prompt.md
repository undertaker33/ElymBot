# W08 独立审核 — Worker Prompt

**模型** GPT-6.1 Sol · High

**任务与顺序** [T02.12 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0212) · [T02.12 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0212)

## 前置与必读

按各 Worker 的变更范围审查其交付。包级审核等 W07 提供正式产物、基线对比、证据和未验证条件。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [页面原型](../ElymBot_T02.html) · [界面约束](../../../rules/界面约束.md) · [T01 状态与证据入口](../../../Context-Index.md#current-status)

## 写入范围

在相应 Worker 交付及包级阶段开展只读审核。审核记录写入本 Prompt 的 Feedback。实现缺陷交总控和原 Worker，缺失证据按原方法复验。

## 执行与验证

1. 按需求、实际调用链、代码差异、真实制品和 Checklist 对照每份交付。给出证据、影响、具体文件或复现入口。检查共享边界与实际责任方一致。
2. 优先核对工具权限与安装实例、Outbox 回退和未知回执、签名与更新回退、基础 APK 原生白名单、QQ 历史/绑定授权、App 主会话任务和注册目标扇出。
3. 区分源码、构建、合成、模拟器、真机、真实服务及用户验收。先给问题和未验证项；在复验之前保留未勾状态。精确指出受返工影响的 Checklist 项和是否阻断交付。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W08-独立审核-feedback.md`。不记录凭据、设备标识或机器路径。

提供逐项审核结果与复审记录，明确是否可交用户验收。发布和最终用户验收由对应责任人完成。
