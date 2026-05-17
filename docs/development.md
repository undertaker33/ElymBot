# 开发

## 环境

- JDK 17：根 `build.gradle.kts` 设置 JVM target `17`。
- Android SDK：根 `build.gradle.kts` 设置 `compileSdk=36`、`minSdk=29`；`app/build.gradle.kts` 设置 `targetSdk=36`。
- Android Gradle Plugin：根 `build.gradle.kts` 使用 `8.13.2`。
- Kotlin：根 `build.gradle.kts` 使用 `1.9.24`。
- Hilt：根 `build.gradle.kts` 使用 `2.52`，`app/build.gradle.kts` 应用 Hilt plugin。
- Room：`app/build.gradle.kts` 使用 `2.6.1`，`core/db/.../AstrBotDatabase.kt` 当前 DB version 为 `22`。

## Run

```bash
```

## Build

```bash
./gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

模块组构建入口见 `docs/module-build-guide.md`，根任务包括 `allModuleGroupsBuild`、`allModuleGroupsCheck` 和各 `module<ModuleName>Build/Check`。

## Test

```bash
./gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

关键验证入口：

```bash
./gradlew.bat :architecture-tests:test --console=plain --no-daemon --stacktrace
./gradlew.bat :build-logic:check --console=plain --no-daemon --stacktrace
./gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon --stacktrace
./gradlew.bat :app:assembleDebug --console=plain --no-daemon --stacktrace
```

## Notes

- 本轮 `uth-docs` 接管收尾只做文档治理和旧文档归档，没有运行 Gradle 命令。
- 涉及入口、Hilt、依赖图、Manifest、plugin runtime、QQ、Cron、backup 等跨模块边界时，至少补 `architectureCheck`，交付前按仓库规则跑 `clean assembleDebug`。
