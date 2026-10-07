package com.chengyayu.pilauncher.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiCommandLineTest {

    @Test
    fun `renders the bare command with themes disabled`() {
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions()))
    }

    @Test
    fun `appends model and thinking level`() {
        val command = PiCommandLine.render(
            PiLaunchOptions(model = "magpie/auto", thinkingLevel = "high")
        )
        assertEquals("pi --model magpie/auto --thinking high --no-themes", command)
    }

    @Test
    fun `ignores blank model and thinking level`() {
        val command = PiCommandLine.render(
            PiLaunchOptions(model = "  ", thinkingLevel = "")
        )
        assertEquals("pi --no-themes", command)
    }

    @Test
    fun `falls back to the default command when blank`() {
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions(command = " ")))
    }

    @Test
    fun `keeps extra arguments verbatim and last`() {
        val command = PiCommandLine.render(
            PiLaunchOptions(model = "p/m", extraArgs = "--verbose --foo=bar")
        )
        assertEquals("pi --model p/m --no-themes --verbose --foo=bar", command)
    }

    @Test
    fun `trims extra arguments`() {
        val command = PiCommandLine.render(PiLaunchOptions(extraArgs = "   --x   "))
        assertEquals("pi --no-themes --x", command)
    }

    @Test
    fun `does not emit an empty extra argument segment`() {
        assertFalse(PiCommandLine.render(PiLaunchOptions(extraArgs = "   ")).endsWith(" "))
        assertTrue(PiCommandLine.render(PiLaunchOptions()).contains("--no-themes"))
    }
}
