# W07 · 集成与验收 Feedback

## 2026-11-23 · Reply 集成

- 更新现有真实模型验收，从正式 App 配置页验证结构化工具能力，从专属会话发送固定文本探针，并核对该轮的 Reply 回执和聊天气泡。验收输出不记录服务标识和模型回复内容。

| Checklist 项 | 方法或命令 | 平台或产物版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.12` · A02、A15 | `:app:assembleDebug`，覆盖安装，`am start -W` 启动 `MainActivity` | Android 15 / API 35 模拟器，Debug `0.1.0` (`versionCode=1`) | 构建成功，覆盖安装后冷启动成功。设备 APK SHA-256 与本地产物一致，为 `446607F095351F7D78BC74E1186B19913F870FC6D5FFC862D2F4BF1C51CE3D63`。 | [Android 正式应用入口](../../../../app/src/main/kotlin/com/elymbot/android/ElymBotApplication.kt) |
| `T02.12` · A02–A04 | `:app:assembleDebug :app:assembleDebugAndroidTest`，覆盖安装后由 `AndroidJUnitRunner` 运行 `ReplyHostIntegrationTest`、`AppSessionIntegrationTest`、`AppFailureIntegrationTest` 和 `RoomReplySubmitterTest` | Android 15 / API 35 模拟器，Debug `0.1.0` (`versionCode=1`) | 两 APK 均构建成功。App SHA-256 `BB4E9B6D360B048B642300E899F0453F3C4AE65A7E1645D930AD9F7BAB889DE3`，AndroidTest SHA-256 `D2BC152F56F83DA8A34523311319EE9AA01BFC4037DB29FC8E202518ED3FB671`。11 项中 8 项通过。两个 ReplyHost 测试和跨会话测试在合成 HTTP 夹具等待初始请求时超时，交 W02 定位。 | [W02 Feedback](W02-Android会话与投递-feedback.md) |
| `T02.12` · A02 | `ConfiguredModelAcceptanceTest` | Android 15 / API 35 模拟器，Debug `0.1.0` (`versionCode=1`) | App 的已保存配置没有分配文本模型，用例在发起模型请求前结束。真实模型验收待使用具备文本配置的设备执行。 | [真实模型验收](../../../../app/src/androidTest/kotlin/com/elymbot/android/ConfiguredModelAcceptanceTest.kt) |

- 后续诊断使用 App SHA-256 `CD8D6A16952187D5A963941687D5C1E50B4ED43BE4EE0A00F394BF70316758D6` 和 AndroidTest SHA-256 `4C9CBA21CD81F4D583A8AE40CC0EED793B55F38151B87A94FE0B08C08CBE28F8`。单条 Reply 用例仍停在合成 HTTP 入口，测试服务读到的请求数为零。真实模型用例使用同一制品确认配置缺失。
- `:app:dependencies --configuration debugRuntimeClasspath` 显示基础 App 的工程依赖只有 `:core`。基础 APK 容器项为 ARM64 的 PRoot、loader 与 talloc。APK 中未发现 Ubuntu rootfs、NapCat、Linux QQ 或插件 DEX 包。

## 2026-11-23 · Reply 与装载复验

- MuMu Android 15 确认数字回环地址的夹具请求因调试版域名规则失败。改用已允许的 `localhost` 后，正式入口的到期释放、后台未读、跨会话隔离、不兼容模型、取消待发和网络失败重试通过。压缩后 HistoryRead 与检查点回退通过。运行命令、具体用例和制品哈希见 [W02 Feedback](W02-Android会话与投递-feedback.md)。
- 在完整 `ElymBotStorageTest` 的 28 项中，首次 27 项通过。视觉模型直接处理与回退用例改用 Reply 夹具后单项复验通过，保留图像探测缓存、原图投递、回退描述和主模型投影的检查。复验使用同一个 App APK，AndroidTest SHA-256 为 `7809045A5C8899E2309A46C1CC1F6D57720B16104E6B7A251FB51F8C4D99C4FB`。
- 独立编译 D8 的合成签名包在基础 App 进程中通过实际 `DexClassLoader` 生命周期测试。覆盖安装后启用失败、重试成功、更新失败保留旧版、停用与恢复，以及从持久记录恢复安装身份。制品和生成方法见 [W03 Feedback](W03-插件底座与分发-feedback.md)。
- 定时任务模块 `:plugins:scheduled-tasks:testDebugUnitTest :plugins:scheduled-tasks:assembleDebug` 通过，16 项单测无跳过。实际 AAR 的 `classes.jar` 通过 D8 生成只含 `classes.dex` 的插件 JAR，供宿主装配后验证正式触发。包级签名和系统调度尚未验收。

## 2026-11-23 · 现有正式发行基线

- 读取 [GitHub 最新发行 API](https://api.github.com/repos/undertaker33/ElymBot/releases/latest)，版本名称为 `v0.1.0`，标签为 `正式版`，资产只包含调试和正式 APK。正式 APK 的 API 摘要为 `6861704B7CA84E63A2E000A94F19EFAF69E8B2B2C57219E9A1EAC16E3907AC3B`，与已有本地正式 APK 完全一致。
- SDK Build Tools 37.0.0 的 `aapt dump badging` 核对同一 APK 为 `com.elymbot.android`、`versionCode=1`、`versionName=0.1.0`。`apksigner verify --print-certs` 核对其 V2 签名证书 SHA-256 为 `FD4EF592C4DDB82BB26411F977180F689313E8138F1795BA18064B301CF1928F`。此记录提供 A14 的已发布基线。候选包的版本检查、安装更新和异签拒绝待执行。

## 2026-11-23 · 定时任务集成验收制品准备

- 用实际 W05 AAR 生成的 DEX JAR 构建合成签名插件包。ZIP 条目为 `manifest.json`、`signature.bin` 和 `payload/plugin.jar`。使用临时 RSA 公私钥在本地签署清单并复验签名，清单绑定入口类、ABI、版本和 JAR 摘要。插件包 SHA-256 为 `EB41B036FD196C2029692C47AC6E38071117BF89FD748F6EC479F6459F5CC0B2`。此制品供宿主合成信任环境测试，正式发行和 Android 安装待验。

## 2026-11-23 · 设置、Reply 和目录联编复验

- App 和 AndroidTest 联编通过，MuMu 覆盖安装并冷启动成功。设置同卡的 FAQ 和关于、独立搜索、首次配置和模型页返回，在正常动画与关闭动画下通过定向测试。[W06 Feedback](W06-Android页面与引导-feedback.md)
- 同一 App 候选验证正式 Reply、跨会话、不兼容模型及 Room 投递的 12 个用例，全部通过。[W02 Feedback](W02-Android会话与投递-feedback.md)
- 修订为隔离目录与允许的本地 HTTP 域后，签名目录合成测试 5 项通过，包含签名缓存、主源摘要不符时拒绝备用，以及主源传输失败后核对备用文件摘要。[W03 Feedback](W03-插件底座与分发-feedback.md)

## 待验

- 当前产物仅用于 Reply 集成，尚未覆盖官方插件加载、系统调度、QQ、真实双源和同签名 App 更新。正式模型及用户行为验收使用完成相应路径集成后的候选版本。
