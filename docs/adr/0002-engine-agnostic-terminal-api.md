# Create Pi tabs through the engine-agnostic terminal API

Pi tabs are opened with `TerminalToolWindowManager.createShellWidget(dir, tabName,
requestFocus, deferSessionStart)`, which returns a `TerminalWidget`, and every
member is resolved reflectively with `setAccessible(true)`. Text is sent through
`sendCommandToExecute` when submitting and through the tty connector when typing,
with a bounded retry while the terminal is still connecting.

This is deliberate, and each part was forced by a bug found in the sandbox IDE:

- `createLocalShellWidget` (the API the plugin used first) delegates to
  `createShellWidget` and then casts the result to a JediTerm `ShellTerminalWidget`.
  On the block terminal engine - the default in 2024.3+ - that cast fails, so the
  second Pi tab could never be created.
- The classic engine returns a **package-private** bridge class
  (`JBTerminalWidget$TerminalWidgetBridge`). Its methods are public but
  unreachable without `setAccessible`, which silently turned "start pi" into
  nothing: the tab appeared, no command was delivered.
- The 2024.3 block terminal wraps its connector in an RD proxy
  (`BackendTtyConnector`), so process liveness has to unwrap `getConnector()`
  before asking for the shell's process.
- A fresh tab has no pty for a moment, so a single send attempt can legitimately
  find no delivery path; retrying only that case avoids submitting a command
  twice.

## Consequences

- The plugin carries no compile-time dependency on terminal internals; a platform
  rename costs a log line instead of a crash, and both engines work.
- Liveness probing stays best-effort: a connector that hides the process yields
  "unknown", and callers keep the previous state rather than inventing an exit.
