package com.chengyayu.pilauncher.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The temporary directory key that keeps one project's status channels away from
 * another's. It has to be safe as a file name and stable across restarts.
 */
class PiProjectKeyTest {

    @Test
    fun `is stable for the same project`() {
        assertEquals(projectKey("/work/api", "api"), projectKey("/work/api", "api"))
    }

    @Test
    fun `separates projects whose directories share a name`() {
        assertNotEquals(projectKey("/work/api", "api"), projectKey("/personal/api", "api"))
    }

    @Test
    fun `is usable as a directory name`() {
        val key = projectKey("/work/my project/with spaces and 'quotes'", "api")

        assertTrue(Regex("[A-Za-z0-9._-]+").matches(key), key)
    }

    @Test
    fun `falls back to the project name when there is no path`() {
        assertTrue(projectKey(null, "scratch").startsWith("scratch-"))
    }

    @Test
    fun `keeps the readable part short`() {
        val key = projectKey("/x/" + "a".repeat(200), "n")

        assertTrue(key.substringBeforeLast('-').length <= 24, key)
    }
}
