package com.chengyayu.pilauncher.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiCommandLineTest {

    /** Every command says the terminal cannot show images; see `NO_IMAGES` in the renderer. */
    private val noImages = "PI_IMAGE_PROTOCOL=none"

    private fun render(
        options: PiLaunchOptions = PiLaunchOptions(),
        extensionPath: String? = null,
        sessionId: String? = null
    ) = PiCommandLine.render(options, extensionPath, sessionId, windows = false)

    private fun resume(
        options: PiLaunchOptions = PiLaunchOptions(),
        sessionId: String,
        extensionPath: String? = null
    ) = PiCommandLine.resume(options, sessionId, extensionPath, windows = false)

    @Test
    fun `renders the bare command with themes disabled`() {
        assertEquals("$noImages pi --no-themes", render())
    }

    @Test
    fun `appends model and thinking level`() {
        assertEquals(
            "$noImages pi --model magpie/auto --thinking high --no-themes",
            render(PiLaunchOptions(model = "magpie/auto", thinkingLevel = "high"))
        )
    }

    @Test
    fun `ignores blank model and thinking level`() {
        assertEquals("$noImages pi --no-themes", render(PiLaunchOptions(model = "  ", thinkingLevel = "")))
    }

    @Test
    fun `falls back to the default command when blank`() {
        assertEquals("$noImages pi --no-themes", render(PiLaunchOptions(command = " ")))
    }

    @Test
    fun `keeps extra arguments verbatim and last`() {
        assertEquals(
            "$noImages pi --model p/m --no-themes --verbose --foo=bar",
            render(PiLaunchOptions(model = "p/m", extraArgs = "--verbose --foo=bar"))
        )
    }

    @Test
    fun `trims extra arguments`() {
        assertEquals("$noImages pi --no-themes --x", render(PiLaunchOptions(extraArgs = "   --x   ")))
    }

    @Test
    fun `does not emit an empty extra argument segment`() {
        assertFalse(render(PiLaunchOptions(extraArgs = "   ")).endsWith(" "))
        assertTrue(render().contains("--no-themes"))
    }

    @Test
    fun `loads the status extension when there is one`() {
        assertEquals(
            "$noImages pi --extension \"/tmp/pi-launcher/status.ts\" --no-themes",
            render(extensionPath = "/tmp/pi-launcher/status.ts")
        )
    }

    /** A temporary directory can contain spaces on any platform, in any shell. */
    @Test
    fun `quotes the extension path`() {
        val command = render(extensionPath = "/tmp/pi launcher/status.ts")

        assertTrue(command.contains("--extension \"/tmp/pi launcher/status.ts\""), command)
    }

    @Test
    fun `omits the extension flag when Pi cannot host one`() {
        assertEquals("$noImages pi --no-themes", render(extensionPath = null))
        assertEquals("$noImages pi --no-themes", render(extensionPath = "  "))
    }

    @Test
    fun `chooses the identity Pi stores the conversation under`() {
        assertEquals(
            "$noImages pi --session-id pilaunch-0a1b2c3d --no-themes",
            render(sessionId = "pilaunch-0a1b2c3d")
        )
    }

    @Test
    fun `omits the identity when this Pi cannot be given one`() {
        assertEquals("$noImages pi --no-themes", render(sessionId = null))
    }

    /** Pi shows its session name in the footer; a session started here must look the same. */
    @Test
    fun `names nothing, so the session looks like one started in a terminal`() {
        assertFalse(render(sessionId = "pilaunch-0a1b2c3d").contains("--name"))
    }

    @Test
    fun `resumes on the stored conversation without choosing a new identity`() {
        val command = resume(
            sessionId = "pilaunch-0a1b2c3d",
            extensionPath = "/tmp/pi-launcher/pi 1/status.ts"
        )

        assertEquals(
            "$noImages pi --extension \"/tmp/pi-launcher/pi 1/status.ts\" --session pilaunch-0a1b2c3d --no-themes",
            command
        )
        assertFalse(command.contains("--session-id"), "Pi rejects --session-id together with --session")
    }

    @Test
    fun `keeps the extra arguments when resuming`() {
        assertEquals(
            "$noImages pi --session pilaunch-0a1b2c3d --no-themes --verbose",
            resume(PiLaunchOptions(extraArgs = "--verbose"), "pilaunch-0a1b2c3d")
        )
    }

    /**
     * The IDE terminal can draw neither Kitty graphics nor iTerm2 inline images. Pi's
     * `auto` detection gets that wrong and emits image data as a sequence the terminal
     * cannot parse, so the plugin says "none" instead of letting it guess.
     */
    @Test
    fun `tells Pi the terminal cannot show images`() {
        assertTrue(PiCommandLine.render(PiLaunchOptions(), windows = false).startsWith(noImages))
        assertTrue(PiCommandLine.resume(PiLaunchOptions(), "pilaunch-a", windows = false).startsWith(noImages))
    }

    /** On Windows the IDE opens PowerShell, where an environment assignment looks different. */
    @Test
    fun `sets the image protocol the way PowerShell does`() {
        assertEquals(
            "\$env:PI_IMAGE_PROTOCOL='none'; pi --no-themes",
            PiCommandLine.render(PiLaunchOptions(), windows = true)
        )
    }
}
