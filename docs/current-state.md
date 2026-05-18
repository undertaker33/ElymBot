# 当前项目状态

更新时间：2026-05-18 18:03 +08:00

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
- 当前正式任务 `D26051801`：实现与验证已完成，等待用户验收或进入 `uth-git`。
- Git：本轮不执行 Git 写入；如需提交文档清理结果，应由用户显式进入 `uth-git`。

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

## Active formal task

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26051801-包名统一ElymBot/`
- Active Todo: `docs/work/D26051801-包名统一ElymBot/10-D26051801-T01-todo-包名统一.md`
- Feedback: `docs/work/D26051801-包名统一ElymBot/11-D26051801-T01-feedback-包名统一.md`
- Goal: migrate project-owned historical Android naming and package identity to `ElymBot` / `com.elymbot.android`.
- Status: implemented and verified; pending human acceptance or `uth-git`.
- Git status: pending `uth-git`; no Git writes in `uth-dev`.
