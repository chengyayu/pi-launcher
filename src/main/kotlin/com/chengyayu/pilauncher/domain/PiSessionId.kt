package com.chengyayu.pilauncher.domain

/**
 * The identity of a Pi Session: the id Pi stores the conversation under
 * (`~/.pi/agent/sessions/--<path>--/<timestamp>_<id>.jsonl`).
 *
 * The id is Pi's own: it is read from the session once Pi has started it
 * (`docs/adr/0006`). Everything the plugin does with it - reopening a session, keeping it
 * in the parked list - first has to survive Pi's rules for it: letters, numbers, `.`, `_`
 * and `-`, starting and ending with a letter or a number. The parked list is a file on
 * disk, so a hand-edited id has to be rejected before it reaches a command line.
 */
object PiSessionId {

    private val VALID = Regex("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?")

    /** Whether [id] may be handed to Pi at all. */
    fun isValid(id: String): Boolean = VALID.matches(id)
}
