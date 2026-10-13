# Queue prompt input behind the launch command

A Pi Session starts by sending `pi …` through the terminal. The tab has no pty for a
moment (see `docs/adr/0002`), so the command is sent after a settle delay and retried
while the pty is missing. Prompt input - the `@path/file.go#L10-25` reference from
`Send to Pi` - was sent to the terminal immediately, in parallel.

That races. Each send retries on its own timer, so the input could be typed *before*
the launch command was ever written. The shell then received both on one line:

```
> @backend/.../db.go#L39-43 pi --no-themes
zsh: no such file or directory: @backend/.../db.go#L39-43
```

Pi never started, and the reference was destroyed with the failed command. It only
showed up when the Terminal tool window had not been opened yet, because that is when
the pty takes long enough for the input's retry to fire first.

## Decision

`PiTerminal.send` reports when the text reached the pty, and `PiSession` holds prompt
input back until the start command has been reported that way. The terminal adapter
owns "did this text get delivered"; the session owns "nothing may be typed before Pi
was launched". Input arriving after launch goes straight through, and input gathered
while waiting is flushed in arrival order.

The delivery report is also sent when every retry was used up, so a terminal that never
becomes ready cannot strand a queued reference forever - it degrades to the old
behaviour instead of hanging.

## Considered options

- **Give Pi an `@file` argument instead of typing it.** Pi accepts `@files` on the
  command line, but that attaches the reference as context, while `Send to Pi` inserts
  it into the prompt for the user to keep writing around. Rejected as a UX change.
- **Delay the input by a fixed extra interval.** A longer version of the same race, and
  a second magic number next to the settle delay. Rejected.
- **Wait until the liveness probe sees a foreground command.** A real readiness signal,
  but it is polled every 2s and answers "unknown" on some engines, so the reference could
  be delayed by seconds or never flushed. Rejected; the write order is what the bug was
  about.

## Consequences

- `PiTerminal.send` takes a completion callback, so the adapter can no longer report
  delivery by returning.
- A queued reference appears when the pty accepts the launch command, which is still
  before Pi is ready to read it. Those bytes wait in the pty buffer as typeahead - the
  same place a user's fast typing ends up - rather than being typed into the shell.
