package com.chengyayu.pilauncher.domain

import java.util.UUID

/**
 * The identity of a Pi Session: the id Pi stores the conversation under
 * (`~/.pi/agent/sessions/--<path>--/<timestamp>_<id>.jsonl`).
 *
 * The plugin picks the id when a session starts instead of discovering it afterwards,
 * so a session whose Host is gone still has a name to come back to (`docs/adr/0006`),
 * and the prefix is what says which sessions this plugin created.
 *
 * Pi's own rules apply: letters, numbers, `.`, `_` and `-`, starting and ending with a
 * letter or a number.
 */
object PiSessionId {

    /** Marks the sessions this plugin created; also the dashboard's filter for reopening one. */
    const val PREFIX = "pilaunch-"

    private const val SUFFIX_LENGTH = 8

    private val VALID = Regex("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?")

    /** A fresh id: [PREFIX] plus eight hex characters. */
    fun new(random: () -> UUID = UUID::randomUUID): String =
        PREFIX + random().toString().replace("-", "").take(SUFFIX_LENGTH)

    /** Whether [id] may be handed to Pi at all. */
    fun isValid(id: String): Boolean = VALID.matches(id)

    /** Whether [id] names a session this plugin created. */
    fun isOurs(id: String): Boolean = id.startsWith(PREFIX) && isValid(id)
}
