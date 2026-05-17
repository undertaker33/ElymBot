# docs/00 到 docs/11 分类

更新时间：2026-05-17 21:05 +08:00

## 规则

- 本分类只处理旧 `docs/00_*.md` 到 `docs/11_*.md` 的当前入口地位。
- 代码事实优先，旧文档只作为历史辅助证据。
- `docs/ONB26051701-pre-uth-docs-backup.zip` 中已经确认存在旧 `docs/00` 到 `docs/11` 的原路径。
- 旧模块正文已归档到 `docs/archive/pre-uth-docs/docs-00-11/`，不再保留在 `docs/` 根目录。

## 分类结果

| 原路径 | 当前路径 | Decision | Current entry? | Replacement |
| --- | --- | --- | --- | --- |
| `docs/00_当前基线与迁移摘要.md` | `docs/archive/pre-uth-docs/docs-00-11/00_当前基线与迁移摘要.md` | 已归档为辅助总览证据 | 否 | `docs/context/00-模块拆分.md`, `docs/current-state.md` |
| `docs/01_应用入口_构建_导航壳层.md` | `docs/archive/pre-uth-docs/docs-00-11/01_应用入口_构建_导航壳层.md` | 已归档为 app-shell 辅助证据 | 否 | `docs/context/02-应用壳层与集成.md` |
| `docs/02_数据真源_Room_Repository_备份基线.md` | `docs/archive/pre-uth-docs/docs-00-11/02_数据真源_Room_Repository_备份基线.md` | 已归档为 core/db 与 backup 辅助证据 | 否 | `docs/context/03-核心基础与数据库.md`, `docs/context/11-资源设置备份.md` |
| `docs/03_Provider_Config_Bot_Persona.md` | `docs/archive/pre-uth-docs/docs-00-11/03_Provider_Config_Bot_Persona.md` | 已归档为领域模块辅助证据 | 否 | `docs/context/06-Provider配置Bot与Persona.md` |
| `docs/04_聊天会话_App内消息链路.md` | `docs/archive/pre-uth-docs/docs-00-11/04_聊天会话_App内消息链路.md` | 已归档为 chat/conversation 辅助证据 | 否 | `docs/context/07-聊天与会话.md`, `docs/context/04-核心运行时.md` |
| `docs/05_QQ登录_NapCat_OneBot运行时.md` | `docs/archive/pre-uth-docs/docs-00-11/05_QQ登录_NapCat_OneBot运行时.md` | 已归档为 QQ/NapCat/OneBot 辅助证据 | 否 | `docs/context/08-QQ_NapCat_OneBot.md`, `docs/context/05-下载与容器资产.md` |
| `docs/06_STT_TTS_声音克隆_资产.md` | `docs/archive/pre-uth-docs/docs-00-11/06_STT_TTS_声音克隆_资产.md` | 已归档为 voiceasset/audio 辅助证据 | 否 | `docs/context/12-语音资产与音频.md`, `docs/context/04-核心运行时.md`, `docs/context/05-下载与容器资产.md` |
| `docs/07_插件平台_模型_安装_执行_治理.md` | `docs/archive/pre-uth-docs/docs-00-11/07_插件平台_模型_安装_执行_治理.md` | 已归档为 plugin runtime 辅助证据 | 否 | `docs/context/09-插件平台.md` |
| `docs/08_插件UI_市场_详情_配置_工作区.md` | `docs/archive/pre-uth-docs/docs-00-11/08_插件UI_市场_详情_配置_工作区.md` | 已归档为 plugin UI 辅助证据 | 否 | `docs/context/09-插件平台.md` |
| `docs/09_设置_日志_运行时清理_备份入口.md` | `docs/archive/pre-uth-docs/docs-00-11/09_设置_日志_运行时清理_备份入口.md` | 已归档为 settings/resource/backup 辅助证据 | 否 | `docs/context/10-Cron运行时.md`, `docs/context/11-资源设置备份.md` |
| `docs/10_测试入口_回归面_已知风险.md` | `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md` | 已归档为验证辅助证据 | 否 | `docs/context/01-验证构建治理.md` 与各模块 context 的测试入口段落 |
| `docs/11_全链路执行流程图.md` | `docs/archive/pre-uth-docs/docs-00-11/11_全链路执行流程图.md` | 已归档为辅助图 | 否 | `docs/context/00-模块拆分.md` 与目标模块 context |

## 当前入口替代关系

- 当前状态入口：`docs/current-state.md`
- 当前上下文索引：`docs/context/README.md`
- 当前拆分报告：`docs/context/00-模块拆分.md`
- 旧文档分类：`docs/context/old-doc-classification.md`

## 使用规则

- 旧 `docs/00` 到 `docs/11` 不再作为默认加载对象。
- 需要历史辅助证据时，按本表打开 archive 路径。
- 归档旧文档中的旧提交号、旧路径、旧版本号和早期验证结果不得覆盖编号 context。
- 新模块事实只写入 `docs/context/` 的编号 context 或当前状态入口，不回写旧模块正文。
