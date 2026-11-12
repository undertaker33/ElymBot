# ElymBot

**可按需扩展的原生 Android 个人助手。**

[![Android](https://img.shields.io/badge/Android-minSdk%2026-3DDC84?logo=android&logoColor=white)](gradle/libs.versions.toml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[快速开始](#快速开始) · [项目结构](#项目结构) · [文档](#文档)

> [!NOTE]
> 项目已进入开发阶段。当前可构建并运行 Material 3 启动页，下列产品能力为开发目标。

## 项目简介

- **原生助手** — 由单个 Agent 处理文本与图片对话，调用工具并根据结果继续完成请求。
- **人格与连续会话** — 可保存多个人格预设。App 与手动绑定的本人 QQ 私聊可接续同一个主会话。
- **按需安装插件** — 基础 APK 独立运行。QQ、语音和定时任务等能力由用户分别下载、安装和删除。

## 快速开始

准备 **JDK 17**、**Android SDK Platform 37** 和 **Build Tools 37.0.0**。可在 Android Studio 中打开项目根目录，也可使用 Gradle Wrapper 构建。

```bash
git clone https://github.com/undertaker33/ElymBot.git
cd ElymBot
./gradlew :app:assembleDebug
```

Windows PowerShell 构建命令

```powershell
.\gradlew.bat :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

<details>
<summary>完整构建检查与工具链</summary>

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:check :plugins:qq:assembleDebug :plugins:scheduled-tasks:assembleDebug
```

| 组件 | 版本 |
| --- | --- |
| Gradle Wrapper / Android Gradle Plugin | 9.9.1 / 9.5.1 |
| Kotlin / Compose Compiler | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Compose UI / Material 3 | 1.12.1 / 1.4.0 |
| Android SDK | `minSdk 26` · `compileSdk 37` · `targetSdk 37` |

当前组合已通过项目构建验证。Gradle 和 AGP 版本超出 Kotlin 2.4.21 官方完整兼容矩阵。 [版本目录](gradle/libs.versions.toml) · [Gradle Wrapper 配置](gradle/wrapper/gradle-wrapper.properties) · [Kotlin 兼容矩阵](https://kotlinlang.org/docs/gradle-configure-project.html)

</details>

## 项目结构

| 目录 | 用途 |
| --- | --- |
| [`app/`](app/) | Android 应用入口、基础界面与依赖装配 |
| [`core/`](core/) | Kotlin/JVM 模块，承载 Agent、上下文和模型接入 |
| [`plugins/`](plugins/) | 各插件的独立源码和构建模块 |
| [`docs/`](docs/README.md) | 产品决策、架构设计与开发文档 |

当前基础 APK 的项目依赖只有 `:core`。QQ 和定时任务模块各自生成 AAR 用于构建验证，用户下载的插件制品与加载方式待验证。

## 文档

- [功能简表](docs/product/ElymBot-功能简表.md) · [功能盘点与产品决策](docs/product/ElymBot-重构前功能盘点.md)
- [Minimal Core 设计](docs/architecture/ElymBot-Minimal-Core设计.md) · [代码组织](docs/architecture/ElymBot-代码组织.md)
- [工作包流程与 Agent 指引](AGENTS.md) · [任务书模板](docs/templates/任务书模板.md) · [工作包规则](docs/rules/WorkPackageRules.md)

## 许可证

本项目采用 [MIT License](LICENSE)。
