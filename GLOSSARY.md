# Pi Launcher

JetBrains 插件，用于在 IDE 的 Terminal 工具窗口中启动和驱动 [Pi coding agent](https://pi.dev)。

## Language

**Pi Session**:
一个 Pi tab 内运行的一个 `pi` 进程及其被插件跟踪的生命周期。一个 project 可以同时有多个 Pi Session。
_Avoid_: Pi 实例、pi 进程（口语场景）、Pi tab（指逻辑实例时）

**Terminal Tab**:
Terminal 工具窗口中的一个 tab 页，是 Pi Session 的宿主 UI。一个 Terminal Tab 至多承载一个 Pi Session。关闭 tab 即终结其上的 Pi Session。
_Avoid_: 终端窗口、console

**Terminal Engine**:
JetBrains 终端的两种实现：classic（JediTerm）与 block（2024.3+ 默认）。插件通过引擎无关的 API 同时支持两者。
_Avoid_: 终端类型、新/旧终端

**Session Registry**:
一个 project 内所有 Pi Session 的集合。插件通过它而不是单一字段来跟踪全部会话。
_Avoid_: session 列表、sessions map（指概念时）

**Active Session**:
Send to Pi 与聚焦操作默认指向的 Pi Session，即最近被聚焦或创建的运行中的会话。
_Avoid_: 当前会话（泛指时）、default session

**Launch**:
启动 Pi：若无 Active Session 则创建新会话，否则聚焦已有会话。
_Avoid_: start、open、run

**Send to Pi**:
把编辑器选区或文件以 `@path#L10-25` 引用的形式插入 Active Session 的输入。
_Avoid_: 发送给 pi、提交代码
