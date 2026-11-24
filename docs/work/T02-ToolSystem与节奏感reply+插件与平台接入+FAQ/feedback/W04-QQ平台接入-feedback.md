# W04 · QQ 平台接入 Feedback

## 实际结果

- 新增 Core 平台插件边界：`PlatformPluginHandle` 暴露短期运行状态、登录/重连动作、出站回执和有字节上限的媒体读取；`PlatformRuntimeHost`/`PlatformGuestProcess` 提供宿主绑定的 guest 进程控制，不暴露 Android 类型。入站 envelope 明确区分 `TRIGGER_AGENT` 与 `CONTEXT_ONLY` 及私聊、成员隔离和群公共历史可见范围。
- 新增 QQ 模块构建依赖与首个纯 Kotlin 接入切片。OneBot 解析器将文本、图片、语音、视频、文件及引用转为 Core 消息；路由按 QQ 账号、私聊用户或群成员隔离。获准的群公共材料使用共享群路由且不触发 Agent。
- D07 策略切片接受配置快照，覆盖用户/群/用户与群组合准入、配置管理员绕过、命令别名、私聊唤醒、群 @/唤醒、关键词屏蔽、限频丢弃或有界暂存重放，以及获准群公共材料。事件 ID 在 QQ 插件内去重，App 入站仍需负责持久幂等和最终绑定授权。
- 交付文件：`core/src/main/kotlin/com/elymbot/core/contract/PlatformPluginContracts.kt`、`plugins/qq/build.gradle.kts`、`plugins/qq/src/main/kotlin/com/elymbot/android/plugins/qq/`、`plugins/qq/src/test/kotlin/com/elymbot/android/plugins/qq/`。

## 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.08 A05`（入站合成部分） | 总控执行 `:plugins:qq:testDebugUnitTest --offline --console=plain` | Windows amd64；Gradle Wrapper 9.9.1、Microsoft JDK 17.0.17、AGP 9.5.1、Android SDK Build Tools 37.0.0 | 16/16 JVM 单元测试通过、0 跳过。合成 OneBot 输入覆盖文本、图片、语音、视频、文件、引用及稳定路由；未覆盖出站不支持类型回执，也不是 OneBot 真连接或媒体下载验收 | `plugins/qq/src/test/kotlin/com/elymbot/android/plugins/qq/OneBotMessageParserTest.kt`、`QqOneBotInboundAdapterTest.kt` |
| `T02.08 A08、A09`（策略合成部分） | 同一 JVM 单元测试任务 | 同上 | 覆盖成员隔离、群公共 `CONTEXT_ONLY`、私聊/群触发策略、管理员绕过、命令别名、关键词屏蔽、重复事件及限频暂存顺序；未覆盖正式 App 会话、真实收发或设备恢复 | `plugins/qq/src/test/kotlin/com/elymbot/android/plugins/qq/QqInboundPolicyTest.kt`、`QqEventDeduplicatorTest.kt`、`QqRateLimiterTest.kt` |
| `T02.07 A06–A08、A17` | 未执行 Android 构建、插件安装、容器启动或设备操作 | 不适用 | 未验证；当前交付不包含 QQ `PluginEntrypoint`、ON-04 资产准备/运行控制或正式 Proot 接线。依总控指示，QQ 登录与设备测试留待后续阶段。Checklist 未勾选 | `plugins/qq/`；[ON-04 真机历史记录](../../../../探索/T02/QQ验证/记录.md) |

## 限制与待验

- 本次为 Core 契约、OneBot 合成适配及 D07 纯 Kotlin 策略阶段，不代表 W04 或 T02.07/T02.08 完成。
- 下一阶段需补 QQ 插件正式入口、配置读取、ON-04 私有运行资产准备和进程生命周期、OneBot 连接/发送及真实 receipt；W02 负责把 `PlatformRuntimeHost` 绑定到 active 安装身份的数据目录并接入现有 Proot launcher。
- `copyMedia` 契约与事件期间的媒体来源索引已定义，但真实 OneBot 媒体读取、类型/字节限额接线及 App 附件持久化尚未实现。出站 QQ 媒体尚未实现；不支持类型须返回受控失败。
- 未运行真实 QQ 登录、二维码扫码、私聊/群收发、后台/锁屏或断线重连；未进行 APK/插件安装、真机或模拟器验证。测试使用合成 JSON 输入，不包含账号或凭据。

