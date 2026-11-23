# W07 集成与验收 — Worker Prompt

**模型** GPT-6 Luna · Max

**任务与顺序** [T02.12 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0212) · [T02.12 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0212)

## 前置与必读

W01–W06 交接代码、制品和定向证据，前序不再占用共享文件。确认合成、真实模型、受支持 ARM64 和真实分发各自的可用条件。审核按 W08 执行。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [原型](../ElymBot_T02.html) · [T01 交付回顾](../../T01-Core和基础app重构/feedback/W01-总控与集成-feedback.md) · [能力欠账](../../../OutstandingDebtList.md)

## 写入范围

写集成所需的非页面接线、端到端测试、对应包级说明与本 Prompt Feedback；页面缺陷由 W06 修复。前序缺陷交原所有者，重新集成前核查受影响证据。Git 写和发布按用户授权。

## 执行与验证

1. 集成正式 App 启动、宿主装配、两个插件、发布检查与导航。核对已退出的模型流式正文和直接工具内容路径，保持 T01 已保存数据兼容。确认实际 APK 与依赖图满足边界。
2. 逐项对照 A01–A17。真实模型优先复测任务书所列模型，支持后才作为证据。用 ARM64 候选 App 驱动安装、扫码、OneBot 私聊/群聊、后台重连，以及实际系统触发；多平台/无外部目标和故障分支使用合成补齐。
3. 核对 GitHub/Gitee 真实同制品、App 正式签名更新、FAQ 和首次引导，给出干净安装与 T01 数据升级证据。复用已有效的前序证据，不重复无关检查。
4. 交 W08 独立审查，修复后复验对应项。依据候选产物与用户反馈更新记录，包级验收时维护受影响的产品、架构、用法、状态和能力欠账。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W07-集成与验收-feedback.md`。不记录凭据、设备标识或机器路径。

提供基础 APK、两份已验证官方插件文件包、发布目录和双源证据、对应 A01–A17 的 Feedback 与最短用户验收步骤。依用户实测反馈闭合用户验收。
