# 02 数据真源、Room、Repository、备份基线

> 文档层级：模块背景层
> 阅读时机：当你准备修改本编号对应模块，或需要确认其当前真源、测试入口与易错点时再读本文。
> 默认加载顺序：`README.md` -> `../AGENTS.md` -> （如需统一基线）`00_当前基线与迁移摘要.md` -> 本文。
> 交叉补读：若问题跨模块，回到相关编号文档；若需要整体执行链，再补 `11_全链路执行流程图.md`。
> 说明：本文保留独立基线；以下“当前代码基线”只服务于本文，不替代其他模块文档的独立基线。

## 1. 当前代码基线

- 基线提交：`e495263`（`Release v0.8.5`；本轮覆盖 `d060c5e..e495263`，含 `113dc12 / v0.8.2`、`a6ffa95 / v0.8.3`、`b790abce / v0.8.4`）
- Room 数据库版本：`AstrBotDatabase.version = 22`
- 数据库入口：`app/src/main/java/com/astrbot/android/data/db/AstrBotDatabase.kt`
- schema：`app/schemas/com.astrbot.android.data.db.AstrBotDatabase/22.json`

## 2. 数据层总体结构

当前数据层已进入 feature-first 迁移期：

- `data/db/*`：Room entity / dao / mapper / migration，仍保留在 root data
- `data/http/*`：HTTP 基础设施，仍保留在 root data
- `data/AppPreferencesRepository.kt`、legacy import/cache/runtime asset 等仍在 root data
- 业务 repository 已迁到 feature：
  - `feature/bot/data/FeatureBotRepository.kt`
  - `feature/config/data/FeatureConfigRepository.kt`
  - `feature/chat/data/FeatureConversationRepository.kt`
  - `feature/persona/data/FeaturePersonaRepository.kt`
  - `feature/provider/data/FeatureProviderRepository.kt`
  - `feature/plugin/data/FeaturePluginRepository.kt`
  - `feature/cron/data/FeatureCronJobRepository.kt`
  - `feature/resource/data/FeatureResourceCenterRepository.kt`
  - `feature/qq/data/*`

不要再按旧的 `data/BotRepository.kt`、`data/ConfigRepository.kt`、`data/CronJobRepository.kt` 找主实现。

## 3. Room v22 新增/关键实体

`AstrBotDatabase` 当前新增/关键项：

- `ConfigProfileEntity.includeScheduledTaskConversationContext`
  - table：`config_profiles`
  - `e495263` / `migration21To22` 新增，默认 `false`
  - 作用：控制 scheduled task 执行时是否把最近会话作为只读背景注入 `PromptAssembler`

- `PluginStateEntryEntity`
  - table：`plugin_state_entries`
  - DAO：`data/db/plugin/PluginStateEntryDao.kt`
  - 作用：承载插件 `storage.plugin` / `storage.session` 的持久化状态，主键为 `pluginId + scopeKind + scopeId + key`
- `PluginConfigSnapshotEntity`
  - table：`plugin_config_snapshots`
  - `d060c5e` 后不再通过 Room FK 对 `plugin_install_records` 做级联删除，卸载清理改由 `PluginDataCleanupService` 根据 `PluginUninstallPolicy` 显式执行

- `CronJobExecutionRecordEntity`
  - table：`cron_job_execution_records`
  - DAO：`data/db/cron/CronJobExecutionRecordDao.kt`
- `ResourceCenterItemEntity`
  - table：`resource_center_items`
  - DAO：`data/db/resource/ResourceCenterDao.kt`
- `ConfigResourceProjectionEntity`
  - table：`config_resource_projections`
  - DAO：`data/db/resource/ResourceCenterDao.kt`

已有但仍重要：

- `cron_jobs`
- `config_mcp_servers`
- `config_skills`
- plugin config snapshots / catalog / install records
- plugin state entries
- conversation / provider / persona / bot 聚合

`migration20To21` 当前做两件事：

- 重建 `plugin_config_snapshots`，去掉旧 FK 级联约束。
- 创建 `plugin_state_entries` 及其查询索引。

`migration21To22` 当前做一件事：

- 给 `config_profiles` 增加 `includeScheduledTaskConversationContext INTEGER NOT NULL DEFAULT 0`。

## 4. Resource Center 数据真源

Resource Center 是这轮新增的数据真源，用于承载：

- MCP server
- prompt skill
- tool skill
- host/tool 资源展示

