# Feature 模块上下文

## 总体规则

- 先按 feature-first 找代码：`feature/<module>/api`、`data`、`presentation`、`runtime`、`impl`。
- feature domain 不直接依赖 Android / Compose / 旧单例。
- 空 `impl` 或 compat 聚合壳不等于生产 owner。
- 生产 wiring 走 Hilt 和 port，不恢复手写 subgraph / static bypass。

## 当前 feature 模块

| Feature | 当前职责 |
| --- | --- |
| `feature/bot` | Bot domain/data/presentation 与 selected state 相关职责 |
| `feature/config` | Config domain/data/presentation 与删除事务、profile 配置职责 |
| `feature/conversation` | Conversation API/data 和会话 repository port |
| `feature/chat` | App Chat API/presentation/runtime、消息发送、bot command 和插件命令入口 |
| `feature/cron` | Cron data/presentation/runtime、scheduled task、active capability、run now / delivery port |
| `feature/persona` | Persona domain/data/presentation 与引用保护 |
| `feature/plugin` | 插件 API/data/presentation/runtime、包结构、catalog、config/state、runtime lifecycle、toolsource |
| `feature/provider` | Provider API/data/presentation/runtime、provider capability、LLM / STT / TTS / search probe |
| `feature/qq` | QQ / OneBot / NapCat data/presentation/runtime、reverse WS、QQ command、QQ message runtime |
| `feature/resource` | Resource Center API/data/presentation 与 MCP/skill/tool resource projection |
| `feature/settings` | Settings / Me / backup presentation 入口 |
| `feature/voiceasset` | Voice asset API/data/presentation、TTS 音色和 runtime asset |

## 已确认边界重点

- `:app` 只依赖 feature presentation，不直接依赖 feature data/runtime/impl 生产主线。
- `ProviderRuntimePort` contract 位于 `feature/provider/api/.../ProviderRuntimePort.kt`，production implementation 位于 `feature/provider/runtime`。
- `feature/qq:data/runtime/presentation` 是当前 QQ 生产边界，`:feature:qq:impl` 不作为生产 owner。
- `feature/voiceasset:data/presentation` 已从 app/root 侧收口，voiceasset presentation 不依赖 provider runtime implementation。
- Plugin runtime、host capability、LLM orchestration 走 port + Hilt wiring，不走 static API。

## 主要证据

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `docs/03_Provider_Config_Bot_Persona.md`
- `docs/04_聊天会话_App内消息链路.md`
- `docs/05_QQ登录_NapCat_OneBot运行时.md`
- `docs/06_STT_TTS_声音克隆_资产.md`
- `docs/07_插件平台_模型_安装_执行_治理.md`
- `docs/08_插件UI_市场_详情_配置_工作区.md`
- `docs/10_测试入口_回归面_已知风险.md`
