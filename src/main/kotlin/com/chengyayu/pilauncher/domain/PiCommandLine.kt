package com.chengyayu.pilauncher.domain

/** Renders [PiLaunchOptions] into the shell command that starts Pi. */
object PiCommandLine {

    /** Loads the status reporter into Pi (`docs/adr/0005`). */
    private const val EXTENSION = "--extension"

    /**
     * Pi's `auto` detection guesses this terminal can draw images and emits Kitty sequences
     * the classic engine cannot parse, swallowing keystrokes (`docs/adr/0007`).
     */
    private const val NO_IMAGES = "PI_IMAGE_PROTOCOL=none"

    /**
     * No `--session-id` (Pi warns about every id it has not seen) and no `--name` (Pi would
     * print the IDE label in its footer): the identity is read back from Pi (`docs/adr/0006`).
     */
    fun render(
        options: PiLaunchOptions,
        extensionPath: String? = null,
        windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    ): String {
        val parts = mutableListOf(command(options, windows))

        extensionPath?.takeIf { it.isNotBlank() }?.let {
            parts += EXTENSION
            parts += quote(it)
        }

        options.model?.takeIf { it.isNotBlank() }?.let {
            parts += "--model"
            parts += it
        }

        options.thinkingLevel?.takeIf { it.isNotBlank() }?.let {
            parts += "--thinking"
            parts += it
        }

        options.extraArgs.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

        return parts.joinToString(" ")
    }

    /** The image protocol is turned off as an env assignment for a POSIX shell or `$env:` for PowerShell. */
    private fun command(options: PiLaunchOptions, windows: Boolean): String {
        val pi = options.command.ifBlank { PiLaunchOptions.DEFAULT_COMMAND }
        return if (windows) "\$env:PI_IMAGE_PROTOCOL='none'; $pi" else "$NO_IMAGES $pi"
    }

    /** Always quoted: the path is a temp path, which can contain spaces on any platform. */
    private fun quote(path: String): String = "\"$path\""
}
