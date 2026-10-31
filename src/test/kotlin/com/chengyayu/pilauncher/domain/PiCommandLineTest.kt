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

    @Test
    fun `loads the status extension when there is one`() {
        val command = PiCommandLine.render(PiLaunchOptions(), "/tmp/pi-launcher/status.ts")

        assertEquals("pi --extension \"/tmp/pi-launcher/status.ts\" --no-themes", command)
    }

    /** A temporary directory can contain spaces on any platform, in any shell. */
    @Test
    fun `quotes the extension path`() {
        val command = PiCommandLine.render(PiLaunchOptions(), "/tmp/pi launcher/status.ts")

        assertTrue(command.contains("--extension \"/tmp/pi launcher/status.ts\""), command)
    }

    @Test
    fun `omits the extension flag when Pi cannot host one`() {
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions(), null))
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions(), "  "))
    }

    @Test
    fun `chooses the identity and the name Pi shows`() {
        val command = PiCommandLine.render(
            PiLaunchOptions(),
            sessionId = "pilaunch-0a1b2c3d",
            name = "Pi 1"
        )

        assertEquals("pi --session-id pilaunch-0a1b2c3d --name \"Pi 1\" --no-themes", command)
    }

    @Test
    fun `omits the identity when this Pi cannot be given one`() {
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions(), sessionId = null))
    }

    /** The name is a tab label and the command is typed into a shell. */
    @Test
    fun `strips shell-active characters from the name`() {
        val command = PiCommandLine.render(PiLaunchOptions(), name = "a\"b`c\$d;e|f")

        assertEquals("pi --name \"abcdef\" --no-themes", command)
    }

    @Test
    fun `omits a name that is nothing but unsafe characters`() {
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions(), name = "\"`\$"))
        assertEquals("pi --no-themes", PiCommandLine.render(PiLaunchOptions(), name = "   "))
    }

    @Test
    fun `resumes on the stored conversation without choosing a new identity`() {
        val command = PiCommandLine.resume(
            PiLaunchOptions(),
            sessionId = "pilaunch-0a1b2c3d",
            extensionPath = "/tmp/pi-launcher/pi 1/status.ts"
        )

        assertEquals(
            "pi --extension \"/tmp/pi-launcher/pi 1/status.ts\" --session pilaunch-0a1b2c3d --no-themes",
            command
        )
        assertFalse(command.contains("--session-id"), "Pi rejects --session-id together with --session")
    }

    @Test
    fun `keeps the model, the thinking level and the extra arguments when resuming`() {
        val command = PiCommandLine.resume(
            PiLaunchOptions(model = "p/m", thinkingLevel = "high", extraArgs = "--verbose"),
            "pilaunch-0a1b2c3d"
        )

        assertEquals("pi --session pilaunch-0a1b2c3d --no-themes --verbose", command)
    }
}
