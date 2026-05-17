# 模块组构建说明

本文说明模块拆分后的独立构建入口。这里的“独立构建”指在当前根 Gradle 工程内按业务/能力模块组单独构建和校验，不表示每个模块已经拆成可脱离根工程运行的独立仓库。

## 1. 基本原则

- 日常验证优先使用模块组任务，不必每次都跑完整 `:app`。
- `*Build` 任务只构建该模块组的 Gradle artifact；Android 子项目优先走 `assembleDebug`，JVM 子项目走 `assemble`，适合快速确认编译边界。
- `*Check` 任务会先执行对应 `*Build`，再运行该组子项目的 `check`。
- 涉及入口、Hilt、依赖图、Manifest、plugin runtime、QQ、Cron、backup 等跨模块边界时，仍需要追加 `architectureCheck` 和最终 `clean assembleDebug`。

## 2. 清理生成产物

安全清理入口：

```powershell
.\gradlew.bat cleanGeneratedProjectArtifacts --console=plain --no-daemon --stacktrace
```

该任务只清理明确的生成物：

- 各 Gradle 子项目的 `build/`
- 根 `build/` 下的生成子目录
- `artifacts/apk/`
- `artifacts/qwen-search-probe/`

该任务会保留本地环境和非构建真源：

- `AGENTS.md`
- `docs/`
- `.android-sdk/`
- `.gradle-user-home/`
- `.codex/`
- `.codex-home/`
- `.superpowers/`
- `.worktrees/`
- `local.properties`
- `keystore.properties`
- `app/src/main/assets/runtime/assets/*`

为避免误删真实工作区，清理任务会跳过包含嵌套 `.git` 元数据的目录。如果 `build/tmp/` 下出现确认属于构建期拉取的临时源码检出，需要先核对具体路径，再按单一路径人工删除。

不要用裸 `git clean -fdX` 作为本仓库默认清理方式。当前 `.gitignore` 覆盖了文档、本地 SDK、密钥配置和运行时大资产，粗暴清理会误删非构建产物。

## 3. 全部模块组

一次构建所有模块组：

```powershell
.\gradlew.bat allModuleGroupsBuild --console=plain --no-daemon --stacktrace
```

一次校验所有模块组：

```powershell
.\gradlew.bat allModuleGroupsCheck --console=plain --no-daemon --stacktrace
```

`allModuleGroupsCheck` 只证明模块组自身的 Gradle `check` 可过；它不替代 `architectureCheck`，也不替代最终 app 级 `clean assembleDebug`。

## 4. 模块组命令

| 模块组 | 快速构建 | 模块校验 | 覆盖范围 |
| --- | --- | --- | --- |
| Core Foundation | `moduleCoreFoundationBuild` | `moduleCoreFoundationCheck` | `:core:common`、`:core:backup`、`:core:db`、`:core:logging`、`:core:network`、`:core:ui` |
| Core Runtime | `moduleCoreRuntimeBuild` | `moduleCoreRuntimeCheck` | `:core:runtime*` 能力模块 |
| Download | `moduleDownloadBuild` | `moduleDownloadCheck` | `:download:api`、`:download:impl` |
| Bot | `moduleBotBuild` | `moduleBotCheck` | `:feature:bot:*` |
| Chat | `moduleChatBuild` | `moduleChatCheck` | `:feature:chat:*` |
| Config | `moduleConfigBuild` | `moduleConfigCheck` | `:feature:config:*` |
| Conversation | `moduleConversationBuild` | `moduleConversationCheck` | `:feature:conversation:*` |
| Cron | `moduleCronBuild` | `moduleCronCheck` | `:feature:cron:*` |
| Persona | `modulePersonaBuild` | `modulePersonaCheck` | `:feature:persona:*` |
| Plugin | `modulePluginBuild` | `modulePluginCheck` | `:feature:plugin:*` |
| Provider | `moduleProviderBuild` | `moduleProviderCheck` | `:feature:provider:*` |
| QQ | `moduleQqBuild` | `moduleQqCheck` | `:feature:qq:*` |
| Resource | `moduleResourceBuild` | `moduleResourceCheck` | `:feature:resource:*` |
| Settings | `moduleSettingsBuild` | `moduleSettingsCheck` | `:feature:settings:*` |
| Voice Asset | `moduleVoiceAssetBuild` | `moduleVoiceAssetCheck` | `:feature:voiceasset:*` |
| App Shell | `moduleAppShellBuild` | `moduleAppShellCheck` | `:app-integration`、`:app` |

示例：

```powershell
.\gradlew.bat moduleQqBuild --console=plain --no-daemon --stacktrace
.\gradlew.bat modulePluginCheck --console=plain --no-daemon --stacktrace
```

## 5. 建议验收顺序

只改一个业务模块时：

```powershell
.\gradlew.bat module<ModuleName>Build --console=plain --no-daemon --stacktrace
.\gradlew.bat module<ModuleName>Check --console=plain --no-daemon --stacktrace
```

涉及跨模块依赖、Hilt wiring、runtime、入口或架构边界时：

```powershell
.\gradlew.bat module<ModuleName>Check architectureCheck --console=plain --no-daemon --stacktrace
```

准备收口或交付前：

```powershell
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

## 6. 维护规则

- 新增 Gradle 子项目后，必须把它加入根 `settings.gradle.kts`。
- 新增业务模块组内子项目后，必须同步更新根 `build.gradle.kts` 的 `moduleBuildGroups`。
- 新增 source root 后，必须同步更新 architecture source root 基线，否则 `architectureCheck` 应阻止通过。
- 如果模块组边界变更，先更新本文，再运行对应模块组任务和 `architectureCheck`。
