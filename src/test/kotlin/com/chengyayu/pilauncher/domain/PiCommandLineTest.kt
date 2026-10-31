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
        extensionPath: String? = null
    ) = PiCommandLine.render(options, extensionPath, windows = false)

    @Test
    fun `renders the bare command`() {
        assertEquals("$noImages pi", render())
    }

    @Test
    fun `appends model and thinking level`() {
        assertEquals(
            "$noImages pi --model magpie/auto --thinking high",
            render(PiLaunchOptions(model = "magpie/auto", thinkingLevel = "high"))
        )
    }

    @Test
    fun `ignores blank model and thinking level`() {
        assertEquals("$noImages pi", render(PiLaunchOptions(model = "  ", thinkingLevel = "")))
    }

    @Test
    fun `falls back to the default command when blank`() {
        assertEquals("$noImages pi", render(PiLaunchOptions(command = " ")))
    }

    @Test
    fun `keeps extra arguments verbatim and last`() {
        assertEquals(
            "$noImages pi --model p/m --verbose --foo=bar",
            render(PiLaunchOptions(model = "p/m", extraArgs = "--verbose --foo=bar"))
        )
    }

    @Test
    fun `trims extra arguments`() {
        assertEquals("$noImages pi --x", render(PiLaunchOptions(extraArgs = "   --x   ")))
    }

    @Test
    fun `does not emit an empty extra argument segment`() {
        assertFalse(render(PiLaunchOptions(extraArgs = "   ")).endsWith(" "))
        assertFalse(render().contains("--no-themes"), "Pi loads its own themes")
    }

    @Test
    fun `loads the status extension when there is one`() {
        assertEquals(
            "$noImages pi --extension \"/tmp/pi-launcher/status.ts\"",
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
        assertEquals("$noImages pi", render(extensionPath = null))
        assertEquals("$noImages pi", render(extensionPath = "  "))
    }

    /**
     * No `--session-id`: Pi warns "No project session found with id …" for every id it has
     * not seen, which is every new session, and the warning lands in the terminal before
     * the interface paints. The identity is read back once Pi has started.
     */
    @Test
    fun `chooses no identity, so Pi starts without a warning`() {
        assertFalse(render().contains("--session-id"))
    }

    /** Pi shows its session name in the footer; a session started here must look the same. */
    @Test
    fun `names nothing, so the session looks like one started in a terminal`() {
        assertFalse(render().contains("--name"))
    }

    /**
     * The IDE terminal can draw neither Kitty graphics nor iTerm2 inline images. Pi's
     * `auto` detection gets that wrong and emits image data as a sequence the terminal
     * cannot parse, so the plugin says "none" instead of letting it guess.
     */
    @Test
    fun `tells Pi the terminal cannot show images`() {
        assertTrue(PiCommandLine.render(PiLaunchOptions(), windows = false).startsWith(noImages))
    }

    /** On Windows the IDE opens PowerShell, where an environment assignment looks different. */
    @Test
    fun `sets the image protocol the way PowerShell does`() {
        assertEquals(
            "\$env:PI_IMAGE_PROTOCOL='none'; pi",
            PiCommandLine.render(PiLaunchOptions(), windows = true)
        )
    }
}
