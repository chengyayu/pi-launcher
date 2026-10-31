# Pi Sessions stay in the Terminal tool window

Status: accepted. An editor-tab Host was built, used, and removed.

Air in IDEs shows a CLI agent's terminal in a full-screen editor tab, so a Pi Session in
an editor tab was built to match: the widget created without any tool-window `Content`,
shown by a `FileEditor`, its lifetime owned by the editor. It worked - it was used on a
real IDE, and it fixed the narrow tool-window strip. Then it was removed, because the
surface was never the thing holding the plugin back, and moving it made every other
problem worse.

What the editor tab cost, in the order they were found:

- The shell session asked for a terminal size before its widget was on screen, and Pi
  laid itself out for the 80x24 fallback.
- The block terminal inside an editor is an editor inside an editor: scrolling was not
  smooth, and its input method mangled CJK. Switching to the classic engine fixed both
  and introduced a third problem - the classic engine cannot parse the Kitty graphics
  sequences Pi emits when it thinks the terminal supports images, so it logged a warning
  per chunk and could swallow the keystrokes that followed.
- Closing the tab did not reliably dispose the editor, so the Host had to end the run
  itself rather than trust `FileEditor.dispose()`.

None of that is about where a session is drawn. It is about what a terminal emulator
can do, and the emulator was the same one in both Hosts.

## Considered options

- **Keep the editor tab** - rejected: it buys width, and the tool window strip can be
  widened. Everything else it cost.
- **Replace the terminal with a chat UI over `pi --mode rpc`** - rejected *for now*:
  `rpc` does give structured messages, tool calls, usage and native images (verified:
  a base64 image in `prompt.images` reaches the model), but a chat UI means
  reimplementing Pi's own interface - transcript, `/tree`, `/resume`, permissions,
  compaction, queueing - and staying permanently one release behind it. The terminal
  carries **all** of Pi's abilities by construction, because it is Pi's own UI.
- **Embed another emulator (libghostty, JCEF + xterm.js)** - rejected: it would buy
  inline images and nothing else the plugin needs, at the price of owning a terminal.

## Consequences

- The plugin keeps doing what a terminal host cannot: launching, identity, parking and
  resuming a session, status, and handing it file references. That is the whole job.
- The ceiling is the IDE's terminal emulator: **no inline image previews**. A dropped
  image is referenced by path, which Pi reads correctly; showing it is the IDE's job, not
  the terminal's.
- Sessions are started with `PI_IMAGE_PROTOCOL=none`, because Pi's detection guesses that
  this terminal can draw images, and its attempts break input.
- Revisit the chat UI only if the requirement becomes one the terminal cannot express:
  clickable diffs, native permission dialogs, or images inside the transcript.
