package com.chengyayu.pilauncher.domain

/**
 * Pi's own id for a conversation, read back from the session (`docs/adr/0006`). Pi accepts
 * only letters, numbers, `.`, `_` and `-`; the parked list is a hand-editable file, so an id
 * is checked before it reaches a command line.
 */
object PiSessionId {

    private val VALID = Regex("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?")

    fun isValid(id: String): Boolean = VALID.matches(id)
}
