# 接管基线修正记录

更新时间：2026-05-17 15:54 +08:00

## 当前结论

此前 `uth-docs onboarding-followup` 写入的项目级基线结论已经失效。

无效项：

- `docs_completion_level=full-project-docs-complete`
- “项目级文档基线已建立”
- “旧文档分类、context 和 current-state 已经足以支撑全项目接管完成”

当前有效结论：

- `docs_completion_level=partial/paused`
- 本项目已进入 `module-governance active after module 8`
- 旧文档只作为辅助证据
- 代码事实优先

## 代码事实摘要

- Gradle 模块超过 60 个。
- 排除 `build/`、`bin/` 和生成物后，首方 Kotlin/Java 源码与测试文件约 1032 个。
- 架构合同测试覆盖 44 个合同入口。
- 模块边界横跨 `:app`、`:app-integration`、`:architecture-tests`、`:core:*`、`:download:*`、`:feature:*`。

上述规模超过单窗口 full-project-baseline 的可信范围。

## 当前事实入口

- `docs/current-state.md`
- `docs/context/README.md`
- `docs/context/00-模块拆分.md`
- `docs/context/docs-00-11-classification.md`
- `docs/context/old-doc-classification.md`

## Seed 文档说明

以下文件由旧 onboarding follow-up 或 pre-numbering 过程生成，后续只能作为 module-governance 的 seed 或历史证据：

- `docs/archive/context-pre-numbering/core-data-runtime.md`
- `docs/context/feature-modules.md`

第 1 到第 8 模块已经迁移为两位编号 current context；未编号模块文件不再作为当前入口。