## 复审修复（2026-11-23）

- 修复暂存重放沿用旧策略的问题。`PendingQqInbound` 现在保存解析后的原始 OneBot 事件；实际重放时重新按传入的当前 D07 配置评估准入、触发、命令别名和公共性，再构造交给宿主的 envelope。策略拒绝的暂存消息不会到达宿主。
- 将插件内去重改为 reservation 生命周期。并发期间同一 event key 仍会被挡住；宿主明确返回 `Accepted` 或 `Duplicate` 后 reservation 才进入已完成集合。宿主 `Rejected`、异常、取消、限流丢弃和暂存过期会释放 reservation。限流器现在把过期/清理项交给适配器释放，并在重放异常时释放本批尚未处理项。
- 补充合成 JVM 回归用例：当前白名单撤销时丢弃已暂存事件并允许配置恢复后重投；重放使用当前命令别名及原始输入；`Rejected(PERSISTENCE)`、异常、取消后可以重投；并发重复事件只调用宿主一次；过期暂存释放 reservation。
- 总控在 Windows amd64、Gradle Wrapper 9.9.1、Microsoft JDK 17.0.17、AGP 9.5.1、Android SDK Build Tools 37.0.0 环境执行 `:plugins:qq:testDebugUnitTest --offline --console=plain`：25/25 JVM 测试通过、0 跳过。覆盖本节策略重评、reservation 生命周期、并发重复及过期暂存用例。此前 16/16 结果对应修改前源码；本轮未执行设备或 QQ 登录操作。

## ON-04 运行入口与安全切片（2026-11-23）

- `QqPluginEntrypoint` 是签名插件的无参入口；候选 `prepare` 只创建 handle，不启动 guest。激活后的 `start` 在插件自己的私有数据目录校验/准备资源，再通过宿主 `PlatformRuntimeHost` 启动 guest 并监听 OneBot；同一 handle 暴露运行状态和 `QqPlatformSettingsHandle`。设置变更按 revision 原子持久化，初始 D07 配置仍 fail-closed。
- `PlatformPluginHandle.stop()` 是可逆暂停：停止 monitor、OneBot listener 和 guest，保留安装身份、设置、rootfs 与登录历史，后续 `start` 可重用；`close()` 仍负责最终卸载撤销。重连重启 guest，登录刷新复用 ON-04 的 `QQLogin/RefreshQRcode`，登录状态读取 `QQLogin/CheckLoginStatus`。
- NapCat API 使用本地 WebUI `auth/login`，凭安装私有 WebUI secret 计算登录 hash 并缓存短期 credential；credential 不进入状态、消息或日志。退出不调用未确认的远端登出端点，而是停止并重启 guest，保留 quick-login 历史；只在 guest 仍存活且状态接口确认未登录后返回成功，超时或重启失败抛出失败，不宣称远端凭据已撤销。
- OneBot 反向 WebSocket 绑定 `127.0.0.1:6199`，握手前要求 `Authorization: Bearer <ONEBOT_WS_TOKEN>` 与安装私有令牌精确匹配。每次 guest 启动重写 NapCat WebUI/OneBot 配置以替换旧固定 token；token 由安装私有 secrets 文件保存，初次生成 256-bit 随机值。移除了配置快照以及 secrets 内容、长度或路径日志。受控异常日志只报告阶段，不输出 credential/token。
- 使用 ON-04 资源名 `runtime/ubuntu-rootfs.tar.xz`（64,133,552 bytes，SHA-256 `91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa`）与 `runtime/root_launcher.sh`。后者当前为 14,900 bytes，SHA-256 `b67c6afbfb2764c4e609d2e2f2b4e5caca0150e77dae342dd8f5a0512dd34c64`，与源码内校验常量一致。根文件系统只作为签名插件 JAR 资源，不加入基础 APK 或仓库源码；实际签名插件包大小仍待 W03 打包验证。
- 插件独立运行依赖 Commons Compress 1.28.0、XZ 1.10 及其 runtime transitives（Commons Codec 1.19.0、Commons IO 2.20.0、Commons Lang 3.18.0）；Core、协程和 JSON 使用宿主提供的 compileOnly API。HTTP 使用 Android 可用的 `HttpURLConnection`。

