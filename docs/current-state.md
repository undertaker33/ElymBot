# 当前项目状态

更新时间：2026-05-20 13:07 +08:00

## 接管收尾状态

- UTH 启用：yes
- 场景：`uth-docs`
- 模式：`module-governance` 收尾与旧文档清理
- 完成等级：`full-project-docs-complete`
- 项目标记：`.uth-governance/project.json`
- 文档语言：`zh-CN`
- 接管前备份：`docs/ONB26051701-pre-uth-docs-backup.zip`
- 接管快照：`docs/snapshots/ONB26051701-existing-project-handoff.md`
- 当前入口：`docs/README.md`
- 当前上下文索引：`docs/context/README.md`
- 模块拆分报告：`docs/context/00-模块拆分.md`
- 旧文档分类：`docs/context/old-doc-classification.md`

此前文档中出现的 `full-project-docs-complete` 结论已被本轮修复判定为旧证据；当前 `full-project-docs-complete` 只指本轮在 12 个编号模块上下文、旧文档分类、归档清理和当前状态索引一致后重新形成的完成态。

## 当前事实入口

| 入口 | 路径 | 说明 |
| --- | --- | --- |
| 文档入口 | `docs/README.md` | 新窗口文档读取入口 |
| 状态入口 | `docs/current-state.md` | 当前接管完成态、证据和后续路由 |
| 上下文索引 | `docs/context/README.md` | 当前事实层索引 |
| 模块拆分 | `docs/context/00-模块拆分.md` | 已确认模块队列、代码事实范围和清理规则 |
| 旧模块分类 | `docs/context/docs-00-11-classification.md` | 旧 `docs/00` 到 `docs/11` 的归档与替代关系 |
| 旧文档分类 | `docs/context/old-doc-classification.md` | 接管前文档与历史协作材料分类 |

## 已完成模块上下文

| Order | Module package | Context |
| --- | --- | --- |
| 1 | `verification-build-governance` | `docs/context/01-验证构建治理.md` |
| 2 | `app-shell-and-integration` | `docs/context/02-应用壳层与集成.md` |
| 3 | `core-foundation-and-db` | `docs/context/03-核心基础与数据库.md` |
| 4 | `core-runtime` | `docs/context/04-核心运行时.md` |
| 5 | `download-and-container-assets` | `docs/context/05-下载与容器资产.md` |
| 6 | `provider-config-bot-persona` | `docs/context/06-Provider配置Bot与Persona.md` |
| 7 | `chat-and-conversation` | `docs/context/07-聊天与会话.md` |
| 8 | `qq-napcat-onebot` | `docs/context/08-QQ_NapCat_OneBot.md` |
| 9 | `plugin-platform` | `docs/context/09-插件平台.md` |
| 10 | `cron-runtime` | `docs/context/10-Cron运行时.md` |
| 11 | `resource-settings-backup` | `docs/context/11-资源设置备份.md` |
| 12 | `voiceasset-audio` | `docs/context/12-语音资产与音频.md` |

## 旧文档清理

- 旧 `docs/00_*.md` 到 `docs/11_*.md` 已归档到 `docs/archive/pre-uth-docs/docs-00-11/`。
- 归档前已确认这些旧文件原路径存在于 `docs/ONB26051701-pre-uth-docs-backup.zip`。
- `docs/archive/takeover-repair/baseline.md`、`docs/archive/takeover-repair/feature-modules.md`、`docs/archive/takeover-repair/onboarding-followup-evidence.md` 已归档到 `docs/archive/takeover-repair/`，只作历史修复证据。
- `docs/archive/context-pre-numbering/core-data-runtime.md` 保持为旧未编号 context 迁移证据。
- 旧文档只作为历史辅助证据；当前事实以编号 context 和代码事实来源为准。

## 当前阻塞

- 文档接管收尾：无活跃阻塞。
- 当前正式任务 `D26051901`：QQ 斜杠指令管理员权限已实现并完成 `/help` 绕过权限的 debug 修复；Git 写入未执行，等待后续 `uth-git`。
- Git：当前 `master`、`origin/master`、`codex/ColorOS16(RealmeUI7)` 与 `origin/codex/ColorOS16(RealmeUI7)` 均指向 `66eee69`；本轮 `uth-docs` 不执行 Git 写入。

## 最新验证证据

