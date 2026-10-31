# A Pi Session opens in an editor tab, eventually

Status: proposed. The editor-tab Session Host exists and is reachable only through a
development switch; the Terminal tool window Host still ships and is deleted only after
the editor tab has been used on a real IDE.

Air in IDEs shows a CLI agent's terminal in a **full-screen editor tab**, not in the IDE's
Terminal tool window, and keeps the session itself in a session list. The lesson is not
"agents need chat", it is that the session and the surface are separable
(`docs/adr/0006`). Pi Launcher's terminal has been pinned to a narrow tool-window tab
since the first release, which fixes where a session can live, how wide it can be, and -
before identities existed - how long it could last.

So a second Host renders the same `pi` in an editor tab: the widget is created with no
tool-window `Content` at all (`AbstractTerminalRunner.startShellTerminalWidget`), the
editor shows `widget.getComponent()`, and the widget's lifetime is the editor's
disposable. That is the platform's own arrangement for a terminal restored into an
editor (`org.jetbrains.plugins.terminal.vfs.TerminalSessionEditor`), not an invention.

## Considered options

- **Offer the user a choice of Host** — rejected: two surfaces that have not been shown
  to be equally good is a promise, not a setting. The switch below is for dogfooding and
  disappears with the host that loses.
- **Delete the tool-window Host in the same change** — rejected: five interactions can
  only be verified by a person on a real IDE (focus, paste, the ordering of Send to Pi
  against the start command, closing the editor parking the session, editor splitting
  with the terminal inside one pane). Removing the only verified Host before that would
  leave nothing to compare against.

## Consequences

- `PiHostChoice` is a development switch (`-Dpilauncher.session.host=editor`, or the
  `pilauncher.session.host.editor` registry key), not a setting, and not documented as a
  feature.
- The editor Host reports no rename: an editor tab is named by its file and the platform
  offers no inline rename for one. Renaming a *parked* session is unaffected, because
  that name is the plugin's own.
- `plugin.xml` keeps `<depends>org.jetbrains.plugins.terminal</depends>`: creating the
  widget still needs the terminal plugin, only the tool-window Content goes away.
- The delete list, once the editor tab has held up: `ToolWindowSessionHost`, the
  `Content`-based event plumbing, and the reflective `createShellWidget` path. ADR 0002
  narrows to `ReflectiveTerminal` only and must be marked superseded by this ADR.
- `shownNames()` changes meaning with the Host: today it is every tab in the Terminal
  tool window, so a manually opened `Local` shell can push a Pi session to the next
  number. In the editor it is only the sessions this plugin opened.
