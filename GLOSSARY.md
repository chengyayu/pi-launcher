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

**Session Status**:
pi 自己报告的一个 Pi Session 的执行状态，取值为 working / blocked / done / error / idle。它描述 pi 在做什么，不描述插件认为 pi 在做什么。报告里还可能带两样东西：pi 对自己状态的解释（对话框标题、错误首行）与它此刻的活动（正在跑的工具调用）；两者的区别是前者在问你要东西，后者只是它在忙。
_Avoid_: 状态、running 状态、busy 状态

**Status Source**:
一个 Pi Session 的 Session Status 从其获得的来源。当前唯一的 Status Source 是 Status Extension 写出的报告文件。
_Avoid_: 状态通道、事件源、OSC

**Status Extension**:
随 Pi 启动注入进该进程的一个 pi 扩展，它把 Session Status 写进 Status Source。它让插件从进程内部观察 pi，而不是从终端外面猜。
_Avoid_: reporter 脚本、状态插件

**Inferred Status**:
插件在 Status Source 不可用时，从进程存活与否推断出的降级状态。它与 Session Status 可信度不同，必须可区分地呈现。
_Avoid_: status（不加限定时）、推定状态

**Session Dashboard**:
展示 Session Registry 中每个 Pi Session 的 Session Status 的单一面板。
_Avoid_: 控制面板、状态面板、dashboard
