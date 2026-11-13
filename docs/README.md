# ElymBot 文档

| 目录 | 内容 |
| --- | --- |
| [product/](product/) | 产品范围、功能盘点与用户决策 |
| [architecture/](architecture/) | Minimal Core、插件边界与代码组织 |
| [任务书模板](templates/任务书模板.md) | 用户确认目标、范围、行为和验收条件 |
| `docs/work/` | 工作包目录。每项任务单独建目录，存放原任务书、Spec、Tasks、Checklist、Worker Prompt 和 Feedback。待归档的工作包仍放在这里，已归档的工作包放在 `docs/work/archive/`。 |
| [工作包规则](rules/WorkPackageRules.md) | 拆包、实施、验证和归档规则 |
| [当前上下文与工作包状态](Context-Index.md) | 当前源码入口、工作包状态和验收证据 |
| [能力欠账](OutstandingDebtList.md) | 工作包因后置依赖而明确暂缓的事项 |
| [工作记录](records/工作记录.md) | 项目文件改动记录与工作包起止时间 |

## 产品与架构

- [功能简表](product/ElymBot-功能简表.md)供原型绘制参考。
- [重构前功能盘点](product/ElymBot-重构前功能盘点.md)第五节记录 D01–D16。
- [Minimal Core 设计](architecture/ElymBot-Minimal-Core设计.md)说明 Agent、会话、上下文和插件的运行边界。
- [代码组织](architecture/ElymBot-代码组织.md)说明 App、Core 和插件如何划分源码与构建依赖。

## 按场景读取

- **探索** · 从 [工作包流程](../AGENTS.md#工作包流程) 和 [当前上下文](Context-Index.md) 出发，结合任务涉及的产品决定、架构、源码和测试验证关键前提。
- **拆包** · 读取用户交付的任务书、探索证据、[工作包规则](rules/WorkPackageRules.md)、[状态索引](Context-Index.md#current-status) 和 [能力欠账](OutstandingDebtList.md)。
- **实施和验收** · 按 Worker Prompt 读取对应任务书、Spec、Tasks、Checklist 和源码。包级验收同时核对 Feedback，并按改动同步 README、产品决定、架构文档和当前上下文中受影响的内容。