| Time | Method | Result | Notes |
| --- | --- | --- | --- |
| 2026-05-17 20:20 +08:00 | code-fact scan | pass | 第 12 模块 `voiceasset-audio` 已按当前源码、构建、Hilt、Room、runtime audio、脚本资产和测试入口核对 |
| 2026-05-17 21:05 +08:00 | backup path check | pass | 旧 `docs/00` 到 `docs/11` 原路径存在于 `docs/ONB26051701-pre-uth-docs-backup.zip` |
| 2026-05-17 21:05 +08:00 | archive cleanup | pass | 旧 `docs/00` 到 `docs/11` 与早期 seed / 失效证据已移动到 `docs/archive/` |
| 2026-05-17 21:05 +08:00 | `uth-utf8-guard` pre-write | pass | 写入前 58 个 Markdown 文件通过 UTF-8 guard |
| 2026-05-17 21:05 +08:00 | `uth-utf8-guard` post-write | pass | 写入后 58 个 Markdown 文件通过 UTF-8 guard |
| 2026-05-17 21:05 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-docs` 收尾与旧文档清理 closeout gate 通过 |
| 2026-05-17 21:30 +08:00 | changelog anchor scan | pass | 以 `git tag --list 'v*'`、`git log --grep='^Release v[0-9]'` 和 `app/build.gradle.kts` 版本号建立 `docs/changelogs/version-git-anchors.md` 索引；未写发布正文 |
| 2026-05-18 18:03 +08:00 | `clean architectureCheck` | pass | 首次窄构建命中 stale generated output；清理后架构入口通过 |
| 2026-05-18 18:03 +08:00 | `:build-logic:check` | pass | Gradle convention / build logic 回归通过 |
| 2026-05-18 18:03 +08:00 | `:app:testDebugUnitTest` | pass | App debug unit test 回归通过 |
| 2026-05-18 18:03 +08:00 | `clean assembleDebug` | pass | 日志保存到 `build/reports/D26051801-clean-assembleDebug.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-18 18:03 +08:00 | legacy exact-name scan | pass | 排除 `.git`、`.worktrees`、`build`、`.gradle`、`bin`、`logs`、APK artifacts 和 zip 后，项目自有旧名精确集合无命中 |
| 2026-05-18 18:03 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-dev` / `formal-dev` closeout 通过；positive claim evidence 与 code verification clean |
| 2026-05-18 20:44 +08:00 | version / branch anchor scan | pass | `app/build.gradle.kts` 为 `versionName = "1.0.0"`、`versionCode = 76`；`v1.0.0` tag 指向 `c25812e`，当前 `HEAD` 为 `66eee69` |
| 2026-05-18 20:44 +08:00 | generated artifacts guard scan | pass | `.gitignore` 排除 `artifacts/`；CI 新增 `Verify generated artifacts are not tracked` 步骤 |
| 2026-05-19 23:49 +08:00 | targeted `:app:testDebugUnitTest` | pass | 覆盖 `QqPluginDispatchServiceTest`、`ElymBotDatabaseSchemaContractTest`、`ConfigMappersTest`；`BUILD SUCCESSFUL in 35s` |
| 2026-05-19 23:56 +08:00 | `clean architectureCheck assembleDebug` | pass | 日志保存到 `build/reports/D26051901-plugin-command-admin-only-clean-architecture-assemble.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-19 23:59 +08:00 | `:app:testDebugUnitTest` | pass | 日志保存到 `build/reports/D26051901-plugin-command-admin-only-app-testDebugUnitTest.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-20 00:01 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-dev` / `formal-dev` closeout 通过；`code-verification-clean` |
| 2026-05-20 13:00 +08:00 | red regression test | fail expected | `QqPluginDispatchServiceTest.qq_bot_command_permission_blocks_non_admin_help_when_admin_only_enabled` 复现非管理员 `/help` 返回帮助文本 |
| 2026-05-20 13:03 +08:00 | focused QQ command tests | pass | `QqPluginDispatchServiceTest` 与 `BotCommandRouterProviderTest` 通过；覆盖 QQ 内置命令和插件命令权限入口 |
| 2026-05-20 13:06 +08:00 | `clean architectureCheck assembleDebug` | pass | 日志保存到 `build/reports/D26051901-slash-command-admin-only-debug-clean-architecture-assemble.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-20 13:07 +08:00 | `:app:testDebugUnitTest` | pass | 日志保存到 `build/reports/D26051901-slash-command-admin-only-debug-app-testDebugUnitTest.log`；warning / deprecated / exception 扫描计数为 0 |

## 当前事实来源

- `AGENTS.md`
- `README.md`
- `.uth-governance/project.json`
- `docs/README.md`
- `docs/context/README.md`
- `docs/context/00-模块拆分.md`
- `docs/context/01-验证构建治理.md` 到 `docs/context/12-语音资产与音频.md`
- `docs/context/docs-00-11-classification.md`
- `docs/context/old-doc-classification.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/elymbot/android/ElymBotApplication.kt`
- `app/src/main/java/com/elymbot/android/MainActivity.kt`
- `app/src/main/java/com/elymbot/android/di/**`
- `app/src/main/java/com/elymbot/android/ui/**`
- `app-integration/src/main/java/**`
- `architecture-tests/src/test/java/**`
- `core/**/src/main/java/**`
- `download/**/src/main/java/**`
- `feature/**/src/main/java/**`
- `app/src/test/java/**`
- `feature/**/src/test/java/**`
- `app/src/main/assets/runtime/scripts/**`

## 后续路由

- 普通开发：`uth-governance` -> `uth-dev`
- bug / 构建失败 / 回归：`uth-governance` -> `uth-debug`
- 验收 / 代码审查：`uth-governance` -> `uth-review`
- 文档同步：`uth-governance` -> `uth-docs`
- Git / PR / 发布：`uth-governance` -> `uth-git`

## 最近正式任务

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26051901-插件指令管理员权限/`
- Active Todo: `docs/work/D26051901-插件指令管理员权限/10-D26051901-T01-todo-插件指令管理员权限.md`
- Feedback: `docs/work/D26051901-插件指令管理员权限/11-D26051901-T01-feedback-插件指令管理员权限.md`
- Goal: add a config switch that restricts QQ slash commands to administrator UIDs.
- Status: implemented, debug-fixed, and verified; pending human acceptance / optional `uth-git`.
- Git baseline: pending `uth-git`；本轮 `uth-dev` 未执行 Git 写入。
