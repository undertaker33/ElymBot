# W03 插件底座与分发 — Worker Prompt

**模型** GPT-6 Luna · Max

**任务与顺序** [T02.05 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0205) · [T02.05 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0205) · [T02.06 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0206) · [T02.06 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0206)

## 前置与必读

收到 T02.01 及 W02 的早期接口。先交付制品和原生入口，W04/W05 可随之开始。W02 按 T02.05/T02.06 的真实服务完成 App 装配。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [模块设置](../../../../settings.gradle.kts) · [App 构建边界](../../../../app/build.gradle.kts) · [Android 动态代码只读要求](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading) · [Android 可执行文件限制](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)

## 写入范围

写根和共享打包构建、App 的 `build.gradle.kts`、版本目录、App 最小启动二进制及其管理、插件/分发/版本检查实现、本 Prompt Feedback。App Manifest、服务装配及 UI 交原所有者。插件私有构建先协作确认，交接后由对应插件 Worker 修改。

## 执行与验证

1. T02.05 在正式基础 APK 上先证明可从私有目录加载独立签名合成制品。插件编译引用宿主提供的契约，制品排除宿主 API 的重复类型。校验身份、版本、ABI 和宿主 API，按 Android 要求只读加载；初始化成功后切换活动实例，新版故障保留可用旧版，撤销实例同步停止旧能力。
2. 用依赖与实际 APK 检查最小原生集，向 W04 提供从 Android 安装目录启动 proot 所需的 API 与适用 ABI，验证最小 Ubuntu 命令。集中打包实现排除 rootfs、QQ、NapCat 和插件专用资产，仓库只纳入经约定的正式发行输入。
3. T02.06 从可信 GitHub 目录查询及下载，Gitee 仅在主文件传输故障时提供相同制品。检查签名、摘要和兼容性；Agent 具备查询与推荐入口。App 更新匹配包名、签名和数值版本，交系统安装器。向 W02/W06 给出只读状态、下载动作和更新入口。
4. 复验 A01、A06、A07、A12–A14、A17。先用合成目录、签名制品与网络反例覆盖分支。真实双源和候选 App 更新由 W07 汇总。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W03-插件底座与分发-feedback.md`。不记录凭据、设备标识或机器路径。

正式基础 APK 能装卸独立合成文件包，提供识别、只读、启停和回退证据。W04/W05 已接受构建、制品和宿主接口。
