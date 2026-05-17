# 06 STT、TTS、声音克隆、资产

> 文档层级：模块背景层
> 阅读时机：当你准备修改本编号对应模块，或需要确认其当前真源、测试入口与易错点时再读本文。
> 默认加载顺序：`README.md` -> `../AGENTS.md` -> （如需统一基线）`00_当前基线与迁移摘要.md` -> 本文。
> 交叉补读：若问题跨模块，回到相关编号文档；若需要整体执行链，再补 `11_全链路执行流程图.md`。
> 说明：本文保留独立基线；以下“当前代码基线”只服务于本文，不替代其他模块文档的独立基线。

## 1. 当前代码基线

- 基线提交：`e495263`（`Release v0.8.5`；本轮覆盖 `d060c5e..e495263`，含 `113dc12 / v0.8.2`、`a6ffa95 / v0.8.3`、`b790abce / v0.8.4`）
- 语音/音频 runtime 基础能力在：`core/runtime/audio/*`
- Voice asset / runtime asset 业务已迁到：`feature/voiceasset/api`、`feature/voiceasset/data`、`feature/voiceasset/presentation`
- 第 25 期后，`feature/voiceasset/data` 与 `feature/voiceasset/presentation` 是当前生产 owner；app 内旧 `RuntimeAssetRepository`、`RuntimeAssetViewModel`、`AssetScreens` 路径不再是生产主线。
- Sherpa ONNX AAR：`libs/sherpa-onnx-1.12.31-static-jni-only.aar`
- `SherpaOnnxAssetManager.FRAMEWORK_VERSION = "1.12.30"`

## 2. 当前核心文件

