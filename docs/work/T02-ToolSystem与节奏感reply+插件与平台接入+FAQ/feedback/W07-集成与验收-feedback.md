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

## 2026-11-23 · 定时任务和设置入口复验

- 基础 App `0DEC37D7CF4A539A70777879813F3BA66267BB0D1AC30D8E8F5074BF6456D116` 联编通过。检查 `:app:dependencies --configuration debugRuntimeClasspath`、APK ZIP 条目及 14 个 DEX 的 `dexdump -f` 类定义。基础 App 的工程依赖为 `:core`，容器原生文件限 ARM64 PRoot、loader 和 talloc。ZIP 无 rootfs、NapCat 或插件 JAR，DEX 无 QQ、定时任务、Commons Compress 或 XZ 的实现。
- 在 MuMu Android 15 / x86_64 实际装载新旧签名定时任务制品，覆盖新版引导状态持久化和旧版忙碌主会话排队。宿主启停的定向复验使用记录式调度器，覆盖取消唤醒、重新登记和保留待运行任务入队。[W02 Feedback](W02-Android会话与投递-feedback.md) · [W05 Feedback](W05-主会话定时任务-feedback.md)
- Compose 定向通过常见问题、关于、任务入口门禁和暂停任务手动运行。Activity 重建用例验证收到合成下载票据后回收。正式制品安装、任务启用和系统升级留作端到端验收。[W06 Feedback](W06-Android页面与引导-feedback.md)

## 2026-11-23 · 真实模型 Reply 复验

- MuMu 上已分配的真实文本模型接受自动结构化工具调用。能力探测使用与正式请求一致的工具选择后，App 定向用例 1/1 通过。同一 Reply 调用的两条消息均成功入账并按序可见，持久化投递时间相差 1,200 ms。[W01 Feedback](W01-Core工具与Reply-feedback.md)

## 2026-11-24 · 定时任务插件目录安装

- `:app:assembleDebugAndroidTest` 通过。MuMu Android 15 / x86_64 运行 `OfficialPluginDistributionRepositoryTest`（参数 `t02ScheduleUiProbe=true`），6 项通过，无跳过。从本地合成签名目录经 App 插件管理服务下载实际 W05 定时任务插件，验证启用、停用、无须重新下载即可重新启用和卸载清理。App SHA-256 `1C3D53993B2C631F6CF238A3571AC78F3F5F077D227A8B864909B201D2F43726`，AndroidTest SHA-256 `13D79307B9EC38979D39AC34ACDB3D1B167845B379B7919D605AE11D1EEC59BA`，定时任务签名包 SHA-256 `E9F1392B9F72F436D8830C8356114C0277A58A14490E0DB7641ECD9E235E51D2`。证据覆盖 A06、A07、A12 的安装路径和目录异常回归。[目录安装验收测试](../../../../app/src/androidTest/kotlin/com/elymbot/android/runtime/pluginpackage/OfficialPluginDistributionRepositoryTest.kt)

## 2026-11-24 · 系统任务登记与重开补跑

- 以 `ef94a40` 构建隔离 Debug 探针，使用独立应用包名、本地签名测试目录和合成模型。定时任务经正式 App 服务创建，使用实际签名 W05 插件和正式的 WorkManager 装配。到期前，Android JobScheduler 可见登记的 `SystemJobService`，App 的任务运行表未出现提前触发。
- 在 MuMu Android 15 / x86_64 分别测试结束 instrumentation、正常 Activity 退后台后回收进程，以及后台进程仍存活但被冻结。自然到期未启动后台服务。手动请求系统执行和后续自然调度均出现 `user 0 is stopped` 的服务启动拒绝，同期系统用户状态查询为 `RUNNING_UNLOCKED`。受支持 ARM64 设备的自然唤醒仍待验。
- 重新进入 App 后，`ScheduledTaskWakeWorker` 实际运行。三个到期时间各有一条 `SCHEDULED/SUCCEEDED` 主会话记录、一个完成轮次和一条已送达的 App Reply，分别迟到约 198、97、78 秒。两个未更换安装身份的运行回执均确认。剩余一个发生在探针重装插件与终态回执交接并发时，App 保留成功记录但待写回标记仍在，已纳入旧安装回执处理的定向返工。

