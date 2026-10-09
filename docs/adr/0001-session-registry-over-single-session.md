# Session registry with an Active Session pointer, not a single session

The plugin originally tracked exactly one Pi Session per project (one `PiSession`, one
`created` terminal field, one `TAB_NAME`). Users want several Pi processes running side
by side in the Terminal tool window, so the core abstraction changed to a **Session
Registry** (all running sessions, keyed by tab name) plus an **Active Session** pointer
(the most recently focused or created running session) that Send to Pi and focus
operations target.

## Considered options

- **Keep a single session** — rejected: cannot express the requested multi-tab usage.
- **Explicit targeting UI for every Send** — rejected as the default: a picker on each
  send is friction; focus-follows matches what the user is looking at.

## Consequences

- Every Pi Session gets its own exit polling; on exit it is removed from the registry,
  the Active Session rolls over to the most recent remaining session, and its Terminal
  Tab is left in place for the user to inspect.
- Tab names ("Pi 1", "Pi 2", …) are UI labels allocated as the smallest free number,
  not identities; the registry key is the tab name.
- All sessions share the global settings; the command line is a snapshot taken when a
  session starts. Per-session overrides are a future enhancement.
- The status topic publishes the running-session count instead of a single status.
