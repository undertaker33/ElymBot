# 子代理工作流

- 增量开发默认优先使用 worker subagent，但主窗口负责路由、边界、验证和文档回写。
- worker Prompt 只为实际 worker 写入；planner 和 evaluator 默认只读。
- 多 worker 并行时必须明确写入范围，避免多个 worker 同时编辑同一 Gradle 依赖图、Hilt binding 或共享写域。
