# ElymBot

![Version](https://img.shields.io/badge/version-0.1.0-f59e0b)
![Android](https://img.shields.io/badge/Android-minSdk%2026-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

ElymBot 是基于 Kotlin 和 Jetpack Compose 的原生 Android 个人助手。App 管理本地会话和界面，Core 提供 App 与已启用插件共用的宿主 Agent、模型接入和工具执行。

[功能概览](#功能概览) · [技术栈](#技术栈) · [项目结构](#项目结构) · [开发环境](#开发环境) · [构建与测试](#构建与测试) · [文档与发布记录](#文档与发布记录)

## 功能概览

| 模块 | 当前能力 |
| --- | --- |
| 人格与会话 | 创建、编辑、搜索和按标签筛选人格；使用一个可切换人格的主会话，也可从人格入口创建固定归属的专属会话；各会话分别保存历史和检查点，支持批量删除和新消息提示。 |
| 聊天 | 文字和图片输入、模型原生流式回复、停止和重试；支持五种可改名的 Slash Command。 |
| 模型 | 配置 OpenAI 兼容服务、API Key 和模型 ID，拉取模型列表，分配文本与视觉模型，并查看可用性、视觉和原生流式探测结果。 |
| 上下文与工具 | Core 按模型预算装配上下文、压缩较早历史并保留原文；内置 HistoryRead，支持宿主按序调用工具并接收结果。 |
| 设置 | 支持浅色、深色和跟随系统；「关于」页从 Android 已安装应用信息读取版本，并显示 MIT 许可及项目链接。 |

T01「Core 和基础 App 重构」已于 2026-11-22 验收通过，详见 [交付回顾](docs/work/T01-Core和基础app重构/feedback/W01-总控与集成-feedback.md#2026-11-22-用户验收与包级关闭)。

当前没有预置人格、模型服务或 API Key。首次使用时先在人格库创建人格，再配置可用的 OpenAI 兼容服务并选择文本模型；图片对话还需配置合适的视觉路由。配置页的「上下文」和「工具」分类目前没有独立配置内容。

QQ、语音、定时任务和其他独立工具属于后续可选扩展。`plugins/qq` 和 `plugins/scheduled-tasks` 是独立 Android Library 构建模块，可产出 AAR 用于构建验证；当前基础 App 尚未提供下载、安装、加载和卸载插件包的完整流程。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| Android App | Kotlin、Jetpack Compose、Room、AndroidX Lifecycle |
| Agent 与模型 | Kotlin/JVM Core、Kotlin Coroutines、OkHttp、Kotlin Serialization |
| 模型协议 | OpenAI 兼容 Chat Completions |
| 构建 | Gradle Wrapper 9.9.1、Android Gradle Plugin 9.5.1、JDK 17 |
| Android SDK | `minSdk 26` · `compileSdk 37` · `targetSdk 37` · Build Tools 37.0.0 |
| Kotlin 与 Compose | Kotlin 2.4.21 · Compose BOM 2026.09.00 |

版本以 [Gradle 版本目录](gradle/libs.versions.toml)、[Wrapper 配置](gradle/wrapper/gradle-wrapper.properties) 和 [App 构建配置](app/build.gradle.kts) 为准。

## 项目结构

```text
ElymBot/
├── app/                         # Android 入口、Compose 界面、本地数据和依赖装配
├── core/                        # 与 Android Framework 和具体插件无关的宿主 Agent 与模型实现
├── plugins/
│   ├── qq/                      # QQ Android Library 模块
│   └── scheduled-tasks/         # 定时任务 Android Library 模块
├── docs/                        # 产品决策、架构、工作包和开发记录
├── AGENTS.md                    # 项目边界、实施约束和 Git 流程
└── README.md                    # 项目介绍与开发入口
```

基础 APK 的项目依赖为 `:core`。可选插件实现留在各自模块；模块存在或 AAR 构建成功并不表示已有可下载、可安装或可运行的发布包。

## 开发环境

准备 JDK 17、Android SDK Platform 37 和 Android Build Tools 37.0.0。可在 Android Studio 中打开仓库，或使用仓库自带的 Gradle Wrapper：

```sh
git clone https://github.com/undertaker33/ElymBot.git
cd ElymBot
./gradlew :app:assembleDebug
```

Windows PowerShell 使用 `cd ElymBot` 和 `.\gradlew.bat :app:assembleDebug`。

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

### 首次运行与模型配置

1. 打开「人格库」并创建人格。仓库不会预置示例人格。
2. 从底栏打开「配置」，添加 OpenAI 兼容服务，填写服务地址、API Key 和模型 ID。
3. 将可用模型分配给文本用途。文本模型支持视觉时可直接发送图片；否则配置视觉模型。

API Key 在 App 中配置。不要把真实密钥写入 README、源码、测试输入、日志或仓库配置文件。

## 构建与测试

在 macOS 或 Linux 中使用 `./gradlew`；在 Windows PowerShell 中使用 `.\gradlew.bat`，后接相同的任务名：

```sh
# 构建基础 App
./gradlew :app:assembleDebug

# 运行 Core JVM 测试
./gradlew :core:test

# 构建 Android 仪器测试 APK
./gradlew :app:assembleDebugAndroidTest

# 连接 Android 设备或模拟器后运行仪器测试
./gradlew :app:connectedDebugAndroidTest

# 单独构建可选 Android Library 模块
./gradlew :plugins:qq:assembleDebug :plugins:scheduled-tasks:assembleDebug
```

### 本地插件文件包

`scripts/package-plugin.ps1` 从插件 release AAR 构建单插件 DEX JAR，并按 App 文件包格式生成签名包。调用时必须显式传入发布者、版本、ABI、入口类和调用者提供的 RSA PEM 私钥路径；脚本不生成密钥，也不读取隐式密钥。QQ 等显式资源需同时提供对应的 SHA-256。生成的发布 DEX、文件包和暂存文件留在忽略的 `build/` 目录；仓库不包含发布签名私钥、签名发布包或 QQ rootfs。

```powershell
$PublisherId = 'your-publisher-id'
$Version = 'your-plugin-version'
$SigningPrivateKeyPath = 'path/to/your-plugin-key.pem'
.\scripts\package-plugin.ps1 `
  -PluginId 'scheduled-tasks' `
  -PublisherId $PublisherId `
  -Version $Version `
  -HostApi 'elymbot-plugin-api-v1' `
  -Abis @('arm64-v8a') `
  -EntryClass 'com.elymbot.android.plugins.tasks.ScheduledTasksPluginEntrypoint' `
  -SigningPrivateKeyPath $SigningPrivateKeyPath
```

`connectedDebugAndroidTest` 需要已授权的 Android 设备或运行中的模拟器。测试覆盖情况及环境按实际工作包 Feedback 记录，不以命令清单代替验收结果。

创建 PR 前更新 `app/build.gradle.kts` 的 `versionName` 和 `versionCode`，并在候选 App 的「关于」页核对显示版本。

## 文档与发布记录

- [当前上下文与工作包状态](docs/Context-Index.md)
- [重构待办清单](docs/重构待办清单.md)
- [功能盘点与产品决策](docs/product/ElymBot-重构前功能盘点.md)
- [Minimal Core 设计](docs/architecture/ElymBot-Minimal-Core设计.md) · [代码组织](docs/architecture/ElymBot-代码组织.md)
- [界面约束](docs/rules/界面约束.md) · [工作包规则](docs/rules/WorkPackageRules.md)
- [工作记录](docs/records/工作记录.md) · [T01 任务书](docs/work/T01-Core和基础app重构/T01-Core和基础app重构.md)

- [v0.1.0 更新日志](updates/v0.1.0/CHANGELOG.md)

## 许可证

本项目采用 [MIT License](LICENSE)。
