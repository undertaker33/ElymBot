# 架构

## 当前状态

项目级文档基线已经通过 `uth-docs` module-governance 修复完成。当前架构事实入口不是旧 `docs/00` 到 `docs/11`，而是 `docs/context/` 下的两位编号模块上下文。

本文件只做项目级导航，不替代编号 context。

## 当前模块边界

| Module | Responsibility | Not responsible for |
| --- | --- | --- |
| `:app` | Android Manifest、Application、Activity、app shell、Compose root、navigation root、theme/resources、app chrome 与薄 Android adapter | feature data/runtime/impl 生产主线 |
| `:app-integration` | Hilt bindings、adapter、contributor、跨模块 wiring | feature presentation、Compose UI、长运行 runtime loop |
| `:architecture-tests` | 独立 architecture contract test 入口 | 业务实现 |
| `:build-logic` | Gradle convention 与模块组任务支持 | 业务实现 |
| `:core:*` | shared db、runtime、network、logging、ui、context、tool、search、container、audio 等基础能力 | 反向依赖 feature |
| `:download:*` | download API 与 implementation、下载任务和断点续传 | 其他 feature 业务状态 |
| `:feature:*` | feature-first 业务模块的 api/data/presentation/runtime/impl 分层 | core 基础设施职责 |

## 核心约束

- 生产依赖注入唯一合法方法是 Hilt。
- `core/**` 不依赖 `feature/*`。
- feature domain 不直接依赖 Android / Compose / 旧单例。
- `:app` 不直接依赖 feature data/runtime/impl 生产主线。
- 共享 runtime context 以 `core/runtime-context` 和 Hilt wiring 为事实入口。
- 架构合同入口为 `architectureCheck`，其中包含 `:architecture-tests:test`。

## 细节入口

- 模块拆分和完成态：`docs/context/00-模块拆分.md`
- app shell：`docs/context/02-应用壳层与集成.md`
- core foundation / DB：`docs/context/03-核心基础与数据库.md`
- core runtime：`docs/context/04-核心运行时.md`
- feature modules：`docs/context/06-Provider配置Bot与Persona.md` 到 `docs/context/12-语音资产与音频.md`
- 旧模块文档归档：`docs/archive/pre-uth-docs/docs-00-11/`

## 当前风险

- 本轮 `uth-docs` 未运行 Gradle 构建或测试；验证命令只作为入口事实记录。
- 旧背景文档中的提交号、版本号、路径和阶段描述是历史口径；当前状态以 `docs/current-state.md`、`docs/context/README.md` 和编号 context 为准。