核心文件：

- `feature/resource/model/ResourceCenterModels.kt`
- `feature/resource/data/FeatureResourceCenterRepository.kt`
- `feature/resource/data/ResourceCenterCompatibility.kt`
- `feature/resource/domain/ResourceCenterPort.kt`
- `data/db/resource/ResourceCenterItemEntity.kt`
- `data/db/resource/ConfigResourceProjectionEntity.kt`
- `data/db/resource/ResourceCenterMappers.kt`

兼容逻辑：

- 如果新表已有 projections，就按 Resource Center 读
- 如果某个 config 还没有 projections，就用 `ResourceCenterCompatibility.projectionsFromConfigProfile(profile)` 从旧 `ConfigProfile.mcpServers / skills` 投影
- 首次初始化时 `FeatureResourceCenterRepository.seedFromLegacyConfigTablesIfNeeded(...)` 会尝试从旧 config 表种子化资源

## 5. Cron 数据真源

Cron 现在不只是 `cron_jobs`：

- job 真源：`feature/cron/data/FeatureCronJobRepository.kt`
- job 表：`cron_jobs`
- 执行记录表：`cron_job_execution_records`
- 执行记录 DAO：`CronJobExecutionRecordDao`
- 执行协调：`feature/cron/runtime/CronJobRunCoordinator.kt`

`CronJob` 当前新增显式目标字段：

- `platform`
- `conversationId`
- `botId`
- `configProfileId`
- `personaId`
- `providerId`
- `origin`

这些字段会从旧 `payloadJson.target` 兼容读取，但新代码应优先按显式字段理解。

## 6. Repository Port / Legacy Adapter

当前多个 feature 已建立 domain port：

- `feature/provider/domain/ProviderRepositoryPort.kt`
- `feature/config/domain/ConfigRepositoryPort.kt`
- `feature/bot/domain/BotRepositoryPort.kt`
- `feature/persona/domain/PersonaRepositoryPort.kt`
- `feature/chat/domain/ConversationRepositoryPort.kt`
- `feature/cron/domain/CronJobRepositoryPort.kt`
- `feature/resource/domain/ResourceCenterPort.kt`
- `feature/plugin/domain/PluginRepositoryPort.kt`
- `feature/qq/domain/*Port.kt`

legacy adapter 只应作为过渡 seam，不能无限扩张。

`fb8e7ff` 下已经完成或继续推进语义重命名的生产类：

- `feature/bot/data/FeatureBotRepositoryPortAdapter.kt`
- `feature/chat/data/FeatureConversationRepositoryPortAdapter.kt`
- `feature/config/data/FeatureConfigRepositoryPortAdapter.kt`
- `feature/cron/data/FeatureCronJobRepositoryPortAdapter.kt`
- `feature/cron/data/FeatureCronSchedulerPortAdapter.kt`
- `feature/persona/data/FeaturePersonaRepositoryPortAdapter.kt`
- `feature/provider/data/FeatureProviderRepositoryPortAdapter.kt`
- `feature/qq/data/FeatureQqConversationPortAdapter.kt`
- `feature/qq/data/FeatureQqPlatformConfigPortAdapter.kt`
- `feature/resource/data/FeatureResourceCenterPortAdapter.kt`

`bbb2f5f` 之后 repository port 的生产装配入口已进入 Hilt，`fb8e7ff` 下还继续收口到 constructor-injected `Feature*PortAdapter`，并且不再绑定 `Legacy*Adapter`：

- `di/hilt/RepositoryPortModule.kt`
  - `BotRepositoryPort -> FeatureBotRepositoryPortAdapter`
  - `ConfigRepositoryPort -> FeatureConfigRepositoryPortAdapter`
  - `PersonaRepositoryPort -> FeaturePersonaRepositoryPortAdapter`
  - `ProviderRepositoryPort -> FeatureProviderRepositoryPortAdapter`
  - `ConversationRepositoryPort -> FeatureConversationRepositoryPortAdapter`
  - `CronJobRepositoryPort -> FeatureCronJobRepositoryPortAdapter`
  - `CronSchedulerPort -> FeatureCronSchedulerPortAdapter`
  - `ResourceCenterPort -> FeatureResourceCenterPortAdapter`
  - `QqConversationPort -> FeatureQqConversationPortAdapter`
  - `QqPlatformConfigPort -> FeatureQqPlatformConfigPortAdapter`

