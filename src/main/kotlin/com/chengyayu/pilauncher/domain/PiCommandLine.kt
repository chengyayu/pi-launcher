package com.chengyayu.pilauncher.domain

/**
 * Renders [PiLaunchOptions] into the shell command that starts Pi.
 *
 * Kept free of IDE types so the argument handling is unit testable.
 */
object PiCommandLine {

    /**
     * Pi renders its own footer and expects to own the screen, so the IDE
     * terminal themes are always disabled.
     */
    private const val NO_THEMES = "--no-themes"

    /**
     * How Pi is told to load the reporter that gives it a Session Status
     * (`docs/adr/0005`).
     */
    private const val EXTENSION = "--extension"

    /**
     * Tells Pi that this terminal cannot draw inline images.
     *
     * The IDE terminal implements neither the Kitty graphics protocol nor iTerm2's inline
     * images, and Pi's `auto` detection guesses wrong: it emits Kitty image data as an APC
     * sequence (`ESC _`), which the classic engine cannot parse - it logs a warning per
     * chunk and can swallow the keystrokes that follow. Nothing is lost by saying so: the
     * image was never going to appear (`docs/adr/0007`).
     */
    private const val NO_IMAGES = "PI_IMAGE_PROTOCOL=none"

    /** Chooses the identity of a new Pi Session (`docs/adr/0006`). */
    private const val SESSION_ID = "--session-id"

    /** Opens the conversation Pi already stores under that identity. */
    private const val SESSION = "--session"

    /**
     * Starts a new Pi Session, optionally with an identity of the plugin's choosing.
     *
     * No `--name`: Pi shows that name in its footer, so a session started from the IDE
     * would read `folder • Pi 1` where the same session started in a terminal reads just
     * `folder`. The label the user sees is the IDE's - the tab - so nothing is lost by
     * leaving Pi's own naming alone.
     */
    fun render(
        options: PiLaunchOptions,
        extensionPath: String? = null,
        sessionId: String? = null,
        windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    ): String {
        val parts = mutableListOf(command(options, windows))

        extensionPath?.takeIf { it.isNotBlank() }?.let {
            parts += EXTENSION
            parts += quote(it)
        }

        sessionId?.takeIf { it.isNotBlank() }?.let {
            parts += SESSION_ID
            parts += it
        }

        options.model?.takeIf { it.isNotBlank() }?.let {
            parts += "--model"
            parts += it
        }

        options.thinkingLevel?.takeIf { it.isNotBlank() }?.let {
            parts += "--thinking"
            parts += it
        }

        parts += NO_THEMES

        options.extraArgs.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

        return parts.joinToString(" ")
    }

    /**
     * Reopens the conversation Pi stores under [sessionId].
     *
     * `--session-id` is deliberately absent: Pi rejects it together with `--session`,
     * and the identity already exists (`docs/adr/0006`).
     */
    fun resume(
        options: PiLaunchOptions,
        sessionId: String,
        extensionPath: String? = null,
        windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    ): String {
        val parts = mutableListOf(command(options, windows))

        extensionPath?.takeIf { it.isNotBlank() }?.let {
            parts += EXTENSION
            parts += quote(it)
        }

        parts += SESSION
        parts += sessionId
        parts += NO_THEMES

        options.extraArgs.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

        return parts.joinToString(" ")
    }

    /**
     * The command with the image protocol turned off in front of it - as an environment
     * assignment for a POSIX shell, or a `$env:` assignment for the PowerShell the IDE
     * opens on Windows.
     */
    private fun command(options: PiLaunchOptions, windows: Boolean): String {
        val pi = options.command.ifBlank { PiLaunchOptions.DEFAULT_COMMAND }
        return if (windows) "\$env:PI_IMAGE_PROTOCOL='none'; $pi" else "$NO_IMAGES $pi"
    }

    /**
     * Always quoted: the path lives under a temporary directory, which can contain
     * spaces on any platform, and double quotes are accepted by POSIX shells,
     * PowerShell and cmd alike.
     */
    private fun quote(path: String): String = "\"$path\""
}
