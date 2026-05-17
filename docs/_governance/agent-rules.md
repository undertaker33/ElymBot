# Agent 规则

- 当前事实写入 current fact 文档，不写入旧任务日志。
- 工程任务先由 `uth-governance` 路由到对应 `uth-*` 场景。
- 不要执行 Git 写入，除非 `uth-git` 已激活且用户已确认 Git 计划。
- 不要默认扫描全部文档；先读入口索引，再按场景补读。
- `AGENTS.md` 只保留全局规则、高频误判和新窗口最小动作。
