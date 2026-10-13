# Report Session Status from an extension loaded into Pi

Status: accepted.

Pi cannot be observed from outside: its own status protocol is an OSC 7501 escape
sequence that the IDE terminal drops (ADR 0004), it offers no attach, and it writes no
status file. So instead of watching Pi, the plugin puts a reporter **inside** it.

The plugin ships a small status extension (`src/main/resources/pi/pi-launcher-status.ts`),
writes a copy of it into a private directory per session, and starts Pi with
`--extension <that copy>`. The copy reports the states Pi announces - `agent_start`,
`ui_prompt_start`/`ui_prompt_end`, `message_end` errors and `agent_settled` - into a
`status` file beside itself, which the session polls.

Two properties make this work:

- One copy per session means the extension can locate its own report through
  `__dirname`. No environment variable has to survive the user's shell, and the plugin
  never touches terminal internals, so both terminal engines and IDE versions behave
  the same. (Verified: `import.meta.url` is a `data:` URL under Pi's jiti loader, so
  `__dirname` is the only reliable anchor.)
- The flag is only passed after Pi itself confirmed it accepts `--extension`
  (`pi --help`, probed in the background). A Pi without it is never handed a flag it
  would reject: no status is better than a session that will not start.

## Consequences

- The status now covers `working`, `blocked`, `done`, `error` and `idle`. A report also
  carries Pi's own words when it has any (`blocked` gives the dialog title, `error` the
  first line) and what it is doing right now (`tool_execution_start` gives the running
  tool and its subject), which is what the dashboard's Detail column shows.
- The plugin depends on Pi's extension API (`--extension`, `pi.on`), so Pi has a floor
  version. An older Pi, a failed probe, or an unwritable directory all degrade to
  Inferred Status.
- ADR 0002 stands unchanged: tabs are still created the old way, and the OSC listener,
  the terminal probe and the shell-specific `PI_PROGRAM_STATUS` injection were deleted
  rather than kept around.
- `ProgramStatusOscDispatchTest` stays as the evidence for why the terminal is not a
  channel, so the next attempt does not start by re-reading the same bytecode.
