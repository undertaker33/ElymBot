# ElymBot 当前上下文

通过 [文档索引](README.md) 查找产品决定和架构约束，沿真实调用链核对当前实现。

| 关注范围 | 当前入口 |
| --- | --- |
| Android 入口与基础界面 | [MainActivity.kt](../app/src/main/kotlin/com/elymbot/android/MainActivity.kt) |
| App、Core 和插件的实际构建边界 | [模块注册](../settings.gradle.kts) · [App 依赖](../app/build.gradle.kts) · [Core 配置](../core/build.gradle.kts) |
| 工具链、构建和检查 | [版本目录](../gradle/libs.versions.toml) · [构建入口](../README.md#快速开始) |

## current-status

工作包状态以实际目录、Checklist 和 Feedback 为依据。新建、部分实施或验收项未闭合的包标为 `not_implemented`，已实施并具备完整验收证据的活跃包标为 `implemented_unarchived`，用户已归档的包标为 `archived`。创建、重拆、包级验收和归档后更新日期、路径与证据摘要。 [状态维护规则](rules/WorkPackageRules.md#8-实施冻结与状态)

| 编号 | 工作包 | 状态 | 更新日期与验收证据 |
| --- | --- | --- | --- |
| T01 | [Core 和基础 App 重构](work/T01-Core和基础app重构/T01-Core和基础app重构-spec.md) | `not_implemented` | 2026-11-13 已同步全局主会话与人格专属会话设计。见 [Tasks](work/T01-Core和基础app重构/T01-Core和基础app重构-tasks.md) 和 [Checklist](work/T01-Core和基础app重构/T01-Core和基础app重构-checklist.md)。界面基准为 v0.6.3。并行回复、批量删除、新消息提示和图片位置调整已纳入验收，正式验收待实施。 |
