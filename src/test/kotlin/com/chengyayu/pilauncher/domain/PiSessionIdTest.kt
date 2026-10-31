package com.chengyayu.pilauncher.domain

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The identity handed to Pi. Pi accepts letters, numbers, `.`, `_` and `-`, and
 * requires an alphanumeric first and last character - an id it rejects would stop the
 * session from starting at all.
 */
class PiSessionIdTest {

    @Test
    fun `a new id carries the plugin prefix and a hex suffix`() {
        val id = PiSessionId.new()

        assertTrue(Regex("pilaunch-[0-9a-f]{8}").matches(id), id)
    }

    @Test
    fun `the suffix comes from the given source`() {
        val fixed = UUID.fromString("0a1b2c3d-0000-0000-0000-000000000000")

        assertEquals("pilaunch-0a1b2c3d", PiSessionId.new { fixed })
    }

    @Test
    fun `accepts the shapes Pi accepts`() {
        assertTrue(PiSessionId.isValid("pilaunch-0a1b2c3d"))
        assertTrue(PiSessionId.isValid("Pi.1_work-2"))
    }

    @Test
    fun `rejects the shapes Pi refuses`() {
        assertFalse(PiSessionId.isValid(""), "empty")
        assertFalse(PiSessionId.isValid("-abc"), "leading dash")
        assertFalse(PiSessionId.isValid("abc-"), "trailing dash")
        assertFalse(PiSessionId.isValid("a b"), "space")
        assertFalse(PiSessionId.isValid("a/b"), "slash")
        assertFalse(PiSessionId.isValid("会话"), "non-ASCII")
    }

    @Test
    fun `recognises only the plugin's own ids`() {
        assertTrue(PiSessionId.isOurs(PiSessionId.new()))
        assertFalse(PiSessionId.isOurs("6f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f"), "a plain uuid")
        assertFalse(PiSessionId.isOurs("pilaunch-"), "a prefix is not an id")
    }
}
