# 复现报告：VFS 变更处理导致 GoLand 卡死（0.1.4）与修复验证（0.1.5）

> **说明**：本文档记录本次重构之前的排查过程 —— 版本号仍是 0.1.x，插件当时名为
> "Pi Agent Launcher"。文中的旧类名（`PiDiffWatcher` 等）已移除，
> 保留此文是为了留下冻结根因的原始证据。

## 环境

| 项 | 值 |
|---|---|
| IDE | GoLand 2026.2.3 (GO-262.10968.67) |
| OS | macOS 26.6.1 (Apple Silicon) |
| JBR | 25.0.4 |
| 测试项目 | `pi-freeze-test`（隔离的最小 Go 项目） |
| 插件设置 | `autoOpenFiles = false`（与用户实际配置一致） |

对照方法：**完全相同的操作**，只换插件版本。

## 复现步骤

1. 打开项目 `pi-freeze-test`
2. 工具栏 **π** 启动 Pi（Terminal 出现 `Pi` 标签）
3. 打开 `lib/b.go` → `Cmd+A` → 右键 **Send to Pi**
   - 该动作调用 `PiDiffWatcher.snapshotFile(b.go)` + `startWatching()`，激活 diff 路径
4. 清空 Pi 输入框（不让模型执行）
5. 对同一个文件 `lib/b.go` 连续写盘 N 次（每次约 0.3~0.4s）
6. 保持 GoLand 前台，观察

> 用脚本直接写盘而非让模型编辑，是为了让两次对照的输入完全一致、可重复。

## 对照组结果

| 指标 | **0.1.4（旧）** | **0.1.5（修复后）** |
|---|---|---|
| 写盘次数 | 15 | 15（另加一轮 35 次压测） |
| diff 标签数 | **0**（见下方说明） | **1**（`1 difference`，15 处改动合并） |
| IDE UI 提示 | **"GoLand is not responding"** + `Dump threads` | 无 |
| GoLand CPU | **811.8%** | 峰值 30% |
| `Dispatchers.Default` 线程 | 4 个 worker 长期 RUNNABLE，累计 CPU **62s / 57s / 57s / 43s** | 正常 |
| 日志 | 大量 `TimeoutCancellationException: Timed out waiting for 30000 ms` + `PluginException: Cannot check provider ...` | 无 |
| IDE freeze 报告 | **生成** `threadDumps-freeze-20261007-020537-GO-262.10968.67/` | 无新增 |
| 冻结点 | `NestedLocksThreadingSupport$ComputationState.acquireWriteIntentPermit` (31700ms) | 无 |

## 关键证据

### 1. IDE 自动生成的 freeze 报告（0.1.4）

`~/Library/Logs/JetBrains/GoLand2026.2/threadDumps-freeze-20261007-020537-GO-262.10968.67/report.txt`

```
java.awt.EventDispatchThread.run 31700ms
 ...
  com.intellij.ide.IdeEventQueue.dispatchByCustomDispatchers 31700ms
   com.intellij.openapi.application.WriteIntentReadAction.computeThrowable 31700ms
    com.intellij.platform.locking.impl.NestedLocksThreadingSupport.runWriteIntentReadAction 31700ms
     ...ComputationState.acquireWriteIntentPermit 31700ms        ← 冻结点
      com.intellij.openapi.application.impl.ApplicationImpl.lambda$postInit$2 31700ms
       com.intellij.openapi.progress.util.SuvorovProgress.dispatchEventsUntilComputationCompletes
        com.intellij.openapi.progress.util.SuvorovProgress.showNiceOverlay   ← "not responding" 弹窗
```

与 JetBrains YouTrack **IJPL-252277**（Deadlock: read/write lock cycle）描述的机制一致。

### 2. 手动线程 dump（0.1.4）

`docs/repro/threaddump-0.1.4-excerpt.txt`

