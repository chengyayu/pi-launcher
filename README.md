# Pi Launcher

[中文文档](README_CN.md)

One-click [Pi coding agent](https://pi.dev) launcher for JetBrains IDEs — opens a "Pi" tab inside the Terminal tool window and starts `pi` automatically.

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

## Features

- **One-click launch** — Click the π button in the toolbar to start Pi
- **Terminal integration** — Pi runs as a tab inside the IDE's Terminal window (alongside Local)
- **Send to Pi** — Select code → Right-click → "Send to Pi" inserts `@path/file.go#L10-25` into Pi's input
- **Status bar state** — Shows whether Pi is running, and follows it when you quit
- **Model configuration** — Pick model and thinking level from `~/.pi/agent/models.json`

## Silent by design

Pi sessions routinely rewrite dozens of files. The plugin deliberately opens **no**
diff editor, shows **no** per-file notification and reports **no** exit — surfacing
100 modified files as 100 editor tabs is unusable. Review the work with `git diff`.

The only notification it ever raises is an error when *you* click launch and the
terminal cannot be created.

If you do want modified files opened as ordinary editor tabs, opt in via
**Settings → Tools → Pi Launcher → Auto-open files** (off by default).

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+Backquote` | Launch or focus Pi |
| `Ctrl+Shift+L` | Send selection to Pi |

## Quick Start

1. Install the plugin
2. Ensure the `pi` CLI is installed and in your PATH:
   ```bash
   npm i -g @earendil-works/pi-coding-agent
   ```
3. Click the **π** button in the toolbar
4. A "Pi" tab opens in the Terminal window and `pi` starts automatically

## Configuration

**Settings → Tools → Pi Launcher**

- **Model** — Select from models defined in `~/.pi/agent/models.json`
- **Custom model id** — Override the dropdown with any model identifier
- **Thinking level** — Default / none / low / medium / high / max
- **Pi command** — Custom path to pi binary
- **Extra arguments** — Additional CLI flags
- **Auto-open files** — Off by default. When enabled, opens modified files as ordinary editor tabs (never diffs), debounced and capped.

## Supported IDEs

Works with all JetBrains IDEs: IntelliJ IDEA, GoLand, PyCharm, WebStorm, PhpStorm, CLion, Rider, RubyMine, and more.

## Why the code is shaped like this

Two constraints drive the design:

1. **VFS change notifications arrive on a background thread.** Feeding them
   straight into editor APIs is what used to freeze GoLand: a burst of writes
   meant a burst of editor work, off the EDT, racing the platform's read/write
   lock. All editor access now goes through `PiChangeDebouncer`, which coalesces
   a burst, filters irrelevant changes, skips indexing, and dispatches to the EDT.

2. **Process liveness cannot be probed naively.** For pty4j local terminals,
   `TtyConnector.isConnected()` is delegated to a short-lived spawn helper rather
   than the interactive shell, and `ShellTerminalWidget.hasRunningCommands()`
   returns early with `false` whenever that is false. `pid()` is the real child
   pid, so the shell's process tree is inspected instead.

## Development

```bash
# Build
./gradlew build

# Run unit tests
./gradlew test

# Run sandbox IDE for testing
./gradlew runIde

# Package
./gradlew buildPlugin
```

## Project Structure

```
src/main/kotlin/com/chengyayu/pilauncher/
├── domain/                        # Pure logic, no IDE types, unit tested
│   ├── PiLaunchOptions.kt         # Inputs that determine how Pi starts
│   ├── PiCommandLine.kt           # Renders options into the CLI command
│   ├── PiFileReference.kt         # @path#L10-25 references
│   └── PiModel.kt                 # Model advertised by the Pi CLI
├── infrastructure/                # Adapters to the IDE and the filesystem
│   ├── PiTerminal.kt              # Terminal interfaces (fakeable)
│   ├── IdeTerminalProvider.kt     # Reflective Terminal tool window access
│   ├── PiModelRepository.kt       # Reads ~/.pi/agent/models.json
│   └── PiNotifier.kt              # Notification interface + IDE impl
├── util/
│   └── PiChangeDebouncer.kt       # Coalesces write bursts, IDE-free
├── services/
│   ├── PiSessionService.kt        # Project-facing adapter
│   ├── PiSession.kt               # Session lifecycle
│   ├── PiSessionState.kt          # Observable status (topic)
│   ├── PiCommandDispatcher.kt     # Sends the start command
│   ├── PiFileWatcher.kt           # Optional auto-open (off by default)
│   ├── PiStatusWidget.kt          # Status bar entry
│   └── PiVfsUtils.kt              # VFS change filtering
├── actions/                       # Extract input, delegate to a service
└── settings/                      # Persisted state and settings UI
```

The `domain` and `util` packages have no IntelliJ dependencies, so argument
handling, reference formatting and debouncing are covered by plain unit tests
(`./gradlew test`).

## License

MIT
