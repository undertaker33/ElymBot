# ElymBot Android Native

ElymBot 是一个独立运行的 Android 个人助手。一个宿主 Agent 处理对话、模型调用和工具执行，QQ、语音和定时任务等能力可通过插件按需安装。

目前处于重构前准备阶段。仓库包含可构建的 Android 模块骨架和 Material 3 启动页。

| 路径 | 用途 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Codex 项目说明与当前工作边界 |
| `app/` | Android 应用入口、基础界面和依赖装配 |
| `core/` | Kotlin/JVM 模块，承载后续宿主 Agent、上下文和模型接入 |
| `plugins/` | QQ 和定时任务各有独立 Gradle 模块，用户按需另行下载插件 |
| [docs/](docs/README.md) | 产品、架构、工作方式和任务记录 |
| `探索/` | 本地独立探针、复现说明和探索记录，Git 忽略 |
| `验证/` | 本地 QQ 验证材料，包含安装包、日志和构建输入 |

## 构建

使用 JDK 17、Android SDK Platform 37 和 Build Tools 37.0.0。Gradle Wrapper 固定为 9.9.1，版本目录固定 AGP 9.5.1、Kotlin 与 Compose Compiler 2.4.21、Compose BOM 2026.09.00。BOM 提供 Compose UI 1.12.1 和 Material 3 1.4.0。初始 `minSdk` 为 26，`compileSdk` 和 `targetSdk` 为 37。 [版本目录](gradle/libs.versions.toml) · [AGP 版本说明](https://developer.android.com/build/releases/agp-9-5-0-release-notes) · [Compose 设置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)

Kotlin 2.4.21 的完整兼容矩阵上限为 Gradle 9.7.0 与 AGP 9.3.1。骨架采用较新的稳定构建工具组合，以实际构建结果核对兼容性。Gradle 9.9.1 包含近期的安全修复。 [Kotlin 兼容矩阵](https://kotlinlang.org/docs/gradle-configure-project.html) · [Gradle 9.9.1](https://docs.gradle.org/9.9.1/release-notes.html)

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:check :plugins:qq:assembleDebug :plugins:scheduled-tasks:assembleDebug
```

基础 APK 可以独立使用，用户按需另外下载、安装和删除插件。App 的项目依赖只有 `:core`。QQ 和定时任务目前各自构建 AAR，作为 Android 库构建产物；用户下载的插件包格式和加载方式留待接入验证。

当前骨架没有需要运行的注解处理器。首次引入支持 KSP 的库时优先用 KSP。 [Android KSP 指南](https://developer.android.com/build/migrate-to-ksp)

## 阅读入口

- [文档索引](docs/README.md)提供功能简表、D01–D16 产品决定、Core 设计和代码组织。
- [工作方式和模板](docs/workflow/README.md)说明正式任务、快速任务和用户验收怎么衔接。
- 本地 `探索/重构前准备.md` 整理验证范围，`探索/core-probe/README.md` 提供 JVM 和 Android 探针的运行步骤。

历史记录中的部分链接指向本机旧仓库、参考仓库或本地制品，需在原工作环境中打开。
