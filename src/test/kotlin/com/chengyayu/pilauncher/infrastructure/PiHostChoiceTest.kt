package com.chengyayu.pilauncher.infrastructure

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which Host a project uses while the editor tab is unproven.
 *
 * The interesting cases are all the ways this must *not* switch: a typo, a stale value,
 * or a registry that is not available on the platform at hand.
 */
class PiHostChoiceTest {

    @Test
    fun `defaults to the Terminal tool window`() {
        assertFalse(PiHostChoice.editorRequested(property = null, registry = { false }))
    }

    @Test
    fun `a VM option asks for the editor tab`() {
        assertTrue(PiHostChoice.editorRequested(property = "editor", registry = { false }))
    }

    @Test
    fun `the registry key asks for the same thing`() {
        assertTrue(PiHostChoice.editorRequested(property = null, registry = { true }))
    }

    @Test
    fun `the environment variable is the switch that runIde can pass through`() {
        assertTrue(PiHostChoice.editorRequested(property = "EDITOR", registry = { false }))
    }

    @Test
    fun `a value that only looks like the editor tab changes nothing`() {
        assertFalse(PiHostChoice.editorRequested(property = "editorial", registry = { false }))
        assertFalse(PiHostChoice.editorRequested(property = "", registry = { false }))
        assertFalse(PiHostChoice.editorRequested(property = null, registry = { false }))
    }

    @Test
    fun `a registry that refuses to answer leaves the Terminal tool window`() {
        val broken = { throw IllegalStateException("no registry on this platform") }

        assertFalse(PiHostChoice.editorRequested(property = null, registry = broken))
    }
}
