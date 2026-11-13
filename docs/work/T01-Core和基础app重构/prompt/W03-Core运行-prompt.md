# W03-Core运行

负责 T01.03、T01.04。T01.01 交接后，先实现上下文再接通 Agent、工具来源和指令。用合成适配器验证。

实施前读 [AGENTS.md](../../../../AGENTS.md)、[任务书](../T01-Core和基础app重构.md)、[Spec](../T01-Core和基础app重构-spec.md)和 [Tasks](../T01-Core和基础app重构-tasks.md)。按 Tasks 的交接契约工作，读 [详细设计](../T01-Core和基础app重构-详细设计.md)的对应章节。

只改 Core context、agent、tool、command 包及其测试。共享契约由 W01 修改。 遇到影响已确认范围、语义或验收的冲突，保留证据，交 W01 处理。依照 [Checklist](../T01-Core和基础app重构-checklist.md)核验负责的 T01.03、T01.04，未通过或未运行的项保留原因。执行后写入 `../feedback/W03-Core运行-feedback.md`，包括实际改动、操作或命令、环境与产物、结果和对应条目。

交给 W04 历史读取与摘要接口、执行事件及指令结果。提交压缩、HistoryRead、ReAct 的可复现证据。
