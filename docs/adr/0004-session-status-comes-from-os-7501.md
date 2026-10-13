# Read Session Status from the terminal's OSC custom-command listener

Status: **rejected** — 机制不成立，被 `ProgramStatusOscDispatchTest` 证伪；由 ADR 0005 取代。

## 曾经的假设

JediTerm 的 `JediEmulator.doProcessOsc` 会把未处理的 OSC 码转发给
`Terminal.processCustomCommand`，而 `Terminal.addCustomCommandListener` 是公开接口，
所以插件只要在 tab 的 `JediTerminal` 上挂一个监听器就能收到 Pi 的 OSC 7501 报告。

这个假设来自读 `doProcessOsc` 的字节码：偏移 167 处有一次
`Terminal.processCustomCommand(List)` 调用，被当成了 `default` 分支。

## 为什么是错的

那次的 switch 表是：

```
0:92  1:92  2:92  7:114  8:116  10:161  11:161  104:167  1341:167  default:199
```

调用在 **167**，即 **OSC 104 与 OSC 1341 两个分支**；`default: 199` 执行
`iconst_0; ireturn`，也就是"未处理"，什么都不做。**OSC 7501 走 default，被直接丢弃。**

已在两个版本上核对，行为一致：

| 版本 | JediEmulator 位置 | `default` 分支 |
|---|---|---|
| IC-243.21565.193 | `lib/lib-client.jar` | 丢弃 7501 |
| 2026.2（GO-262.10968.67） | `lib/intellij.libraries.jediterm.core.jar` | 丢弃 7501 |

`docs/repro/osc7501-status-spike.md` 记录了验证过程，`ProgramStatusOscDispatchTest`
用真实 `JediEmulator` + 真实 `JediTerminal` 把这条行为钉成了回归测试。

## 结论与剩下的路

**插件侧的终端监听器拿不到 Session Status**，因此状态必须来自插件自己拥有的通道。
这条决策由后续 ADR 取代，候选是：

- 插件自己持有 pty：替换建 tab 的 runner，在 `createTtyConnector` 的连接器上 tee
  字节流并解析 OSC 7501（保留 TUI，代价是修订 ADR 0002 的建 tab 路径）。
- 中继进程：`pi` 跑在插件自带的 PTY 中继下，中继解析后写文件/socket。
- 让 Pi 侧写状态文件：用 Pi 的扩展 API 报告 run 生命周期，插件监听文件
  （与终端实现完全无关，需先确认扩展 API 暴露的事件够用）。
- 只保留 Inferred Status：放弃 working/blocked/done，只按进程存活推断。

## 保留的技术事实（与结论无关，仍然有效）

- Pi 只在 interactive TUI 模式发 OSC 7501，只写 stdout，编码为
  `ESC ] 7501 ; state=<state>[:app=<app>][:kind=<kind>][:msg=<base64>] ESC \`。
- JetBrains 终端不会回答该协议的握手查询，所以必须注入 `PI_PROGRAM_STATUS=1`。
- 两种引擎都在前端进程里跑 JediTerm emulator（`ModelUpdatingTerminalDisplay`），
  但 2026.2 的终端插件已拆分 frontend/backend 模块。
- classic 引擎下 `createShellWidget` 返回包私有 bridge，`getTerminal()` 在 `this$0` 上。
