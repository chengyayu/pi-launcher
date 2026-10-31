# A Pi Session owns its identity; a terminal tab is only one way to show one

Status: accepted for the identity split. The editor-tab Session Host is a separate,
still-open step; a static audit of IC-243.21565.193 found the platform doing exactly
this itself (`plugins/terminal/…/vfs/TerminalSessionEditor` returns
`widget.getComponent()` from a `FileEditor`, and `AbstractTerminalRunner.startShellTerminalWidget`
creates a widget without a tool-window `Content`), so the Host half is a matter of
implementation, not of feasibility.

A Pi Session used to *be* a Terminal tool window tab: the tab decided where it lived,
what it was called and how long it lasted (ADR 0001 keys the registry by tab name, and
the glossary said closing a tab ends the session). That made the conversation
inseparable from one narrow strip of UI — a Pi Session could not use the editor area,
could not survive its UI, and could not be reopened.

So the two are split. **A Pi Session owns its identity** — pi's own session file plus
the process currently driving it — and **a Session Host is only the surface that shows
it**. There is one Host per Session and the Host may change; the user is never asked
which Host they want, and "surface" is not a setting. The only Host is the Terminal
tool window tab; an editor tab was tried and lost (`docs/adr/0007`).

## Considered options

- **Keep session == tab** — rejected: it is the constraint being lifted. It also makes
  "close the panel, come back later" impossible to express without pretending pi lost
  the conversation.
- **Expose Terminal/Chat as a user-chosen mode, as Air in IDEs does** — rejected for
  now: Air has two real surfaces to choose between; with one surface, a mode switch is
  a dimension the user pays for and gets nothing from. Revisit if a chat surface
  exists.

## Consequences

- Closing a Host ends a *run*, not the conversation. A session with no Host is still a
  Pi Session, and the dashboard is where it is visible and reopenable — which is what
  turns the dashboard from a read-only report into the one UI that does not depend on
  any Host.
- The registry can no longer be keyed by tab name (ADR 0001): a tab name is a Host's
  label, while identity has to survive the Host.
- **Identity is read from Pi, not chosen by the plugin.** The plugin first generated the
  id itself and passed `--session-id`, which works - and makes Pi print "No project
  session found with id …; creating a new session with that id" on every start, because
  a new id is by definition not on disk. The status extension now reports Pi's own
  `getSessionId()`, which the plugin adopts, and nothing is passed at launch. The cost is
  that identity depends on the same reporter as status: an older Pi that cannot host the
  extension has neither, where it used to have an identity and no status.
- ADR 0002 stays valid only for the Terminal-tool-window Host: the reflective
  `createShellWidget` + `Content` path does not exist for an editor tab. A second Host
  needs its own terminal plumbing, and the two coexist until one proves itself.
- Inferred Status (liveness probing of the pty) is a property of the Host, not of the
  session: a session without a Host has no Inferred Status at all.
