# AGENTS.md

## 项目目标

ElymBot 是独立运行的 Android 个人助手。重构采用一个宿主 Agent。QQ、语音、定时任务和独立工具能力通过按需安装的插件提供。文本、视觉模型及其配置归 Core。

## 工作入口

- 从 `README.md` 了解目录和当前阶段，文档索引见 `docs/README.md`。核对功能范围时查看 `docs/product/ElymBot-重构前功能盘点.md` 的 D01–D16，核对运行边界时查看 `docs/architecture/ElymBot-Minimal-Core设计.md`。
- 目前处于前期准备和探索阶段。正式开发从用户确定项目工作方式和交互原型后开始。
- 旧 ElymBot 和 Re-UthCode 供源码与行为参考。ElymBot 原生独立运行。插件接口随实际插件需要逐步开放。
- Core 与插件使用独立物理目录。每个插件的源码、资源和测试放在自己的 `plugins/<插件标识>/` 下。Core 和其他插件不引用该插件的内部实现。代码分层建议见 `docs/architecture/ElymBot-代码组织.md`。
- 基础 APK 只包含应用和 Core 所需内容，可选插件独立发布，由用户选择是否下载、安装和删除。App 和 Core 的构建依赖中排除插件实现和专用资源。

## 任务与记录

- 用户交付任务书并要求执行时，由总控 Agent 负责拆分、分派、集成和验证。几句话即可说明范围与完成标准的任务，按用户描述直接执行。
- 两种方式都在 `docs/tasks/` 留下任务记录。使用 `docs/workflow/README.md` 中的模板和验收口径。

## 本地验证

- 正式仓库的 Gradle Wrapper 构建和检查命令见 `README.md`。工具链和依赖版本集中在 `gradle/libs.versions.toml`。
- `探索/` 是 Git 忽略的本地目录。已有 Core 探针的 JVM 和 Android 命令见 `探索/core-probe/README.md`。
- QQ 实测材料位于本地 `验证/`。记录结论时区分源码核对、合成样本与真机实测。
- Git 提交按本次改动范围明确选择文件。设备日志、构建产物、账号资料和签名文件留在本地。

## Git 远端连接

Git 直连远端失败时，通过本机 `127.0.0.1:7897` 的 HTTP 代理重试。用 `git -c http.proxy=http://127.0.0.1:7897 <子命令及参数>` 为单次命令指定代理。

```powershell
git -c http.proxy=http://127.0.0.1:7897 ls-remote --symref origin HEAD
```
