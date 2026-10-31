# Pi Launcher

[中文文档](README_CN.md)

One-click [Pi coding agent](https://pi.dev) launcher for JetBrains IDEs — opens "Pi 1", "Pi 2", … tabs inside the Terminal tool window and starts `pi` automatically, several sessions at a time, each with the state Pi reports for itself.

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

| | |
|---|---|
| ![Pi running in a Terminal tab](screenshots/01-pi-terminal.png) | ![Send to Pi context menu](screenshots/02-send-to-pi.png) |
| ![Session Dashboard: every session, the state Pi reported, and the tool call it is running](screenshots/04-session-dashboard.png) | ![Pi Launcher settings](screenshots/03-settings.png) |

## Features

- **One-click launch** — Click the π button in the toolbar to start Pi
- **Terminal integration** — each Pi session runs as its own tab in the IDE's Terminal window (alongside Local), so several sessions can run side by side
- **Another session** — **Alt+click** the π button: plain click focuses the session you have
- **Send to Pi** — Select code → Right-click → "Send to Pi" inserts `@path/file.go#L10-25` into the active session's input; right-clicking a **file or folder** in the Project View sends its `@path` the same way
- **Drop a file on Pi** — Dropping a screenshot or any file on a Pi tab inserts `@path` into its prompt (the same reference, no submit); it never starts a new terminal tab
- **Live session status** — The status bar shows what Pi is doing (`working`, `blocked`, `done`, `error`, `idle`), not how many tabs happen to be open
- **Session Dashboard** — One editor tab listing every session with its state and Pi's own words: the dialog it is blocked on, an error's first line, the tool call it is running
- **Rename a tab, rename the session** — The dashboard and the tooltip use the tab's display name, so `Pi 1` renamed to `api work` is `api work` everywhere
- **Close the panel, keep the conversation** — the plugin reads back the identity Pi gives each session, so closing its tab ends the run, not the work: the dashboard lists it as `not running` and reopens it on Pi's own session file
- **Model configuration** — Pick a model from `~/.pi/agent/models.json` and a thinking level, or type any model id

All sessions share the same settings; the command line is a snapshot taken when a session starts.

## Session status

Pi reports its own execution state, and the plugin shows it rather than guessing:

| State | What Pi announced |
|-------|-------------------|
| `working` | an agent run or a compaction is in progress |
| `blocked` | a dialog is waiting for you — the tooltip and the dashboard name it |
| `done` | a run finished |
| `error` | a run ended with an error that Pi did not retry |
| `idle` | Pi started, or you cancelled the run |

A session Pi has not reported on reads **`no status from Pi`** — never `idle`. Not
every Pi build can be reported on, and a plugin that cannot see a state should say
so instead of inventing one; the status bar counts that session as `N unknown`
rather than folding it into a state. A session whose tab was closed reads **`not
running`**: that is none of Pi's states, because nothing is running and whatever it
last said belongs to the run that is over.

The status arrives through a small extension the plugin loads into Pi
(`--extension`, see `docs/adr/0005`), because Pi's own status protocol is an OSC
escape sequence that the IDE terminal discards (`docs/adr/0004`). The plugin asks Pi
whether it accepts the flag before using it, so an older Pi keeps working and simply
reports no status.

Click the status bar entry to open the **Session Dashboard**; **Alt+click** starts a
new session without leaving what is on screen. Closing a session's tab does not lose
the conversation: it keeps the identity Pi was started under, and the dashboard
reopens that session with `pi --session <id>`, continuing from the last thing Pi
persisted — not from the process that is gone.

The dashboard does that and nothing that could disturb a running session: show one in
the Terminal window, reopen a closed one, rename a closed one, or start another. The
status bar only counts what is running.

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
| `Ctrl+Shift+Backquote` | Focus the active Pi session, or launch one when none is running |
| `Ctrl+Shift+L` | Send selection to the active Pi session |
| `Alt` + click on π | Start an additional session instead of focusing the current one |

## Quick Start

1. Install the plugin
2. Ensure the `pi` CLI is installed and in your PATH:
   ```bash
   npm i -g @earendil-works/pi-coding-agent
   ```
3. Click the **π** button in the toolbar
4. A "Pi 1" tab opens in the Terminal window and `pi` starts automatically
5. For another session: **Alt+click** the π button (or use **New Pi Session** in the dashboard)

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

Five constraints drive the design:

1. **VFS change notifications arrive on a background thread.** Feeding them
   straight into editor APIs is what used to freeze GoLand: a burst of writes
   meant a burst of editor work, off the EDT, racing the platform's read/write
   lock. `PiVfsFilter` drops changes that are not Pi's, `PiChangeDebouncer`
   coalesces a burst off the EDT, and editor work happens only in the dispatch —
   deferred rather than dropped while indexing runs.

2. **Process liveness cannot be probed naively.** For pty4j local terminals,
   `TtyConnector.isConnected()` is delegated to a short-lived spawn helper rather
   than the interactive shell, so the engine's own answer is unusable. `pid()` is
   the real shell pid, so the shell's process tree is read through `ProcessHandle`
   instead — after unwrapping the RD connector proxy the block terminal installs
   first, and with `null` meaning "the engine could not tell" rather than "exited".

3. **The terminal API differs per engine.** Tabs are opened with the
   engine-agnostic `createShellWidget` (not `createLocalShellWidget`, which casts
   to a JediTerm widget and throws on the block engine), through reflection with
   `setAccessible`, because the classic engine returns a package-private bridge
   class. See `docs/adr/0002`.

4. **A fresh tab has no pty, and "the widget took the command" is not "the shell
   ran it".** The launch command waits out a settle delay and retries while the
   pty is missing, so a prompt reference sent in parallel could be typed first and
   run by the shell as part of the command line (`zsh: no such file or directory:
   @path…`). Input waits until the liveness probe sees Pi running, polled for at
   most 2 s, and is released anyway when the engine cannot tell. See `docs/adr/0003`.

5. **Pi cannot be observed from outside.** Its own status protocol is an OSC escape
   sequence the IDE terminal discards, it offers no attach, and it writes no status
   file. The plugin therefore loads a small extension into Pi with `--extension` and
   reads the file that extension writes (`docs/adr/0005`) — after asking Pi whether it
   accepts the flag, because a flag an older Pi rejects would stop the session from
   starting at all.

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
├── domain/                                # Pure logic, no IDE types, unit tested
│   ├── PiLaunchOptions.kt                 # Inputs that determine how Pi starts
│   ├── PiCommandLine.kt                   # Renders options into the CLI command
│   ├── PiFileReference.kt                 # @path#L10-25 references
│   ├── PiStatus.kt                        # Session Status: a state, a message, an activity
│   ├── PiStatusReport.kt                  # Decodes what the Pi-side extension writes
│   ├── PiSessionReport.kt                 # One read of that file: status and identity
│   ├── PiSessionId.kt                     # Whether an id Pi reported may reach a command line
│   ├── PiStatusReadout.kt                 # Wording shared by the status bar and dashboard
│   └── PiModel.kt                         # Model advertised by the Pi CLI
├── infrastructure/                        # Adapters to the IDE and the filesystem
│   ├── PiTerminal.kt                      # Terminal + Session Host interfaces (fakeable)
│   ├── ToolWindowSessionHost.kt           # Reflective Terminal tool window access
│   ├── ReflectiveTerminal.kt              # One terminal widget, reached by reflection
│   ├── Reflection.kt                      # Reflection helpers for that API
│   ├── PiStatusChannel.kt                 # Per-session status source (fakeable)
│   ├── PiStatusExtension.kt               # Hosts the Pi-side extension, reads its reports
│   ├── PiStatusProbeCache.kt              # "Can this Pi host the extension?", asked once
│   ├── PiModelRepository.kt               # Reads ~/.pi/agent/models.json
│   ├── PiProjectKey.kt                    # Per-project name for the temporary status files
│   ├── DroppedFiles.kt                    # Reads the paths a drop carries
│   ├── PiVfsFilter.kt                     # VFS change filtering
│   ├── PiEditorOpener.kt                  # Opens a file as an ordinary editor tab
│   └── PiNotifier.kt                      # Notification interface + IDE impl
├── util/                                  # Small helpers: coalescing bursts, scheduling work
│   ├── PiChangeDebouncer.kt               # Coalesces write bursts, IDE-free
│   └── PiScheduler.kt                     # Delayed work behind an interface (fakeable)
├── services/                              # Lifecycle: one session, all sessions, their parked state
│   ├── PiSessionService.kt                # Project-facing adapter
│   ├── PiSessionRegistry.kt               # Running and parked sessions, Active Session
│   ├── PiSession.kt                       # One session's lifecycle
│   ├── PiSessionStore.kt                  # Parked sessions, per project
│   ├── PersistentPiSessionStore.kt        # ...and its state file
│   ├── PiSessionListener.kt               # Observable running-session count (topic)
│   ├── PiSessionStatusListener.kt         # Observable session snapshot (topic)
│   ├── PiCommandDispatcher.kt             # Sends the start command
│   ├── PiFileWatcher.kt                   # Optional auto-open (off by default)
│   ├── PiStatusWidget.kt                  # Status bar entry
│   ├── PiStatusWidgetFactory.kt           # Registers that entry with the platform
│   └── ApplicationPiStatusProbeCache.kt   # The probe result this IDE session shares
├── ui/                                    # The Session Dashboard editor tab
│   ├── PiDashboardPanel.kt                # Table of sessions + safe actions
│   ├── PiDashboardTableModel.kt           # Readout -> row mapping
│   └── PiDashboardEditorProvider.kt       # One dashboard tab per project
├── actions/                               # Extract input, delegate to a service
│   ├── LaunchPiAction.kt                  # π button: focus, or start; Alt-click starts a second
│   ├── SendSelectionAction.kt             # Selection -> @path#L10-25
│   ├── SendFileToPiAction.kt              # File or folder -> @path
│   ├── PiPrompt.kt                        # Shared "insert this reference" path
│   └── EventFiles.kt                      # Resolves what an action was invoked on
└── settings/                              # Persisted state and settings UI
    ├── PiSettings.kt                      # The state the IDE persists
    ├── PiSettingsConfigurable.kt          # Settings → Tools → Pi Launcher
    └── PiSettingsForm.kt                  # The widgets behind it

src/main/resources/pi/
└── pi-launcher-status.ts                  # The reporter loaded into Pi with --extension
```

`domain` — and `PiChangeDebouncer` — carry no IntelliJ types, so argument handling,
reference formatting, status decoding and debouncing are covered by plain unit tests
(`./gradlew test`).

## License

MIT
