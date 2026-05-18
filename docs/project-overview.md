# 项目概览

## 项目名称

ElymBot

## 项目目的

根据根 `README.md`，ElymBot 是面向 Android 的 ElymBot 原生项目，用于在移动端承载 QQ Bot 管理、模型配置、Persona 管理、对话上下文、运行时控制和 NapCat 本地桥接能力。

## 用户

根 `README.md` 描述的目标用户是希望在 Android 设备上部署和管理 AstrBot / QQ Bot / NapCat 本地桥接能力的用户，尤其是希望用闲置安卓设备承载移动端 Bot 管理与运行时控制的场景。

## 技术栈线索

- Android / Kotlin / Gradle 多模块工程。
- 根 `build.gradle.kts` 显示 AGP `8.13.2`、Kotlin `1.9.24`、KSP `1.9.24-1.0.20`、Hilt `2.52`。
- 根构建配置显示 `compileSdk=36`、`minSdk=29`、JVM target `17`。
- `README.md` 描述使用 Kotlin + Jetpack Compose + Android 前台服务。

## 模块线索

- `settings.gradle.kts` 声明 `:app`、`:app-integration`、`:architecture-tests`、`:core:*`、`:download:*`、`:feature:*` 多模块。
- `feature` 下包含 bot、chat、config、conversation、cron、persona、plugin、provider、qq、resource、settings、voiceasset 等业务模块。
- 当前 UTH 模块上下文入口位于 `docs/context/README.md`。

## 非目标

- 不是 AstrBot 桌面端的完整移植。
- 不把 Python 后端、Node WebUI、完整插件生态和协议栈原封不动搬进 Android。
- 当前 README 明示 QQ 以外的平台支持仍非已完成范围。