- `core/runtime/audio/AndroidSystemTtsBridge.kt`
- `core/runtime/audio/OnDeviceTtsCatalog.kt`
- `core/runtime/audio/SherpaOnnxAssetManager.kt`
- `core/runtime/audio/SherpaOnnxBridge.kt`
- `core/runtime/audio/TencentSilkEncoder.kt`
- `core/runtime/audio/TtsPromptFormatter.kt`
- `core/runtime/audio/TtsStyleMappings.kt`
- `core/runtime/audio/TtsVoiceCatalog.kt`
- `feature/voiceasset/api/src/main/java/com/astrbot/android/feature/voiceasset/api/VoiceAssetPorts.kt`
- `feature/voiceasset/api/src/main/java/com/astrbot/android/feature/voiceasset/api/model/RuntimeAssetModels.kt`
- `feature/voiceasset/api/src/main/java/com/astrbot/android/feature/voiceasset/api/model/TtsVoiceModels.kt`
- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/RuntimeAssetRepository.kt`
- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/TtsVoiceAssetRepository.kt`
- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/VoiceCloneService.kt`

旧路径 `data/SherpaOnnxBridge.kt`、`data/TtsVoiceAssetRepository.kt`、`data/RuntimeAssetRepository.kt`、`core/runtime/audio/TtsVoiceAssetRepository.kt`、`core/runtime/audio/VoiceCloneService.kt`、`runtime/TencentSilkEncoder.kt` 已不是当前主路径。

## 3. 模型与 DB

Runtime asset / voice asset 模型已经迁到：

- `feature/voiceasset/api/src/main/java/com/astrbot/android/feature/voiceasset/api/model/RuntimeAssetModels.kt`
- `feature/voiceasset/api/src/main/java/com/astrbot/android/feature/voiceasset/api/model/TtsVoiceModels.kt`

相关模型：

- `RuntimeAssetId`
- `RuntimeAssetCatalogItem`
- `RuntimeAssetEntryState`
- `RuntimeAssetState`
- `ClonedVoiceBinding`
- `TtsVoiceReferenceClip`
- `TtsVoiceReferenceAsset`

TTS 资产 Room 仍在：

- `data/db/tts/*`

Repository / runtime owner 已迁到：

- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/TtsVoiceAssetRepository.kt`
- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/RuntimeAssetRepository.kt`
- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/VoiceCloneService.kt`

## 4. 运行时资产入口

Runtime asset 主入口已经从 root data 迁出：

- `feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/RuntimeAssetRepository.kt`
- Hilt binding：`feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/VoiceAssetDataModule.kt`

它是资产状态/下载入口的一部分。具体音频 runtime 实现则在 `core/runtime/audio/*`。

UI 已迁移/拆分：

- 资产入口页面：`feature/voiceasset/presentation/src/main/java/com/astrbot/android/ui/settings/AssetScreens.kt`
- voice asset 组件：`feature/voiceasset/presentation/src/main/java/com/astrbot/android/ui/voiceasset/*`
- `RuntimeAssetViewModel`：`feature/voiceasset/presentation/src/main/java/com/astrbot/android/ui/settings/RuntimeAssetViewModel.kt`

`b045602` 起的启动口径还要补一条，`bbb2f5f` 下这条仍成立：

- `AppBootstrapper.bootstrap()` 不再在启动主路径同步调 `TtsVoiceAssetRepository.initialize(application)`
- 当前会改为 `warmUpTtsVoiceAssets()` 异步预热

所以遇到启动链/TTS 资产初始化问题时，要同时看 `di/AppBootstrapper.kt`、`feature/voiceasset/data/VoiceAssetDataModule.kt` 与 `feature/voiceasset/data/TtsVoiceAssetRepository.kt`。

## 5. Provider runtime 与 STT/TTS 探测入口

Provider 页面的运行时能力当前抽到：

- contract：`feature/provider/api/src/main/java/com/astrbot/android/feature/provider/api/runtime/ProviderRuntimePort.kt`
- implementation：`feature/provider/runtime/src/main/java/com/astrbot/android/feature/provider/runtime/DefaultProviderRuntimePort.kt`

它封装：

- provider model fetch
- multimodal / native streaming rule detect 与 probe
- STT / TTS probe
- `TtsVoiceAssetPort.listVoiceChoicesFor(...)`
- `RuntimeAssetPort.ttsAssetState(...)`
- `SherpaOnnxBridge.isFrameworkReady()` / `isSttReady()`
- `ChatCompletionService.synthesizeSpeech(...)`

`ProviderViewModel` 通过 `ProviderRuntimePort` 调这些能力，不要在 ViewModel 内直接散落 `ChatCompletionService`、`SherpaOnnxBridge`、`RuntimeAssetRepository` 调用。

`fb8e7ff` 下这条又继续扩展：

- `ProviderRuntimePort` 同时暴露 `providers`、`voiceAssets`
- 音色资产的导入、保存、重命名、删除与绑定也继续经由这个 port 下沉
- 底层依赖已经是 injected `RuntimeAssetPort` / `TtsVoiceAssetPort` / `VoiceCloneRuntimePort`，不是 UI 层直连静态仓储

边界注意：voiceasset presentation 不依赖 `feature/provider/runtime` implementation；需要 provider runtime 能力时只通过 `feature/provider/api` 的 contract 暴露。

## 6. TTS prompt 与音色真源

- TTS prompt 格式化：`core/runtime/audio/TtsPromptFormatter.kt`
- TTS style mapping：`core/runtime/audio/TtsStyleMappings.kt`
- 音色资产真源：`feature/voiceasset/data/src/main/java/com/astrbot/android/feature/voiceasset/data/TtsVoiceAssetRepository.kt`
- 当前配置侧音色选择仍来自 `ConfigProfile.ttsVoiceId`

## 7. Container / Silk 相关迁移

QQ 语音发送相关的 Silk 编码当前以 `core/runtime/audio/TencentSilkEncoder.kt` 和注入的 `encodeSilkAudio` 闭包理解；不要再把已删除的 `SilkAudioEncoder.kt` 写成现状。

`0a5be12` 后 QQ runtime 侧新增语音附件 materializer：

- `feature/qq/runtime/QqAudioAttachmentMaterializer.kt`

它会把 `ConversationAttachment.base64Data` 写到 `filesDir/runtime/tts-out`，再用当前注入的 silk encoder 转成 NapCat/OneBot 可发送的 `base64://...`。改 QQ TTS 回包时要同时看：

- `feature/qq/runtime/QqStreamingReplyService.kt`
- `feature/qq/runtime/QqOneBotOutboundGateway.kt`
- `feature/qq/runtime/QqReplySender.kt`
- `core/runtime/audio/TencentSilkEncoder.kt`

Container runtime 已迁到：

- `core/runtime/container/*`

Manifest service 当前也是：

- `.core.runtime.container.ContainerBridgeService`

## 8. 备份边界

TTS backup 已迁到：

- `core/db/backup/TtsBackupArchive.kt`

App 备份 manifest 仍包含 `ttsAssets`，但不包含 Resource Center / Cron。

## 9. 当前测试入口

- `app/src/test/java/com/astrbot/android/data/db/tts/TtsVoiceAssetMappersTest.kt`
- `app/src/test/java/com/astrbot/android/data/backup/TtsBackupArchiveTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/VoiceAssetModelOwnershipContractTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/ContainerBridgeRuntimeSupportTest.kt`
- `app/src/test/java/com/astrbot/android/core/runtime/container/RuntimeCompatibilityAliases.kt`
- `app/src/test/java/com/astrbot/android/ui/viewmodel/ProviderViewModelTest.kt`

## 10. 易错点

- 不要把 Sherpa AAR `1.12.31` 和 framework marker `1.12.30` 混成一个事实。
- 新音频 runtime 基础代码应落在 `core/runtime/audio`；voice asset / runtime asset 业务应落在 `feature/voiceasset/*`，不要回到 root `data` 或 root `runtime`。
- `RuntimeAssetViewModel` 与 `AssetScreens` 已迁到 `feature/voiceasset/presentation`；root UI 不再是 asset 业务 owner。
- QQ 语音回包的附件转换入口在 `QqAudioAttachmentMaterializer`，不要把 Silk 转换逻辑重新塞回 `QqOneBotBridgeServer`。
- Provider STT/TTS 探测和语音合成入口现在优先看 `ProviderRuntimePort`。
