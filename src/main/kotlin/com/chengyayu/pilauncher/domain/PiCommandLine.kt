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

    /** Chooses the identity of a new Pi Session (`docs/adr/0006`). */
    private const val SESSION_ID = "--session-id"

    /** Opens the conversation Pi already stores under that identity. */
    private const val SESSION = "--session"

    /** The name Pi's own session picker shows for this conversation. */
    private const val NAME = "--name"

    /**
     * Starts a new Pi Session, optionally with an identity of the plugin's choosing
     * and a name Pi can show.
     */
    fun render(
        options: PiLaunchOptions,
        extensionPath: String? = null,
        sessionId: String? = null,
        name: String? = null
    ): String {
        val parts = mutableListOf(command(options))

        extensionPath?.takeIf { it.isNotBlank() }?.let {
            parts += EXTENSION
            parts += quote(it)
        }

        sessionId?.takeIf { it.isNotBlank() }?.let {
            parts += SESSION_ID
            parts += it
        }

        name?.let(::shellSafe)?.takeIf { it.isNotEmpty() }?.let {
            parts += NAME
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
     * Reopens the conversation Pi stores under [sessionId].
     *
     * `--session-id` is deliberately absent: Pi rejects it together with `--session`,
     * and the identity already exists (`docs/adr/0006`).
     */
    fun resume(options: PiLaunchOptions, sessionId: String, extensionPath: String? = null): String {
        val parts = mutableListOf(command(options))

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

    private fun command(options: PiLaunchOptions): String =
        options.command.ifBlank { PiLaunchOptions.DEFAULT_COMMAND }

    /**
     * Always quoted: the path lives under a temporary directory, which can contain
     * spaces on any platform, and double quotes are accepted by POSIX shells,
     * PowerShell and cmd alike.
     */
    private fun quote(path: String): String = "\"$path\""

    /**
     * The session name is the user's tab label, and it ends up inside a command typed
     * into a shell. Shell-active characters are removed rather than escaped: escaping
     * would have to be right for POSIX shells, PowerShell and cmd at once, and a
     * dropped quote is invisible where an unescaped one would run something.
     */
    private fun shellSafe(name: String): String = name.filterNot { it in UNSAFE_NAME_CHARACTERS }.trim()

    private val UNSAFE_NAME_CHARACTERS = setOf(
        '"', '\'', '`', '$', '\\', ';', '|', '&', '<', '>', '\n', '\r', '\t'
    )
}
