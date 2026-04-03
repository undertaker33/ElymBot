# Meme Manager Android Sample

这个样例只演示 Android 插件协议下的独立样例资产，不改宿主 `app/src/main` 业务代码，也不会把图片写进宿主容器。

## 资产位置

- catalog fixture: `artifacts/plugins/meme-manager-sample/catalog/meme-manager.sample.catalog.json`
- sample packages:
  - `artifacts/plugins/meme-manager-sample/packages/meme-manager-1.0.0.zip`
  - `artifacts/plugins/meme-manager-sample/packages/meme-manager-1.1.0.zip`

两个 zip 都继续保留：

- `manifest.json`
- `assets/`
- `resources/`
- `resources/admin/seed.txt`

## 打包进 zip 的真实图片

图片来源只读参考目录：

- `C:\Users\93445\Desktop\Astrbot\插件\astrbot_plugin_meme_manager-main\memes\**`

为了让仓库保持轻量，样例只复制少量代表性图片。

### `meme-manager-1.0.0.zip`

- `resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg`

### `meme-manager-1.1.0.zip`

- `resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg`
- `resources/memes/angry/0FFD1AFA5CD0866B1065AEAF45D4066A.jpg`
- `resources/memes/baka/file_5600919.jpg`

## `index.json` 约定

zip 内的 `resources/memes/index.json` 现在不再只是类别名，而是明确指向 zip 内真实图片文件：

- `categories[].items[].file` 保存相对路径，例如 `resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg`
- `triggers[]` 描述命令如何命中类别

当前样例触发规则：

- `1.0.0`:
  - `/meme angry` 命中 `angry`
  - `/meme` 无参数时回退到 `defaultCategory = angry`
- `1.1.0`:
  - `/meme angry` 命中 `angry`
  - `/meme_baka` 命中 `baka`

## 当前样例如何证明回复链路

样例 runtime test 会在测试内注册一个 `PluginRuntimePlugin` handler，然后：

1. 从已安装并解包的 sample zip 读取 `resources/memes/index.json`
2. 根据 `triggerMetadata.command` 和 `message.contentPreview` 解析命中的 category
3. 取出 category 下第一张真实图片
4. 返回 `MediaResult(items = listOf(PluginMediaItem(...)))`
5. 断言 `PluginMediaItem.source` 指向解包后的真实文件，且该文件确实存在

这意味着测试已经覆盖：

- 命令触发
- 命中 zip 内资源索引
- 解析到真实图片文件
- 返回更接近表情回复链路的媒体结果

admin path 继续保留，仍通过 `OnPluginEntryClick -> SettingsUiRequest` 验证。

## 相关测试

建议最少运行下面三组样例测试：

```bash
./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleCatalogTest
./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleInstallAndUpgradeTest
./gradlew.bat :app:testDebugUnitTest --tests com.astrbot.android.runtime.plugin.samples.MemeManagerSampleRuntimeAndAdminPathTest
```

它们分别验证：

- catalog fixture 可同步并解析出两个版本
- sample zip 可安装，且解包后确实包含 meme 图片资源
- runtime 主链路可返回 `MediaResult`
- admin 管理入口仍可返回 `SettingsUiRequest`
