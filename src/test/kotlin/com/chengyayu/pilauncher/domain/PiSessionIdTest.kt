package com.chengyayu.pilauncher.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The identity comes from Pi (`docs/adr/0006`), so all the plugin does with it is decide
 * whether it may reach a command line: Pi accepts letters, numbers, `.`, `_` and `-`,
 * starting and ending with a letter or a number.
 */
class PiSessionIdTest {

    @Test
    fun `accepts the ids Pi produces`() {
        assertTrue(PiSessionId.isValid("6f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f"))
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
        assertFalse(PiSessionId.isValid("--help; rm -rf /"), "a command line")
    }
}
