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
