# ONB26051701 Existing Project Handoff

## 接管时间

2026-05-17 03:31 +08:00

## 仓库快照

- 工作目录：`C:\Users\93445\Desktop\Astrbot\ElymBot-Android-Native`
- 分支：`codex/ColorOS16(RealmeUI7)`，跟踪 `origin/codex/ColorOS16(RealmeUI7)`
- 最近提交：`6b97b4a Merge pull request #13 from undertaker33/codex/ColorOS16(RealmeUI7)`
- 预检时已有非本轮改动：`LICENSE`、`build.gradle.kts`
- Git 写入：none

## 备份

- `docs/ONB26051701-pre-uth-docs-backup.zip`

## 接管前文档结构

```text
AGENTS.md
README.md
docs/README.md
docs/00_当前基线与迁移摘要.md
docs/01_应用入口_构建_导航壳层.md
docs/02_数据真源_Room_Repository_备份基线.md
docs/03_Provider_Config_Bot_Persona.md
docs/04_聊天会话_App内消息链路.md
docs/05_QQ登录_NapCat_OneBot运行时.md
docs/06_STT_TTS_声音克隆_资产.md
docs/07_插件平台_模型_安装_执行_治理.md
docs/08_插件UI_市场_详情_配置_工作区.md
docs/09_设置_日志_运行时清理_备份入口.md
docs/10_测试入口_回归面_已知风险.md
docs/11_全链路执行流程图.md
docs/architecture/
docs/module-build-guide.md
docs/superpowers/
changelogs/
.superpowers/
```

## 已发现入口

- Agent 入口：`AGENTS.md`
- 项目说明：`README.md`
- 背景文档入口：`docs/README.md`
- 构建模块声明：`settings.gradle.kts`
- 根构建配置：`build.gradle.kts`
- UTH 项目标记：`.uth-governance/project.json`
- UTH hook runner：`tools/uth-hooks/uth-hook.py`

## 技术栈线索

- Android / Kotlin / Gradle 多模块工程。
- `build.gradle.kts` 显示 AGP `8.13.2`、Kotlin `1.9.24`、KSP `1.9.24-1.0.20`、Hilt `2.52`。
- `build.gradle.kts` 显示 `compileSdk=36`、`minSdk=29`、JVM target `17`。
- `README.md` 描述 Kotlin + Jetpack Compose + Android 前台服务。

## 模块边界线索

- `settings.gradle.kts` 声明 `:app`、`:app-integration`、`:architecture-tests`、`:core:*`、`:download:*`、`:feature:*`。
- `AGENTS.md` 现有规则强调 feature-first、Hilt-only、core 与 feature 边界、资源真源收口、测试和架构合同优先。
- 完整模块职责需由 `uth-docs onboarding-followup` 读取源码、测试和构建声明后确认。

## 旧规则保留候选

- 根 `AGENTS.md` 中的 Context7 文档查询规则。
- feature-first 代码查找规则。
- 生产依赖注入唯一合法方法是 Hilt。
- core 与 feature 边界不能倒置。
- 共享 runtime context、Resource Center 和 ProviderRuntimePort 真源规则。
- 状态切换、删除保护、事务边界不回到 UI 层手拼。
- Plugin、QQ、Cron、Backup 当前主线规则。
- `architectureCheck`、`:build-logic:check`、allowlist 和 `clean assembleDebug` 验收要求。
- `docs/README.md` 对背景文档层级和维护落点的约束。

## 旧文档可信度

- 现有 `docs/` 是有组织的背景文档体系，适合作为接管证据和迁移候选。
- 旧文档仍需按代码事实分类；不能在接管预检中直接声明其全部为当前真源。

## 未确认事实

- 完整模块职责与非职责。
- 当前 active phase。
- 当前 verification baseline。
- 哪些旧文档应迁移为 UTH context，哪些应归档为历史证据。
- 是否存在已完成但仍留在 active 文档中的任务记录。

## 必需 uth-docs follow-up

- `origin_scene=uth-onboarding`
- `origin_mode=existing-project`
- `handoff_type=existing-project-takeover`
- `takeover_session_id=ONB26051701`
- `return_to=uth-onboarding`
- 需要返回 `docs_completion_level=full-project-docs-complete`。
