# ElymBot
一个面向 Android 的 AstrBot 原生项目，用于在移动端承载 QQ Bot 管理、模型配置、Persona 管理、对话上下文、运行时控制和 NapCat 本地桥接能力。

这个项目不是从零独立设计出来的移动端新产品，而是基于两个桌面端项目做“移动端减法”后的原生化整合：
- `https://github.com/AstrBotDevs/AstrBot`
- `https://github.com/NapNeko/NapCatQQ`

目标不是把桌面端的 Python 后端、Node WebUI、插件生态和协议栈原封不动搬进 Android，而是提炼其中适合手机场景的核心管理能力，并以 Kotlin + Compose + Android 前台服务的方式重建。

## 改名说明

本项目已由 **AstrBot-Android** 更名为 **ElymBot**。

由于插件能力上线后，原名称较容易与 AstrBot 官方项目、第三方安卓端实现及插件生态产生混淆，因此改用更独立的名称 **ElymBot**。新名称将作为项目后续统一标识使用，项目定位与核心方向不变，仍然专注于 Android 端的 Bot 管理、本地运行时整合与移动端体验优化。

历史版本中出现的 **AstrBot-Android** 均为旧名称。

## 为什么要做 ElymBot
刷到AstrBot的宣传视频，我寻思闲置设备可以拿来部署一个玩玩，但看了一圈下来发现我只有闲置的安卓手机。
通过AstrBot官方文档我找到了[AstrBot-Android-App](https://github.com/zz6zz666/AstrBot-Android-App),但奈何我的老手机比较掘，死活部署不上，所以打算写一个能兼容我老手机的安卓工程
### 通过ElymBot,你可以：
- [✔] 完全免费开源
- [✔] 实现单台安卓设备一键部署AstrBot
- [✔] 将QQ作为一个AI节点，实现基础的对话
- [✔] 拥有专门为移动端设计的UI/UX体验
- [✔] 部分Android版独特的功能（可能在桌面端插件市场中已经集成）
- [×] QQ以外的平台支持
- [×] 完整的AstrBot桌面端体验
- [×] 定期更新
ps:有空我会一直更新的

## 首次使用
1. 在右侧Realese（移动端在最下方）处下载最新的apk
2. 点击运行状态悬浮窗的启动，等待资产下载完成
3. 打开我的-QQ账号，点击去登录，首次使用请使用扫码登录
4. 配置模型api，配置bot配置，bot绑定模型和配置后即可进行QQ对话

具体教程：https://b23.tv/AdJrQ77 更新在分p里

## 功能一览：
### Bot配置

| 功能项 | 说明 |
|---|---|
| 模型配置 | 配置对话、图片转述、STT、TTS 模型 |
| STT / TTS 支持 | 支持语音输入与语音输出配置 |
| 流式输出 | 支持文字、语音流式输出 |
| TTS音色 | 支持音色克隆、音色选择 |
| 速率限制 | 正常对话速率限制、流式输出速率限制 |
| 时间感知 | 支持现实世界时间感知 |
| 管理员配置 | 支持管理员配置 |
| 群聊隔离 | 支持群聊会话隔离 |
| 唤醒词配置 | 支持唤醒词配置 |
| 回复前缀配置 | 支持回复文本前缀配置 |
| 引用与@ | 支持引用、@发送者 |
| 白名单配置 | 支持白名单配置 |
| 忽略与权限 | 支持忽略与权限配置 |
| 关键词配置 | 支持关键词配置 |
| skills | 仅支持prompt skill、tool-call skill |
| MCP Server | 仅支持stream_http |
| 知识库支持 | 准备开发 |
| 上下文策略 | 准备开发 |
| 网页搜索 | 准备开发 |
| 主动能力 | 设计阶段 |

### 模型支持

| 模型分类 | 模型支持 |
|---|---|
| Chat模型 | OpenAI Compatible、DeepSeek、Gemini、Ollama、Qwen、Zhipu、xAI |
| STT模型 | Whisper API、Xinference STT、Alibaba Bailian STT（含 Qwen）、Sherpa ONNX STT（内置） |
| TTS模型 | OpenAI TTS、Alibaba Bailian TTS（含 Qwen）、MiniMax TTS、Sherpa ONNX TTS（内置） |

### 人格

| 功能项 | 说明 |
|---|---|
| 标签分类 | 按标签分类 |
| 系统提示词 | 支持自定义系统提示词 |
| 上下文配置 | 支持自定义最大上下文（后续并入 Bot 配置上下文策略） |

### 对话

| 功能项 | 说明 |
|---|---|
| 对话管理 | 页面左滑进行对话管理 |
| 对话设置 | 侧边导航栏左滑进行对话设置：置顶 / 删除 / 重命名 |
| 图片发送 | 支持图片发送 |
| 自动同步 | 自动同步非应用内对话绑定的 Bot 和人格 |

### 指令支持

| 指令 | 说明 |
|---|---|
| /help | 查看指令帮助 |
| /stop、/start | 停用、启用 Agent |
| /agent | 列出当前 Agent 列表 |
| /ls、/switch | 对话查看、切换操作 |
| /new、/del | 创建、删除对话 |
| /rename、/reset | 重命名、重置当前对话 |
| /sid | 获取会话 ID 和管理员 UID |
| /wl、/dwl | 白名单管理 |
| /deop、/op | 管理员管理 |
| /provider 系列 | 提供商管理 |
| /model | 提供商模型管理 |
| /llm | 启停大语言模型对话功能 |
| /persona 系列 | 人格管理 |
| /stt、/tts | 启停 STT、TTS |

### 应用内支持

| 功能项 | 说明 |
|---|---|
| 语言切换 | 中英文切换 |
| 深色模式 | 深色模式切换 |
| NapCat | NapCat 开屏自启动 |
| 缓存清理 | 支持缓存清理 |
| 日志 | 日志查看（偏调试）、日志定时清理 |
| 资产管理 | 端侧资产管理（TTS / STT 相关支持） |
| 全局资产断点续传 | 仅对网络波动造成的断点生效，进程被删、缓存被清不生效 |

### 数据备份和导入

| 功能项 | 说明 |
|---|---|
| Bot备份 | 支持 Bot 备份 |
| TTS音色备份 | 支持 TTS 音色备份 |
| 人格备份 | 支持人格备份 |
| 对话备份 | 支持对话备份（支持定时备份） |
| 模型备份 | 支持模型备份（包括 API Key） |
| 配置备份 | 支持配置备份 |
| 完整备份 | 支持完整备份（除插件） |
| 插件备份 | 暂不支持 |

### 平台支持

| 功能项 | 说明 |
|---|---|
| QQ | 已支持 |
| telegram | 设计阶段 |
| 微信ClawBot | 设计阶段 |

### 插件

| 功能项 | 说明 | 进度 |
|---|---|---|
| 包结构定义、安装校验 | 定义插件结构和安装校验 | 开发完成 |
| 启用、升级、卸载 runtime lifecycle管理 | 插件安全，启停状态管理 | 开发完成 |
| 消息入口 | `adapter_message_handler`、`command`、 `command_group`、`regex`| 开发完成 |
| 过滤器 | `event_message_type`、`permission_type`、`platform_adapter_type`、`custom_filter`| 开发完成 |
| 生命周期 | `on_astrbot_loaded`、`on_platform_loaded`、`on_plugin_loaded`、`on_plugin_unloaded`、`on_plugin_error`| 开发完成 |
| LLM 流水线 | `on_waiting_llm_request`、`on_llm_request`、`on_llm_response`、`on_decorating_result`、`after_message_sent`| 开发完成 |
| 工具体系 | `llm_tool`、`on_using_llm_tool`、`on_llm_tool_respond`| 开发完成 |
| 市场接入 | 采用中央仓库 + 分仓库管理安卓端可拿到的插件 | 开发完成 |



## 当前工程结构概览

```text
app/
  schemas/                         Room schema 导出文件

  src/main/java/com/astrbot/android/
    core/                          跨模块核心能力
      common/                      通用工具、日志、Profile 守卫
      db/                          备份、事务等核心数据库能力
        backup/                    应用 / 会话备份导入导出
        transaction/               数据事务边界
      di/                          核心初始化与依赖入口
      network/                     通用网络能力
      runtime/                     核心运行时能力
        audio/                     TTS / 语音资产 / 编码桥接
        container/                 容器运行时、NapCat、rootfs、安装控制
        context/                   RuntimeContext、Prompt 组装、资源投影
        llm/                       LLM 调用端口、媒体服务、Provider 探测
        network/                   Runtime 网络传输
        search/                    Unified Search、搜索 Provider、本地搜索兜底
        secret/                    运行时 secret 存储
        session/                   会话锁
        tool/                      工具合约、工具值归一化

    data/                          Room 与兼容数据层
      db/                          Room Database、DAO、实体、迁移
        bot/                       Bot 聚合数据
        config/                    配置聚合数据
        conversation/              会话聚合数据
        core/                      DB migration / schema reset
        cron/                      定时任务数据
        download/                  下载任务数据
        persona/                   Persona 聚合数据
        plugin/                    插件安装、配置、状态数据
        provider/                  Provider 聚合数据
        resource/                  Resource Center 数据
        tts/                       TTS 音色资产数据
      http/                        HTTP 客户端与请求封装

    di/                            Hilt 与启动链
      hilt/                        Hilt Module、端口绑定
      startup/                     AppStartupChain、启动阶段编排

    download/                      下载任务、断点续传、前台服务、通知

    feature/                       Feature-first 业务模块
      bot/                         Bot 数据、领域、展示、运行时
      chat/                        App Chat 数据、领域、模型、展示、运行时
      config/                      配置数据、领域、展示、运行时
      cron/                        定时任务数据、领域、展示、运行时
      persona/                     Persona 数据、领域、展示、运行时
      plugin/                      插件数据、领域、模型、展示、运行时
      provider/                    Provider 数据、领域、模型、展示、运行时
      qq/                          QQ / OneBot 数据、领域、展示、运行时
      resource/                    Resource Center 数据、领域、模型、展示、运行时
      settings/                    设置展示层
      voiceasset/                  声音资产展示层

    model/                         跨模块通用模型

    ui/                            应用壳层与通用 Compose UI
      app/                         应用壳、顶栏、全局视觉辅助
      common/                      通用 Compose 组件
      navigation/                  全局 route、导航、转场
      settings/                    设置入口兼容 UI
      theme/                       Material typography / theme 定义
      viewmodel/                   兼容 ViewModel 入口

  src/main/assets/                 运行时脚本、rootfs、模型等资产
  src/main/jniLibs/                native 运行时资产
  src/main/res/                    字符串、主题、图标、xml 等 Android 资源

  src/test/java/com/astrbot/android/
    architecture/                  架构边界与迁移护栏测试
    core/                          core runtime / db 单元测试
    data/                          数据层、Room schema、Repository 测试
    di/                            DI / Hilt / 启动链测试
    download/                      下载与断点续传测试
    feature/                       各 feature 模块测试
    model/                         模型与协议解析测试
    runtime/                       运行时、插件、OneBot、搜索测试
    testsupport/                   测试辅助
    ui/                            UI presentation / ViewModel 测试

  src/androidTest/java/com/astrbot/android/
    data/db/                       Room migration instrumentation test
    ui/                            页面 smoke test

docs/                              背景文档仓库
changelogs/                        按 v0.x.x 聚合的 What's Changed

gradle/                            Gradle wrapper 配置
tools/                             本地工具脚本
artifacts/                         构建 / 测试产物
logs/                              本地日志

README.md                          项目说明
build.gradle.kts                   根 Gradle 配置
settings.gradle.kts                Gradle module 配置
gradle.properties                  Gradle 属性
gradlew / gradlew.bat              Gradle wrapper
LICENSE                            开源协议


```

## 构建要求

- Android Studio Ladybug 或更新版本
- JDK 17+
- Android SDK Platform 34
- Android Build Tools 与 AGP 8.5+ 兼容

## 已知问题
1. napcat runtime 进度卡 96%。多次点击启动安卓进程冲突导致，临时解决：开始下了就别点启动了，我发现下载不是最大的问题，安装才是；安装取决于手机性能，以8Elite Gen5为基准(约三分钟)，cpu性能每弱20%约慢5分钟(瞎说的，我就两台手机没法测)。
2. QQ登录态不稳，没招了，安卓底层省电这块

## 差异说明

`ElymBot` 基于 `AstrBot-master` 与 `NapCatQQ-main` 的核心能力思路构建，但并不是两者的 Android 直接移植版。

它保留了 AstrBot 在 Bot、Provider、Persona、聊天、日志和设置上的核心管理体验，同时吸收了 NapCat 在 QQ 登录、运行时控制与 OneBot 桥接上的关键能力，并通过 Kotlin + Jetpack Compose + Android 前台服务的方式，重构为一个面向手机端的原生控制台与本地桥接运行层。

因此，这个项目的目标不是复刻完整桌面端，而是在 Android 场景下提供一个更聚焦、更本地化、更适合移动设备使用的 AstrBot + NapCat 整合方案。


## 致谢

感谢以下开源项目为本 App 提供基础支持：
- [NapCatQQ](https://github.com/NapNeko/NapCatQQ)：高效稳定的 QQ 协议适配器
- [Astrbot](https://github.com/AstrBotDevs/AstrBot)：强大的一站式 Agentic 个人和群聊助手
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)：轻量化的安卓端侧TTS框架
- [searxng](https://github.com/searxng/searxng): 强大的自托管、开源、注重隐私的元搜索引擎。
- **[crawl4ai](https://github.com/unclecode/crawl4ai)**：高效的面向 AI 工作流的网页爬取与内容抽取框架。

## 许可证说明
本项目采用AGPL3.0开源协议
