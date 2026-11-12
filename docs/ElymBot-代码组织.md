# ElymBot 代码组织

已确定的约束是 Core 和各插件物理隔离，`探索/` 与 `验证/` 留在本地。当前 Gradle 骨架包含 `:app`、`:core`、`:plugins:qq` 和 `:plugins:scheduled-tasks`。其余目录随实际功能建立。

## 先按职责定位，再看职责内部的分层

```text
app/                       Android 启动、导航和依赖装配
features/                  应用自身的功能，例如人格、会话、设置
core/                      Agent 执行、上下文装配、文本和视觉模型
plugins/
  qq/                      QQ 安装登录、OneBot、收发和设置
  scheduled-tasks/         任务配置、调度和运行记录
docs/                      产品决定、设计与工程说明
```

每个插件拥有自己的源码、资源和测试目录，例如 `plugins/qq/src/main/kotlin/` 和 `plugins/qq/src/test/kotlin/`。插件较复杂时，可在内部按实际职责分为 `login/`、`onebot/`、`runtime/` 和 `ui/`。应用功能内部可按需要用 `ui/`、`data/` 组织，跨页面复用的业务规则再单独提取。Core 内部按 `agent/`、`context/`、`model/` 等职责定位。目录与 Kotlin 包结构保持对应。 [Android 应用架构](https://developer.android.com/topic/architecture) · [Kotlin 源码组织](https://kotlinlang.org/docs/coding-conventions.html#source-code-organization)

例如修改 QQ 登录时，从 `plugins/qq/` 就能找到登录实现、设置和对应测试。Spring MVC 的 Controller、Service、Repository 分工适合帮助理解请求入口、业务处理和数据访问。ElymBot 的输入来自界面、消息通道和定时任务，代码导航以功能归属为主，内部再根据职责选择所需的层次。 [Android 模块化模式](https://developer.android.com/topic/modularization/patterns)

## 把插件边界落实到构建

- **源码边界。** 每个插件使用自己的 `plugins/<插件标识>/`。Core 不引用具体插件，插件之间不直接依赖彼此的实现。插件与宿主之间只通过实际接入需要的契约交互。
- **编译边界。** 建议每个插件拥有独立的 Gradle 构建边界和独立制品。应用装配 Core 和插件加载入口，基础 App 制品不打包可选插件实现。模块内部优先收窄可见性，只有真实跨模块使用的类型才成为契约。
- **拆分粒度。** `agent/`、`context/`、`model/` 可先作为 Core 内部的 Kotlin 包。需要强制依赖方向、独立构建或隔离平台依赖时，再升级成 Gradle 子模块。小功能也可先放在所属功能的包里。
- **交付边界。** 用户先安装基础 APK，再按需下载、安装和删除单个插件。基础 APK 中排除插件的实现和专用资源。现有模块生成的 AAR 用于编译验证，用户下载的插件包和加载方式仍需验证。含原生组件的 QQ 插件另需验证原生制品的交付方式。

## 参考项目

- **Now in Android** 展示如何用 App 模块装配功能，并将页面状态及实现放在所属功能模块。ElymBot 可借鉴功能归属和依赖方向。 [官方示例](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)
- **Thunderbird for Android** 展示了较大 Kotlin Android 项目如何用独立模块和明确的接口边界控制依赖。ElymBot 可借鉴它的可见性约束，等实际出现跨模块接口再考虑独立 API 模块。 [项目模块文档](https://thunderbird.github.io/thunderbird-android/docs/latest/architecture/module-structure.html)
