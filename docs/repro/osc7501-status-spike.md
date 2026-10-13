# Spike 验证：OSC 7501 Session Status 能否到达插件（ADR 0004）

> **状态：已完成。结论是失败——ADR 0004 被证伪。**
> 解析器、emulator 分发、shell 注入都有测试覆盖且全部通过；
> 「监听器能收到 OSC 7501」这条被 `ProgramStatusOscDispatchTest` 明确否定。

## 目的

判定 ADR 0004 的判定标准：**classic 与 block 两种终端引擎下，Pi 的 Program Status
报告（OSC 7501）都能到达插件挂的 `TerminalCustomCommandListener`**。

## 结果

| 假设 | 结果 | 证据 |
|---|---|---|
| H1 监听器能收到 7501 | **失败** | `ProgramStatusOscDispatchTest`：真实 `JediEmulator` + 真实 `JediTerminal` 下监听器收到 0 条。`doProcessOsc` 的 `default:199` 直接丢弃 7501，只有 OSC 104/1341 被转发 |
| H1b 探针能否挂到真实 widget | 通过但无用 | 集成测试中探针能在真实 `JBTerminalWidget$TerminalWidgetBridge` 上挂载；挂上之后也不会有报告 |
| H2 block 引擎 | 未验证 | 单测环境下 `ExperimentalUI.isNewUI()` 为 false，`LocalBlockTerminalRunner` 会退回 classic widget |
| H3 `PI_PROGRAM_STATUS=1` 注入 | 语法由 `PiShellTest` 覆盖 | 前缀渲染正确；但因 H1 失败，报告无人接收 |
| H4 不渲染垃圾 | 通过 | 7501 既不转发也不落屏（`ProgramStatusOscDispatchTest`） |

## 机制证据（离线，来自 jar 字节码）

1. **转发点**：`com.jediterm.terminal.emulator.JediEmulator.doProcessOsc`：

```
0:92  1:92  2:92  7:114  8:116  10:161  11:161  104:167  1341:167  default:199
```

   `Terminal.processCustomCommand` 在 **167**，即 **OSC 104 与 OSC 1341 的分支**；
   `default: 199` 是 `iconst_0; ireturn`——未处理，直接丢弃。**OSC 7501 走 default。**
2. **两个版本一致**：IC-243.21565.193（`lib/lib-client.jar`）与 2026.2
   （`lib/intellij.libraries.jediterm.core.jar`）行为相同——即使用户跑在 GoLand 2026.2
   上，监听器同样收不到。
3. **2026.2 已拆分前后端**：终端插件出现 `intellij.terminal.backend.jar` /
   `frontend.jar` / `backend.split.jar`，这是 ADR 0002 里提到 "RD proxy connector" 的来源。
4. **Pi 侧**：OSC 7501 只在 interactive TUI 模式发出、只写 stdout；终端不会回答握手查询，
   所以必须注入 `PI_PROGRAM_STATUS=1`；报文格式
   `ESC ] 7501 ; state=<state>[:app=<app>][:kind=<kind>][:msg=<base64>] ESC \`。
5. **classic 引擎的 bridge**：`createShellWidget` 返回包私有 bridge
   （`JBTerminalWidget$TerminalWidgetBridge`），它没有 `getTerminal()`，只有 `this$0`
   指回真正的 widget —— 这条与 ADR 0002 一致，已被集成测试证实。

## 结论

监听器方案作废：**插件侧无法观察到 OSC 7501**，Session Status 必须来自插件自己拥有的通道。
四个候选记录在 ADR 0004，由下一步决策定夺：

- 插件自持 pty（替换建 tab 的 runner，在连接器上 tee）——唯一不牺牲 TUI 的路；
- 中继进程（`pi` 跑在自带 PTY 中继下，解析后写文件/socket）；
- 让 Pi 侧写状态文件（用 Pi 扩展 API，与终端实现完全无关，需先确认事件够用）；
- 只做 Inferred Status（诚实降级，放弃 working/blocked/done）。

## 教训

我上一轮"确认"这条机制时，只看到偏移 167 处有 `processCustomCommand` 调用就认定它是
`default` 分支，没有核对 switch 表。**读字节码不能只看"调用了什么"，必须看清"哪条分支在调用它"。**
这个错误由 `ProgramStatusOscDispatchTest` 抓出，该测试因此保留为回归证据：
将来若有人想再走监听器方案，必须先让它变红。