## 2026-11-24 · 启动和手动版本检查

- MuMu Android 15 / x86_64 定向复验启动及手动版本检查。App 管理服务启动时请求一次，HTTP 503 产生网络错误状态；再次发起自动检查未增加请求。手动检查复用该服务并识别更新，确认前未请求 APK。该用例使用合成本地 Release，覆盖 T02.04 的 A14 装配检查。[定向用例](../../../../app/src/androidTest/kotlin/com/elymbot/android/runtime/pluginpackage/OfficialAppUpdateServiceTest.kt) · [联编制品摘要](W02-Android会话与投递-feedback.md#2026-11-24-高审-p2-媒体清理与授权快照修复)

## 2026-11-24 · ARM64 调度观察与签名插件装配

- 在 Android 16 / API 36 ARM64 设备上安装独立测试 App，由正式服务加载签名定时任务插件并登记一次任务。登记后正常退至后台，结束该测试进程，保留 Android JobScheduler 的任务。到期后观察 13 分 57 秒，系统任务处于 `ready`，App 进程未启动，任务运行表、轮次和投递表无记录。随后用 `cmd jobscheduler run -s` 请求系统在满足约束时执行，系统返回 `Running job`，后续 25 秒仍无进程或模型请求。未观察到该设备在进程结束后的自然唤醒。
- 超过 15 分钟后以正常 App 入口冷启动，定时任务插件的持久记录从零条增加为一条 `MISSED`。App 任务运行表、轮次表和投递表仍为空，没有模型请求。
- 同一设备安装独立 QQ 测试 App，App 管理服务从本地合成签名目录安装两个独立插件，`scheduled-tasks` 和 `qq` 均返回 `Completed`。QQ 新命令设置能力可读。通过正式宿主启动 QQ，Ubuntu rootfs 解包完成，容器内入口已写入 `install-base`（15%）状态。此处确认 ARM64 下签名安装及原生容器启动，登录码与收发继续实测。
- QQ 测试 App SHA-256 `6983EC06C9946685F7A7D3AF634E4A83C07CFC8AF6A23C8A555A528B06735006`。QQ 签名包为 65,735,084 字节，内层 JAR 65,734,240 字节，低于 64 MiB，外层 SHA-256 `FB5495F9788B00312A2CADF338FDA2B7FF7218CCF2F4F8E024BF65820D500379`。定时任务签名包 SHA-256 `78B2BEEEF349B60702272A3C63075F4647C39E0043FA853B5ED3450044B619A7`。测试签名与本地目录仅用于隔离环境。
- Ubuntu APT 基础依赖实际安装完成，随后入口停在 `download-installer`（45%），宿主报告 `FAILED/STARTUP`，没有出现登录码。保留初始现场后，使用同一 APK 安装的 PRoot、loader 与 talloc，在原隔离 Ubuntu 内通过 curl 分别请求固定安装器 API 与固定提交 Raw，均返回 HTTP 404。Desktop 同样收到 404。已验证 APK 仅包含核准的三项 ARM64 容器启动文件，未含 rootfs、可选插件 JAR、NapCat 或 Linux QQ 制品。

- 在同一 ARM64 探针中另建一次任务后正常退至后台，保留 App 进程。到期约 4 小时 32 分钟后，该进程仍存活，JobScheduler 显示任务为 `ready` 且未被领取，系统记录应用未停止，两项后台运行 AppOps 为默认允许，合成模型没有收到请求。该轮未观察到后台自然触发。

## 2026-11-24 · ARM64 QQ 在线安装与 NapCat 状态检查

- 同一 Android 16 / API 36 ARM64 真机隔离探针中的原固定安装器入口，在隔离 App 的 `download-installer`（45%）停住并返回 HTTP 404。按 [W04 在线运行来源恢复与资源 pin 复验](W04-QQ平台接入-feedback.md) 固定来源恢复后，两个固定 commit 上游源码与同版本 Shell 字节归档均逐项通过仓库已有 SHA-256 pin 核对；三项下载内容只写入该 App 的测试私有缓存。随后从 `run-installer`（65%）继续，下载 207,538,924 B ARM64 Linux QQ deb，安装目录出现 QQ 可执行文件，并推进到 `start-napcat`（96%）。
- 设备侧只读观察确认 QQ 与 NapCat 进程仍在运行，loopback 6099 与 6199 均监听。NapCat WebUI 在 6099 的 `GET /` 返回 HTTP 301。6199 的 listener 只证明端口监听，不代表已完成认证的 OneBot 反向 WebSocket 连接。
- 隔离 App UI 仍显示「正在启动 QQ」和「未登录 QQ」，未显示二维码。同一隔离 App 身份的 shell 对 NapCat `auth` 与 `CheckLoginStatus` 发出只读 POST，两者均返回 HTTP 200 且 `code=0`；登录状态为 `isLogin=false`、`coreReady=false`，`auth` 响应的 `qrcodeurl` 非空。未记录 token、二维码内容或账号信息。这只证明 NapCat 后端已生成二维码数据，不证明 App 展示二维码、扫码登录或消息收发。
- Probe Activity 的 180 秒是观察退出上限；观察结束时 App 和 guest 进程仍在运行。该结果不证明长时间稳定性。新固定 URL 的代码与 AAR pin 复验见 [W04 Feedback](W04-QQ平台接入-feedback.md)；尚未用干净 ARM64 真机和正式在线制品复验。

## 2026-11-25 · 网络策略修复与 ARM64 QQ 登录续验

- App 网络安全配置收敛到主 source set。主配置默认拒绝明文流量，仅对 `127.0.0.1` 开放；Debug 配置额外允许 `localhost`。移除旧 Debug manifest 与旧配置资源，新增 `NetworkSecurityPolicyTest` 检查 Debug 下两个 loopback 名称可用且外部域名不允许明文。
- 总控报告 Debug App 与 AndroidTest 构建成功，Release merged manifest 核对通过。解包检查 Debug APK 中的有效配置为 `base-config cleartextTrafficPermitted=false`，仅允许 `127.0.0.1` 和 `localhost`；Release 主配置仅允许 `127.0.0.1`。本轮只确认 AndroidTest 构建，未报告执行该 instrumentation test；旧 Release APK 不作为本轮证据，未确认新的 Release APK 行为。具体构建命令未随本轮结果提供。
- 用户手动覆盖安装同证书、`versionCode=1` 的隔离辅助 APK，SHA-256 为 `09800FCDDD712EBC91AEC0669D04FF38469E3C7E9598B2643DCF184B5F97B197`；总控核对设备已安装字节与候选一致。保留此前已安装的 Linux QQ，不重复执行已经验证过的启动流程。
- 正式插件页面显示 `WAITING_FOR_LOGIN` 且二维码可见。用户确认扫码后，界面显示「已连接」、二维码移除，OneBot loopback WebSocket 已建立。账号栏仍显示「未登录QQ」，账号身份字段待排查；该状态不足以确认身份信息已正确呈现。未验证消息发送或接收。
- 本轮是真机登录界面与 loopback 连接的续验，不关闭 QQ Checklist：真实 QQ 身份状态、OneBot 消息往返、后台/锁屏及断线重连仍待验证。

## 待验

- 已启用插件的任务正式入口、系统自然唤醒、ARM64 QQ 与平台投递、正式双源分发及同签名 App 更新仍待按 A01–A17 验收。