- `AWT-EventQueue-0`：`BLOCKED (on object monitor)`，卡在 `MTLRenderQueue$QueueFlusher.flushNow` ← `SuvorovProgress.showNiceOverlay` ← `ApplicationImpl.lambda$postInit$2`
- `DefaultDispatcher-worker-26`：
  ```
  ProgressIndicatorUtils.runActionAndCancelBeforeWrite      ← 有写操作在排队
    └─ CancellableReadActionKt.cancellableReadActionInternal
  ```
- `DefaultDispatcher-worker-10`：反复构造 `ReadAction$CannotReadException`（读操作取消风暴）

### 3. 根因

0.1.4 的 `PiDiffWatcher` 在 **VFS 变更后台线程**里直接调用文档与编辑器 API：

```kotlin
// 0.1.4
override fun after(events: List<VFileEvent>) {
    for (event in events) {
        if (event is VFileContentChangeEvent) handleFileChange(event.file)
    }
}
private fun handleFileChange(file: VirtualFile) {
    if (snapshots.containsKey(file.path)) showDiff(file.path)   // ← 后台线程
}
fun showDiff(filePath: String) {
    val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return
    ...
    DiffManager.getInstance().showDiff(project, request)        // ← 后台线程操作编辑器
}
```

叠加两个放大因素：

1. **没有防抖**：pi/工具连续写盘，每次写入都触发一次
2. **`snapshots` 从不更新**：同一文件第二次变更时快照仍是初值，永远判定"有变化"

结果：后台线程密集触碰 `getDocument()` / `showDiff()`，与 EDT 的写意图锁形成 IJPL-252277 那个读写锁循环 → 线程池耗干 → UI 冻结。

### 4. 修复（0.1.5）

- 新增 `PiChangeDebouncer`：合并写入突发，600ms 防抖
- 新增 `PiVfsUtils`：过滤 excluded / VCS ignored / binary 文件
- 所有编辑器操作统一派发到 **EDT**（`PiVfsUtils.runOnEdt`）
- `PiDiffWatcher` 每个文件**最多弹一次** diff（`shownFiles`）
- 索引期间（`DumbService.isDumb`）不触碰编辑器模型

## 附注：0.1.4 为什么"0 个 diff 标签"

0.1.4 在这些写入过程中**一个 diff 都没能真正展示出来**：它在后台线程调用编辑器 API，
要么被线程约束挡下、要么与重载中的 document 竞争，始终没走到渲染；
而每次尝试都参与了读写锁竞争，这才是把 IDE 拖死的原因。
0.1.5 因为改到 EDT + 防抖，反而**能正常、稳定地展示 1 个 diff**。

即：**修复不只是"少弹几个 diff"，而是"少了一条不断冲击读写锁的后台路径"。**

## 结语：最终改为完全静默（0.1.5 最终形态）

后续实测发现「每个文件 1 个 diff」在真实场景下仍然不可用：

- 一次 session 改 100 个文件，若这些文件都被 `@` 引用过，就会弹 100 个标签
- 自动弹窗会抢焦点，打断正在阅读的代码
- 实测中同一批次还出现过「3 个文件只弹了 2 个」——源于 `PiChangeDebouncer`
  的一个竞态：`flush()` 把 pending 取走与清空之间没有原子性，
  `submit()` 落在这个窗口里会看到非空集合、跳过调度，随后被 `clear()` 抹掉

因此最终设计改为：

- **彻底删除 `PiDiffWatcher`，插件不再自动打开任何 diff**
- 删除「Pi modified N files」完成通知
- `Auto-open files` 保留但默认关闭（开启时也只开普通编辑器标签，且带防抖/过滤/上限/EDT）
- `Send to Pi` 只负责插入 `@file#Lx-y` 引用
- 想看改动：自己 `git diff`

同时修掉两处正确性问题：

1. `PiChangeDebouncer` 用 `AtomicReference.getAndSet` 原子取走 pending，
   `submit()` 先加入再判断是否调度 → 消除丢事件
2. flush 回调可返回「未能处理的路径」，debouncer 用更长延迟重新入队 →
   索引期间不再永久丢弃
