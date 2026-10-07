# JetBrains Marketplace 发布准备

## 插件信息

- **Name**: Pi Launcher
- **Tagline**: One-click Pi coding agent launcher for JetBrains IDEs
- **Category**: AI Assistant / Code Tools
- **Tags**: ai, coding-agent, terminal, pi, cli

## 需要准备的截图（3 张）

在 `./gradlew runIde` 沙箱 IDE 中操作并截图，建议 1280x800：

### 截图 1：Pi 终端
- 点击工具栏 π 按钮
- 展示 Terminal 窗口里的 `Pi` tab 正在运行 pi
- 状态栏显示 `π Running`

### 截图 2：Send to Pi
- 选中一段代码 → 右键 → **Send to Pi**
- 展示 Pi 输入框里插入了 `@lib/a.go#L10-25`

### 截图 3：设置面板
- **Settings → Tools → Pi Launcher**
- 展示 Model / Thinking level / Pi command / Auto-open files

```
screenshots/
├── 01-pi-terminal.png
├── 02-send-to-pi.png
└── 03-settings.png
```

## Marketplace 描述（用于填表）

Pi Launcher brings the Pi coding agent CLI into your JetBrains IDE.

Click the toolbar button and Pi opens as a tab in the Terminal tool window —
no separate window, no context switching. Select code, right-click "Send to Pi"
and a file reference (`@path/file.go#L10-25`) is inserted into Pi's prompt.

The plugin is intentionally quiet: it opens no diff tabs, shows no completion
balloons and reports no exit. A session can rewrite dozens of files, and
surfacing them as editor tabs is unusable — review the work with `git diff`.
The status bar shows whether Pi is running, and follows it when you quit.

No API keys needed — install the Pi CLI and you are ready.

## 发布 Checklist

- [ ] `./gradlew test` 通过
- [ ] `./gradlew runIde` 手工验证：启动 / 状态栏 / Send to Pi / 设置面板
- [ ] 截 3 张图
- [ ] `./gradlew buildPlugin` 打包
- [ ] `./gradlew verifyPlugin` 通过
- [ ] 登录 plugins.jetbrains.com 上传（需先确认 plugin id 未被占用）
- [ ] 填写描述、截图、分类
- [ ] 提交审核

## 注意

- plugin id 是 `com.chengyayu.pi-launcher`，发布后不可更改
- 排行榜/搜索里可能与其他 Pi 相关插件混淆，描述中已刻意说明差异