注意：旧文档里“这些 `Feature*PortAdapter` 还在 `Legacy*Adapter.kt` 文件里”的口径已经过期。`fb8e7ff` 下多数组件已经直接改名为 `Feature*PortAdapter.kt`，不要把 `Legacy*Adapter` 写回当前生产绑定。

`b045602` 还新增了一条容易写错的事实：Bot / Config 的 selected state 已不再是“repository 内存字段立刻切过去”。

- `feature/bot/data/FeatureBotRepository.kt` 当前通过 `AppPreferenceDao.observeValue(PREF_SELECTED_BOT_ID)` 驱动 `selectedBotId`
- `feature/config/data/FeatureConfigRepository.kt` 当前通过 `AppPreferenceDao.observeValue(PREF_SELECTED_PROFILE_ID)` 驱动 `selectedProfileId`
- `select(...)` 的真实职责是持久化选中 ID；最终 StateFlow 切换以后续 DAO flow 回流为准

后续如果改 backup / restore / selected profile 相关逻辑，必须把 `AppPreferenceDao` 一起视作数据真源。

## 7. 备份基线

App 备份已迁到：

- `core/db/backup/AppBackupRepository.kt`
- `core/db/backup/AppBackupDataPort.kt`
- `core/db/backup/AppBackupModels.kt`

`bbb2f5f` 后 `AppBackupDataRegistry` 不再是当前生产入口；不要再把 backup wiring 写成由 `AppBootstrapper.bootstrap()` 安装 registry port。

第 25 期后生产 backup 入口继续收口到 Hilt 注入服务：

- `core/db/backup/AppBackupRepository.kt`
  - `AppBackupService` 是 Hilt 注入入口，私有持有实例级 `AppBackupRepository`
  - `AppBackupRepository` 不再是生产 `object`，也不再缓存 Hilt/test override port
- `core/db/backup/ConversationBackupRepository.kt`
  - `ConversationBackupService` 是 Hilt 注入入口，私有持有实例级 `ConversationBackupRepository`
  - `ConversationBackupRepository` 不再是生产 `object`
- `app-integration/src/main/java/com/astrbot/android/di/BackupDataPortAdapter.kt`
  - `HiltAppBackupDataPort`
  - `HiltConversationBackupDataPort`
- `app-integration/src/main/java/com/astrbot/android/di/hilt/BackupModule.kt`
  - 绑定 backup data ports 与 `BackupParticipantRegistry`

当前 core backup contract 仍在：

- `core/db/backup/AppBackupDataPort.kt`
- `core/db/backup/ConversationBackupDataPort.kt`

`bbb2f5f` 下当前恢复/导入语义还有几件事必须写死：

- `AppBackupDataPort.restoreConversations(...)` 已改成 `suspend`
- `ConversationBackupDataPort` 额外提供 `importSessionsDurable(...)`
- 生产 conversation restore 会走 `ConversationRepository.restoreSessionsDurable(...)`
- 实例级 `AppBackupRepository` 的 restore stage 现在会把 `AppBackupAppState.selectedBotId` / `selectedConfigId` 纳入 diff
- restore 失败后会按 stage 逆序 rollback，conversation rollback 同样通过 durable restore 入口完成

当前 manifest 模块仍只有：

- `bots`
- `providers`
- `personas`
- `configs`
- `conversations`
- `qqLogin`
- `ttsAssets`

当前明确未纳入 App 备份：

- `plugin_config_snapshots`
- `plugin_state_entries`
- `resource_center_items`
- `config_resource_projections`
- `cron_jobs`
- `cron_job_execution_records`

并且 `configToJson(profile)` 仍未写出：

- `contextLimitStrategy`
- `maxContextTurns`
- `dequeueContextTurns`
- `llmCompressInstruction`
- `llmCompressKeepRecent`
- `llmCompressProviderId`
- `mcpServers`
- `skills`

但 `e495263` 后 `configToJson(profile)` 与 `toConfigProfile()` 已覆盖：

- `webSearchEnabled`
- `proactiveEnabled`
- `includeScheduledTaskConversationContext`

所以“Room 有字段/新表”不等于“App 备份会保留”。

## 8. 初始化边界

`AppBootstrapper.bootstrap()` 当前不再承担 repository initialize 主线。`fb8e7ff` 下：

