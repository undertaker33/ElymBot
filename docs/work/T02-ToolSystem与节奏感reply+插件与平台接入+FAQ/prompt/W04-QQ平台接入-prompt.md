# W04 QQ 平台接入 — Worker Prompt

**模型** GPT-6 Luna · Max

**任务与顺序** [T02.07 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0207) · [T02.07 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0207) · [T02.08 Tasks](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-tasks.md#t0208) · [T02.08 Checklist](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-checklist.md#t0208)

## 前置与必读

收到 W02 的平台注册、入站、绑定授权和回执接口，以及 W03 的制品与原生启动入口。对照任务书确认的 ARM64、单账号和在线准备路线。

[AGENTS](../../../../AGENTS.md) · [工作包规则](../../../rules/WorkPackageRules.md) · [任务书](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ.md) · [Spec](../T02-ToolSystem与节奏感reply+插件与平台接入+FAQ-spec.md) · [QQ 模块构建](../../../../plugins/qq/build.gradle.kts) · [QQ 产品决定](../../../product/ElymBot-重构前功能盘点.md#五需要用户决策的事项) · [宿主架构](../../../architecture/ElymBot-Minimal-Core设计.md)

## 写入范围

只写 `plugins/qq/` 及本 Prompt Feedback，遵循 W03 制品交接。App 原生包、Manifest/宿主路由、通用投递及页面由原所有者维护。测试账号和扫码由获授权用户提供。

## 执行与验证

1. T02.07 复用实测的 ARM64 在线准备路径。QQ 插件负责 Ubuntu/QQ/NapCat 按需输入、失败重试和连接管理，原生程序走基础 APK 安装目录。验证宿主升级时路径修复、二维码刷新、登录退出、断开重连和 OneBot 就绪。
2. T02.08 实作 OneBot 入站与回执适配、真实去重、D07 全套准入及唤醒/屏蔽、管理员绕过、命令、限频丢弃与暂存重放。按授权保留群公共材料及隔离输入，把规范消息、来源和权限语义交宿主。QQ 作为定时任务外部投递渠道，只发已绑定的本人私聊。
3. 验证 A05–A09、A17 的插件环节。将文本、图片、语音、视频和文件事件送入正式 OneBot 适配入口验证规范化，核对渠道不支持的投递结果，向 W02 交接测试样本。真实设备扫码、私聊、群聊与锁屏往返和 W07 合并验证，记录真实及合成证据。

## Feedback 与交接

依 [Feedback 模板](../../../templates/Feedback模板.md) 将实际改动、验证入口和结果、Checklist 覆盖、已知限制和补验要求记录到 `feedback/W04-QQ平台接入-feedback.md`。不记录凭据、设备标识或机器路径。

向 W02/W06 交付入口、账号及连接状态、登录动作、规范化消息和送达回执；交 W07 可安装制品及实际试验条件。
