# Changelog

## 0.1.0

First release (`com.chengyayu.pi-launcher`).

### Fixed

- **GoLand could freeze while Pi was working.** VFS change notifications arrive
  on a background thread, and the plugin fed them straight into editor APIs -
  opening an editor or a diff for every single write, including writes made by
  `go build`, `gofmt` or `git`. A burst of writes became a burst of off-EDT
  editor work racing the platform's read/write lock. All editor access now goes
  through a debouncer that coalesces a burst, filters irrelevant changes, skips
  indexing and dispatches to the EDT. Reproduced on 0.1.4 (IDE freeze report,
  811% CPU) and verified fixed; see `docs/repro/`.
- **Paths could be silently dropped.** The debouncer copied its pending set and
  cleared it outside an atomic swap, so a submit landing in that window skipped
  scheduling and was then wiped. Draining now uses an atomic swap.
- **Process liveness never worked.** The plugin called
  `ShellTerminalWidget.getTtyConnector()`, which does not exist on the class, and
  the exception was swallowed - so the feature silently never ran. It is now
  based on the shell's process tree, which is the only signal that works for
  pty4j local terminals.
- **A failed liveness probe was reported as an exit**, producing spurious "Pi
  process exited" notifications. Unknown now keeps the previous state.
- A Gradle wrapper jar that was gitignored, so a fresh clone could not build;
  a `setup.sh` that downloaded it from a 404; and a missing `LICENSE` file that
  the README badge already pointed at.

### Changed

- **The plugin is silent by design.** It opens no diff editor, shows no
  completion balloon and reports no exit. Pi sessions routinely rewrite dozens
  of files, and surfacing 100 of them as 100 editor tabs is unusable - review the
  work with `git diff`. The status bar carries the state instead, and follows Pi
  when it stops.
- **`Auto-open files` defaults to off.** When enabled it opens plain editor tabs
  (never diffs), debounced, filtered, capped at 10 per burst and skipped while
  the IDE is indexing.
- Renamed to **Pi Launcher** with a new plugin id, vendor and Kotlin package.
- Internals split into `domain` (pure logic), `infrastructure` (adapters),
  `services`, `util` and `settings`, with 37 unit tests covering the parts that
  used to be untestable.

### Removed

- Automatic "Before Pi / After Pi" diff preview.
- "Pi modified N files" and "Pi process exited" notifications.
- The unused `showNotifications` setting.