- `RepositoryInitializationStartupChain.kt` 是过渡空壳
- repository / port / scheduler 主线依赖应按 Hilt module 与 repository 自身状态流理解
- `ProviderRepositoryWarmup` 也已经退化为 compatibility shell，`warmUp()` 当前是 no-op

`bbb2f5f` 下已经明确禁止把这些 registry 写成生产入口：

- `RuntimeContextDataRegistry`
- `AppBackupDataRegistry`
- `ConversationBackupDataRegistry`
- `ContainerBridgeStateRegistry`

Hilt 负责提供 repository port、runtime dependencies、database、transaction service；Room schema、DAO、feature repository singleton 仍是数据事实来源。不要把 `di/hilt/RepositoryPortModule.kt` 写成新的数据存储层。

## 9. 当前测试入口

- `app/src/test/java/com/astrbot/android/data/db/AstrBotDatabaseSchemaContractTest.kt`
- `app/src/androidTest/java/com/astrbot/android/data/db/AstrBotDatabaseMigrationTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/PluginConfigStateBoundaryContractTest.kt`
- `app/src/test/java/com/astrbot/android/feature/plugin/data/config/PluginHostConfigResolverTest.kt`
- `app/src/test/java/com/astrbot/android/feature/plugin/data/state/PluginStateStoreTest.kt`
- `app/src/test/java/com/astrbot/android/feature/plugin/domain/cleanup/PluginDataCleanupServiceTest.kt`
- `app/src/test/java/com/astrbot/android/data/AppBackupRepositoryCompatibilityTest.kt`
- `app/src/test/java/com/astrbot/android/data/ConversationRepositoryTest.kt`
- `app/src/test/java/com/astrbot/android/data/HiltViewModelDependenciesTransactionTest.kt`
- `app/src/test/java/com/astrbot/android/data/FeatureRepositoryPhase3DataTransactionService.kt`
- `app/src/test/java/com/astrbot/android/data/resource/ResourceCenterCompatibilityTest.kt`
- `app/src/test/java/com/astrbot/android/data/ResourceCenterRepositoryTest.kt`
- `app/src/test/java/com/astrbot/android/runtime/cron/CronJobRunCoordinatorTest.kt`
- `app/src/test/java/com/astrbot/android/feature/cron/domain/CronJobUseCasesTest.kt`
- `app/src/test/java/com/astrbot/android/feature/bot/data/FeatureBotRepositorySelectedStateTest.kt`
- `app/src/test/java/com/astrbot/android/feature/config/data/FeatureConfigRepositorySelectedStateTest.kt`
- `app/src/test/java/com/astrbot/android/data/backup/AppBackupJsonTest.kt`
- `app/src/test/java/com/astrbot/android/data/backup/AppBackupManifestAdaptersTest.kt`
- `app/src/test/java/com/astrbot/android/data/backup/ModuleBackupSupportTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/RepositoryPortSourceContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/HiltFoundationContractTest.kt`
- `app/src/test/java/com/astrbot/android/architecture/HiltExitContractTest.kt`

## 10. 易错点

- 不要把 `Feature*Repository` 的 `@Deprecated` 误解成不可用；它表示未来应通过 port/use case 访问，但当前仍是真实 singleton 实现。
- 不要把测试里的 `RepositoryCompatibilityAliases.kt` 当生产兼容层；它在 `src/test`。
- 不要把旧 `AstrBotAppContainer` / `ElymBotAppContainer` 写成当前 port 装配入口；生产入口是 `AppBootstrapper`，repository port module 是 `di/hilt/RepositoryPortModule.kt`。
- 不要把 `AppBackupDataRegistry` / `ConversationBackupDataRegistry` 写成现状；当前 production backup ports 在 `app-integration` 的 `BackupDataPortAdapter.kt` + `BackupModule.kt`，由 Hilt 注入到 backup services。
- 不要把 Bot / Config 当前 selected state 写成“立即改内存字段”；当前真源是 `AppPreferenceDao` + DAO flow。
- 不要把 `plugin_config_snapshots` 的历史 FK 级联口径写成当前事实；`d060c5e` 后插件配置/状态清理由 `PluginDataCleanupService` 按卸载策略处理。
- 不要把新增 `plugin_state_entries` 理解成 App 备份已覆盖；当前备份 manifest 仍没有插件配置/状态模块。
- 新增数据模块时，要同时考虑 Room、feature data、domain port、backup 是否覆盖、architecture contract。
