# A closed tab ends the session

Status: accepted. Supersedes the parked-session half of ADR 0006.

ADR 0006 split identity from the Host so a conversation could outlive the UI that
showed it: closing a tab ended the run, the session was parked with the id Pi had
reported, the dashboard listed it as `not running`, and `Resume` reopened it with
`pi --session <id>`.

That list turned out to be the wrong shape. A dashboard whose job is "what is Pi doing
right now" filled up with rows that say `not running` and nothing else: same words for
every parked session, an empty Detail column, no indication of when it was closed or
whether Pi still had the conversation. Worse, the entries never went away on their
own - the plugin kept a shortlist of 20, and the only way to drop one was to close
enough sessions to push it off the end.

So the session ends with its run. When the `pi` process exits or its Host closes, the
registry forgets the session: it leaves the dashboard, the status bar stops counting
it, and nothing offers to reopen it. Pi still stores the conversation; `pi --session
<id>` still works in a terminal. It is not an action this plugin takes.

## Considered options

- **Keep parking, label it better** (a `closed` state, a "closed 2h ago" detail, a
  group separator, a Forget action): rejected as a patch on the real problem - the
  dashboard would still mix two different questions, "what is running" and "what ran
  before", and the second one is not what it is for.
- **Park without listing**: the records would exist only to be reopened by a menu
  action nobody asked for, and the persisted state would grow with no UI to prune it.

## Consequences

- ADR 0006's identity half stands: Pi's id is still read and validated, and it is what
  a log line uses to say which conversation a tab was driving. What is gone is anything
  acting on it - `--session` is no longer rendered, and `PiCommandLine` has no `resume`.
- The per-project session store and its state file are gone. An existing
  `PiLauncherSessions.xml` in a project's `.idea` is left behind and ignored; it can be
  deleted.
- ADR 0001's registry is unchanged in shape, but it now holds exactly the running
  sessions, which is also what its `runningCount` always counted.
- The dashboard stops being "the one UI that does not depend on any Host" (ADR 0006).
  That job is now Pi's own session files and the `pi --session` command line.
