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

    fun render(options: PiLaunchOptions, extensionPath: String? = null): String {
        val parts = mutableListOf(options.command.ifBlank { PiLaunchOptions.DEFAULT_COMMAND })

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

        parts += NO_THEMES

        options.extraArgs.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

        return parts.joinToString(" ")
    }

    /**
     * Always quoted: the path lives under a temporary directory, which can contain
     * spaces on any platform, and double quotes are accepted by POSIX shells,
     * PowerShell and cmd alike.
     */
    private fun quote(path: String): String = "\"$path\""
}