| 检查 | 方法或命令 | 环境 / 输入 | 结果与证据 |
| --- | --- | --- | --- |
| 运行入口与 AAR 编译 | 总控先前执行 QQ Kotlin/AAR 构建探针 | Windows amd64；Gradle Wrapper 9.9.1、Microsoft JDK 17.0.17、AGP 9.5.1 | 当时生产 Kotlin 与 QQ AAR 编译通过；随后新增本节令牌/退出改动及测试夹具修复后尚待总控复跑，不能作为当前源码绿色证据 |
| JVM 定向测试 | `:plugins:qq:testDebugUnitTest --offline --console=plain`，总控执行 | 合成 OneBot 消息和本地 loopback 握手；不连接 QQ | 本轮修复带默认唤醒词导致的测试拒绝，并给协调等待加 2 秒超时；新增 `QqOneBotReverseWebSocketServerTest` 核对无令牌、错误令牌返回 401，正确 Bearer 才完成 WebSocket upgrade。测试尚未复跑 |
| 资源核对 | 本地文件字节数与 SHA-256 对照 `QqRuntimeAssets` 常量 | ON-04 launcher 文本资源；rootfs 为 W03 已核对的资源输入 | launcher 字节数和 SHA-256 匹配；rootfs 数值沿用 W03 输入清单；本轮未重新打包签名 JAR |
| QQ 登录与设备运行 | 未执行 | 不适用 | 未扫二维码、未接真实 QQ、未运行 ADB/模拟器或启动容器；MuMu 仅获准用于 non-QQ 验证。登录、扫码、私聊/群收发、退出二维码呈现、锁屏/后台及 ARM64 真机稳定性均待后续验收 |

本阶段提供正式 QQ 插件入口、私有 ON-04 资源准备和生命周期、OneBot loopback 鉴权及 D07 设置持久化切片；尚不代表 T02.07/T02.08 完成。后续需总控复跑 QQ JVM/AAR，W03 验证签名插件 JAR 内容与 64 MiB 单文件上限，W02/W06 接入宿主后再完成真实入口与设备验收。

## W08 返工与登录二维码矩阵（2026-11-24）

