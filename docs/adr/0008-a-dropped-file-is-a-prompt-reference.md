# A dropped file becomes a prompt reference, not a new tab

Status: accepted.

Dragging a screenshot onto a Pi tab's input area used to open a new `Local` terminal
tab rooted at the screenshot's temporary directory instead of putting the path into
Pi's prompt. Nothing in this plugin handled the drop: the IDE's terminal did, and
`TerminalDnDHandler` decides what a drop means from the drop point alone.

- Drop on the terminal view: the paths are inserted into the running session.
- Drop anywhere else in the tool window - the tab strip is the easy one to hit, and it
  sits directly above the input: **a new shell is started in the file's directory**
  (`LocalTerminalDirectRunner` was logged doing exactly that).

The second branch is reasonable for a shell tab and wrong for a Pi tab, where the
"terminal view" is Pi's own full-screen UI and the user's mental model is "put this
file into the prompt". It also cannot be avoided by aiming better: the platform
target is installed on the whole tool window, so any drop that lands on the tab strip
is a tab drop whatever the user meant.

So a Pi tab now installs its own `DnDSupport` target on its terminal component. A
component's target wins over the tool window's, which keeps the change local: only Pi
tabs behave this way, and every other tab keeps the platform's drop semantics.

The drop becomes prompt input, not terminal keystrokes: the paths are rendered as
`@path` (relative inside the project, absolute outside it - the same rendering as Send
to Pi) and handed to `PiSession.insertFiles`, so a drop that arrives before Pi is up
queues behind the start command like any other input (`docs/adr/0003`). A drop carrying
only text is inserted as text, which is what the platform did with it.

## Considered options

- **Document "drop on the view, not the tab strip"** - rejected: the view is Pi's whole
  screen, the strip is one row away, and the failure is silent apart from the stray tab.
- **Replace the tool window's drop handler** - rejected: it would change DnD for every
  terminal tab, including ordinary shells where opening a tab in the dropped directory
  is the desired behaviour, and it would tie the plugin to internals it does not own.
- **Disable drops on Pi tabs** - rejected: it removes a gesture instead of fixing what
  the gesture means.

## Consequences

- A file dropped anywhere on a Pi tab's own component is inserted as a reference and
  never starts a terminal. A drop on the tab strip itself is still the platform's and
  can still open a `Local` tab - the strip belongs to the tool window, not to the Pi
  tab, and the plugin only takes over surfaces it owns.
- Dropped paths are references, not attachments: Pi reads the file when it needs it,
  the same way it reads a path sent by Send to Pi.
- The plugin now uses `com.intellij.ide.dnd` on the terminal component. The APIs are
  public and stable (`DnDSupport`, `FileCopyPasteUtil`), unlike the reflective terminal
  calls in `docs/adr/0002`.
