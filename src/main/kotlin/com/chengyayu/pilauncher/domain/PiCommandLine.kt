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

    fun render(options: PiLaunchOptions): String {
        val parts = mutableListOf(options.command.ifBlank { PiLaunchOptions.DEFAULT_COMMAND })

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
}