- **P2：reconnect 取消清理。** `startGuestLocked` 在宿主 launch 返回后立即检查协程取消；`reconnect` 捕获取消后在 `NonCancellable` 中运行 `stopGuestLocked`，再发布 `STOPPED` 并重新抛出取消。回归用例让 fake host 的 `launchGuest` 挂起，取消 reconnect 后再返回 guest，检查该进程已停止且状态为 `STOPPED`。
- **P2：静默期间重放暂存消息。** 正式 OneBot 入站处理完成后唤醒安装期的重放协程；有暂存消息时按短间隔检查限频窗口，无暂存时挂起等待新事件或设置变更。每次重放读取当前 QQ 设置快照和当前宿主 sink；停止/卸载时取消重放任务。回归用例在无后续入站消息时等待限频窗口到期，并在暂存后变更命令别名及 sink，确认重放使用当前别名和 sink。
- **P2：OneBot 异步回执。** `ok + retcode 0` 才报告 `Delivered`；`failed` 且 retcode 不是 `0/1` 才报告明确失败；`async + retcode 1`、字段类型错误、未知状态和状态/retcode 矛盾均为 `Unknown(RESPONSE_INVALID)`，宿主不得据此自动重试。依据 [OneBot 11 HTTP 响应定义](https://github.com/botuniverse/onebot-11/blob/master/communication/http.md) 和 [WebSocket 响应定义](https://github.com/botuniverse/onebot-11/blob/master/communication/ws.md)。
- **登录挑战边界。** Core `PlatformLoginChallenge` 现在携带 `PlatformQrMatrix`：dimension 为 1–177，`darkModules` 行优先且 `true` 表示深色；`copyOf` 校验维度并复制到只读列表。QQ 插件独立使用 ZXing Core 3.5.3、`margin=0` 将 NapCat 原始 challenge 编码为矩阵；原文不进入 Core 状态、UI 或模型。插件缓存同一短期 challenge 的矩阵，登录、停止、刷新或 challenge 变化时清理/替换。上游响应未提供可确认的有效期，因此 `expiresAtEpochMillis` 保留为 `null`。
- Commons Compress 与 XZ 依赖版本移入版本目录，新增的 ZXing 依赖仅由 QQ 插件运行时携带。TAR 解包改用 `getNextEntry()`，并显式检查父目录创建。

| 检查 | 方法或命令 | 环境 / 输入 | 结果与证据 |
| --- | --- | --- | --- |
| 三项 P2 JVM 回归 | `:plugins:qq:testDebugUnitTest --offline --console=plain`，由总控执行 | Windows amd64 构建环境；合成宿主和 OneBot JSON | 总控回报 33/33 JVM 测试通过、0 跳过。该轮构建期间最后把 reconnect 初始 `stopGuestLocked()` 移入取消处理 try 块，不能确定该行变更是否进入该产物；总控将联合复跑作为最终证据 |
| QQ AAR 编译 | `:plugins:qq:assembleDebug --offline`，由总控执行 | Windows amd64 构建环境 | 总控回报 AAR 构建成功，SHA-256 `C078B15D0D3BD3D783FCD840D83216F7CB39688D2ACCF293188C5E7B9749C44C`。由于上述源码并发变更，本轮结果仍待最终联合复跑 |
| QQ 专属依赖 | 总控离线核对依赖图 | ZXing Core 3.5.3 | 总控确认 ZXing 仅在 QQ 插件依赖图内，基础 App 不包含；当前 JAR 容量需随最终联合封包再次核对 |
| 签名插件 JAR | W03 打包脚本，synthetic key 和 ON-04 rootfs | 上一轮无 ZXing 制品 65,434,541 bytes | 该旧制品低于 64 MiB 单文件上限，不覆盖新增 ZXing 后的包。新版签名 JAR 尚待重新打包并核对 |
| 二维码与 QQ 真机 | 未执行 | 仅静态代码和合成 URL | 未进行真实 NapCat 返回载荷、二维码扫描、登录或 QQ 收发验证；`expiresAtEpochMillis` 无上游证据，暂为 `null` |

### 最终制品复验

- 总控在冻结源码上运行 `gradlew.bat :plugins:qq:testDebugUnitTest :plugins:qq:assembleDebug --offline --console=plain`，JVM 33/33 通过、0 跳过。Debug AAR SHA-256 为 `BA0A76985787DCDD30D8F459CD8C466171C7390FCD8807331BD180CCAE952148`。
- 用 W03 打包入口及本地测试签名者构建 ARM64 签名插件，内层 JAR 65,714,944 bytes，低于 64 MiB 单文件上限。外层文件 65,715,793 bytes，SHA-256 `1BB4AFD992257417F6AE2DF9209F6B61F7A5C7235868DCAEF9D2757E013A833C`。核对内层 `classes.dex`、`runtime/root_launcher.sh`、`runtime/ubuntu-rootfs.tar.xz` 及嵌入 rootfs 的已固定摘要。
- 依赖图确认 ZXing Core 3.5.3 只由 QQ 提供，基础 App debug runtime 图无 QQ 模块和 ZXing。复审关闭三项 P2；QQ 管理命令专项及真实 ARM64 扫码、登录和消息往返仍待验收。
- Windows 默认 `core.autocrlf=true` 会改写按原始字节核验的启动脚本。为该脚本固定 LF，使用 `git -c core.autocrlf=true checkout-index` 定向复验，检出文件摘要为已验证的 `B67C6AFBFB2764C4E609D2E2F2B4E5CACA0150E77DAE342DD8F5A0512DD34C64`。

## D07 QQ 命令管理与群历史修正（2026-11-24）

- 旧版 `QqBotCommandRuntimeService` 对 QQ 端所有非空 slash 命令使用 `pluginCommandsAdminOnlyEnabled` 总门禁；拒绝提示仅在 `replyWhenPermissionDenied=true` 时发送，默认两项均为 false。`BotCommandRouter` 的 `/op`、`/deop`、`/wl`、`/dwl` 只有该总门禁，没有独立动作鉴权：管理员/白名单分别按传入非空字符串增删，不会自动启用白名单。旧 `QqWhitelistMatcher` 中纯数字可同时匹配用户 ID 与群 ID，`user_group` 仅匹配指定群成员。
- Core QQ 设置追加 `pluginCommandsAdminOnlyEnabled` 与 `replyWhenPermissionDenied`，默认 false；旧设置文件缺少两字段时按 false 读取，并在后续写入时持久化。
- QQ 命令处理在正式 OneBot 去重与入站策略后、宿主 sink 前执行。四条配置管理命令始终要求当前 `adminUserIds` 中的 QQ 用户；其他 slash 命令只受可配置总门禁限制。拒绝提示原路由发送旧版管理员提示。无效参数被消费并返回用法，不进入 Agent。
- `/op`、`/deop` 原子增删管理员 UID。`/wl <UMO>`、`/dwl <UMO>` 把数字映射为 `allowedUserIds` 与 `allowedGroupIds`，把 `user_group` 映射为 `allowedGroupMembers`；删除不清除 App 使用的 `allowedPrivateUserIds`。ACL 变更通过私有设置 Store 的 revision 更新；成功落盘后先完成事件 reservation，再发送确认。未知发送回执不重放管理副作用；明确持久化失败释放 reservation，允许安全重试。用户定义的命令别名可映射到四条命令，规范命令名保留。
- 群历史策略修复：普通未触发且获准的公共群消息使用 `CONTEXT_ONLY + GROUP_PUBLIC`，群内触发消息使用 `TRIGGER_AGENT + GROUP_MEMBER`。触发消息保留成员隔离路由与原群回复目标，以符合 App 入站 envelope 校验。
- 添加合成 JVM 用例覆盖：管理员权限与默认静默拒绝、可配置总门禁与旧拒绝文案、别名、参数校验、持久化管理员 ACL、纯数字和 `user_group` 准入实际效果、保留 App 私聊授权、重复事件与未知回执不重复变更、公共群上下文和成员触发隔离，以及旧设置缺少新增字段时的默认值。
- 旧 `BotCommandRouter` 还包含多 Agent 控制与 STT/TTS 等命令；这些行为依赖旧版多 Agent/语音范围，本次不在 QQ 管理命令中重建。约定的宿主会话命令由 W02 按当前身份和原回复路由处理。真实 QQ 登录和设备消息往返未执行；构建与 JVM 验证由总控执行并列于下表。

| 检查 | 方法或命令 | 环境 / 输入 | 结果与限制 |
| --- | --- | --- | --- |
| QQ 定向 JVM 与 AAR | 总控执行 `:plugins:qq:testDebugUnitTest :plugins:qq:assembleDebug --offline --console=plain` | 合成 OneBot 消息与临时插件设置目录；Windows amd64，Gradle Wrapper 9.9.1、Microsoft JDK 17.0.17、AGP 9.5.1 | 43/43 JVM 测试通过、0 跳过；Debug AAR SHA-256 `46D71340B2C34DED63CA3ED87B5C0F696595C403AF32CAF284FE7537FBAF23CC`。不代表 QQ 登录或真实设备验收 |

- 去重 reservation 只保存在插件进程内；设置 CAS 和集合增删在单次更新中幂等，但没有持久化 OneBot 命令事件收据。当前没有证据确认 NapCat 会在进程重启后重放旧事件；若重启后旧 `/op` 在较新的 `/deop` 之后重放，旧命令仍可能重新授予权限。需要先核实上游事件是否跨重启重放；若会重放，应把有界 command event IDs 与 ACL 更新放在同一 settings 文件事务中持久化。

## W04 D07 通知行为收尾（2026-11-24）

- 入站策略为私聊缺少必需唤醒词增加独立的 `WAKE_WORD_REQUIRED` 拒绝原因；白名单拒绝和该原因仅在独立 `QqPlatformCommandSettings.replyWhenPermissionDenied` 为 true 时按原 OneBot 消息路由提示。限频策略最终丢弃也使用该开关提示；暂存消息不提示。
- 关键词检测仅作用于通过准入且已触发的消息。匹配时始终按原消息路由发送旧版屏蔽提示，不依赖权限拒绝提示开关。获准但未触发的公共群消息仍以 `CONTEXT_ONLY + GROUP_PUBLIC` 交给宿主，不发送策略提示；忽略自己、忽略 @全体和其他未触发消息仍静默。
- `QqOneBotInboundAdapter` 从插件设置 Store 读取可选命令设置，提示复用原管理回复发送入口；提示发送结果不改变宿主准入决定。
- 暂存回放后重评当前事件的行为已在当前实现中：`dispatchReady` 处理旧事件后，`dispatchCurrentAfterReplay` 重新读取 D07 设置，再决定当前消息是否进入宿主。`QqManagementCommandsTest.currentAllowedEventIsReevaluatedAfterEarlierReplayChangesAcl` 覆盖前条 `/dwl` 移除当前发送者后、当前消息被拒绝且不调用宿主的情况。
- 新增/调整合成 JVM 用例覆盖：默认静默、白名单和私聊唤醒拒绝的权限提示、关键词始终提示、限频 DROP 提示，以及关键词命中的非触发公共群上下文仍静默。

| 检查 | 方法或命令 | 实际结果与限制 | 证据 |
| --- | --- | --- | --- |
| 源码静态核对 | 阅读 D07 policy、OneBot adapter、QQ 插件设置接线及对应合成用例 | 确认通知按原事件路由发送，策略拒绝不会交宿主；未构成编译或运行证据 | `plugins/qq/src/main/kotlin/com/elymbot/android/plugins/qq/QqInboundPolicy.kt`、`QqOneBotInboundAdapter.kt`、`QqPluginEntrypoint.kt` |
| QQ JVM 与 AAR | 本轮未执行；依总控安排待复跑 | 本轮源码变更后，先前的 JVM 通过结果和 AAR 摘要不再证明当前源码状态 | `plugins/qq/src/test/kotlin/com/elymbot/android/plugins/qq/QqInboundPolicyTest.kt`、`QqManagementCommandsTest.kt` |
| QQ ARM64 真机收发 | 未执行 | 登录、真实通知发送、私聊/群聊往返仍待联合验收 | 待 W07 联合验收 |

### 复验返工：拒绝通知去重（2026-11-24）

- 更新 reservation 收尾语义：拒绝分支只有确实要发送策略或限频通知时才先 `complete()` 再发送；无提示的拒绝继续 `release()`，允许消息在准入条件变化后重新提供。相同 OneBot event ID 再次到达时，有通知的拒绝（包括通知回执为 `Unknown`）不会重复发送；暂存、取消、异常、过期及宿主可重试拒绝继续按各自语义释放 reservation。
- 修正私聊提示路由断言以包含解析器提供的 `actorId`：白名单拒绝使用对应发送者 ID，唤醒词及关键词、限频通知使用 `2000`。
- 在关键词和限频 DROP 通知用例中复用完全相同的消息 payload，并模拟 `Unknown(CONNECTION_LOST)` 通知回执；断言重复入站返回 `Duplicate` 且仅产生一条通知。既有无通知拒绝的暂存回放重投用例保持原预期。用例尚未执行，等待总控联编复验。
- 总控联编反馈：52 个 QQ JVM 用例中仅 `stashReplayReevaluatesCurrentWhitelistAndReleasesDroppedReservation` 失败，原因是无通知策略拒绝被错误地标记为完成；本次改为无通知继续释放，待总控重跑确认。

| 检查 | 方法或命令 | 实际结果与限制 | 证据 |
| --- | --- | --- | --- |
| 路由与 reservation 源码核对 | 阅读 OneBot parser、adapter 及对应合成用例 | actorId 与发送者 ID 对齐；四个确定性丢弃分支在通知前完成 reservation；未构成运行证据 | `plugins/qq/src/main/kotlin/com/elymbot/android/plugins/qq/QqOneBotInboundAdapter.kt`、`OneBotMessageParser.kt`、`plugins/qq/src/test/kotlin/com/elymbot/android/plugins/qq/QqManagementCommandsTest.kt` |
| QQ JVM、AAR 与 ARM64 设备 | 本轮未执行；依总控安排待复验 | 当前源码和测试尚未联编；没有新增真实设备证据 | 待总控复验 |

- 总控复跑 `gradlew.bat :plugins:qq:testDebugUnitTest :plugins:qq:assembleDebug --offline --console=plain`，52/52 通过，无跳过。Debug AAR SHA-256 `D22A88F80A017A8278BF996CBCA13ECD4ADDC8E11F16982A3B80B7180BC5E192`。此证据覆盖原路由通知、同事件未知回执不重发、无通知暂存消息重新提交和最新设置复核。ARM64 真实运行与宿主 slash 回执另验。

## 在线运行来源恢复与资源 pin 复验（2026-11-24）

- 原 NapNeko 固定安装器 API 与 Raw 地址返回 HTTP 404 后，在线下载改用固定原 commit 的公开 Raw 字节归档；NapCat Shell 改用同 v4.18.42 的公开 release 字节归档。三项来源都不是原发布者入口，实际取回字节仍由原有 SHA-256 校验限制，未更改版本或摘要。

| 运行输入 | 来源 | 字节数 | SHA-256 |
| --- | --- | ---: | --- |
| NapCat 安装器 | [towjf fork，原 commit Raw](https://raw.githubusercontent.com/towjf/napcat-linux-installer/fb84f1f78db6ee74ab43d2410f590b844b1c9bf9/install.sh) | 12,293 | `bd6a04d3f4b3fcf1bf4f33d8f02a28c829514497484cb149cc7eb27a59dc341b` |
| NapCat launcher source | [bianyuan456 fork，原 commit Raw](https://raw.githubusercontent.com/bianyuan456/napcat-linux-launcher/557b5ea2de8867ae8d72f5054ab0cdd96c0aa9c7/launcher.cpp) | 9,992 | `4bb4c04b379cb6bb361dee6b3561ec24f85f32e13ca86a359895e9c67203e258` |
| NapCat Shell v4.18.42 | [XingYunStar release 字节归档](https://github.com/XingYunStar/NapCatQQ/releases/download/v4.18.42/NapCat.Shell.zip) | 35,967,555 | `02e5ff0f79916c1e04c81c1b4590056406fb4293f1f1300c955e71fd08b2829d` |

- 两个脚本来源的固定 commit tree 均未发现 LICENSE 文件；公开可见性不作为再分发授权依据。本次仅修复在线取回相同固定字节的地址，未将第三方脚本或运行资产捆入 App 或插件包。
- 将 `QqRuntimeAssets` 中 `runtime/root_launcher.sh` 的固定长度更新为 14,898 字节、SHA-256 更新为 `d2074a6913da3a5e3e4711fbc5c68372e376f09cfd1c9ec378ebd9bbaf82829d`。源文件含 489 个 LF、0 个 CR；Debug AAR 内 `classes.jar/runtime/root_launcher.sh` 实际抽取结果与长度及摘要 pin 完全相同。
- 独立代码复审已关闭 P1。

| 检查 | 方法或命令 | 环境与结果 | 限制 |
| --- | --- | --- | --- |
| Shell 语法 | `bash -n plugins/qq/src/main/resources/runtime/root_launcher.sh` | Git Bash 解析通过 | 静态解析，不执行运行环境 |
| QQ JVM 与 AAR | `gradlew.bat :plugins:qq:testDebugUnitTest :plugins:qq:assembleDebug --offline --console=plain` | Windows amd64；Gradle Wrapper 9.9.1、Microsoft JDK 17.0.17、AGP 9.5.1、Android SDK Build Tools 37.0.0；52/52 JVM 用例通过，0 失败、错误或跳过。Debug AAR 为 336,896 字节，SHA-256 `f1461abc9c7fcb3b27d3379279221e346e3cc82af5aa757c3eac0b4258930430` | Debug AAR 编译不证明签名发行插件在线启动 |
| AAR 资源与 pin | 从 AAR 的 `classes.jar` 抽取 `runtime/root_launcher.sh`，核对长度、SHA-256、LF/CR 和 Kotlin 常量 | 14,898 字节、SHA-256 `d2074a6913da3a5e3e4711fbc5c68372e376f09cfd1c9ec378ebd9bbaf82829d`；489 LF、0 CR；源码、AAR 资源与 Kotlin pin 一致 | 未进行 Android 设备操作 |
| 差异检查 | `git diff --check` | 通过 | — |

- 正式签名发行插件的 ARM64 在线准备仍待总控验证；本轮不构成发布制品或 QQ 登录、收发验收。
