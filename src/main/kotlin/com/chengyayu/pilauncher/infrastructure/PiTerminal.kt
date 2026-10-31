package com.chengyayu.pilauncher.infrastructure

/**
 * One terminal on screen, as far as a Pi Session cares about it.
 *
 * It is what a [PiSessionHost] hands back when it opens a Host, and it outlives nothing:
 * when the Host is closed the terminal is gone, while the session it showed may not be
 * (`docs/adr/0006`).
 */
interface PiTerminal {

    /**
     * Type [text] into the terminal, optionally submitting it.
     *
     * [onDelivered] is called once the text has reached the pty - or once every
     * attempt to get it there has failed, so a caller queueing behind this send
     * can never wait forever. It is the only ordering signal available: the
     * widget reports no readiness, and a tab that was just opened has no pty for
     * a while.
     */
    fun send(text: String, submit: Boolean, onDelivered: () -> Unit)

    /**
     * Whether a foreground command is running in this terminal.
     *
     * `null` means the engine could not tell; callers must then keep their
     * previous state instead of guessing an exit.
     */
    fun isForegroundCommandRunning(): Boolean?

    /**
     * Called with the absolute paths of the files the user drops onto the
     * terminal's surface.
     *
     * Without this, the platform's own drop handling decides what a drop means and
     * a drop that misses the terminal view opens a new tab in the file's directory
     * (`docs/adr/0008`). A Pi tab hands the paths to its session instead, which
     * inserts them as prompt references.
     */
    fun onFilesDropped(listener: (List<String>) -> Unit)
}

/**
 * Where Pi Sessions are shown: exactly one Host per running session.
 *
 * A Host is a place on screen - a Terminal tool window tab today, an editor tab next
 * (`docs/adr/0006`) - and nothing more. It knows nothing about identities, statuses or
 * parked sessions, so a second Host is a new implementation of this interface and not
 * a change to the registry.
 *
 * Two things here are about Hosts rather than sessions, and the difference matters:
 * a Host can outlive its run (the tab stays open after Pi exits) and a session can
 * outlive its Host (it gets parked and reopened later).
 */
interface PiSessionHost {

    /** Show a terminal rooted at [workingDirectory] called [name], and focus it. */
    fun open(workingDirectory: String, name: String): PiTerminal

    /** Bring the Host called [name] to the front, if it still exists. */
    fun focus(name: String)

    /**
     * The names of every Host currently on screen, including Hosts whose Pi has
     * already exited.
     *
     * Duplicate names would make focusing - and therefore Send to Pi - ambiguous, so
     * new sessions are named around this set rather than around the running ones.
     */
    fun shownNames(): Set<String>

    /** Called with the name whenever the user selects a Host. */
    fun onTabSelected(listener: (name: String) -> Unit)

    /**
     * Called with the old and the new name whenever the user renames a Host.
     *
     * The Host's name is the user's label for a Pi Session and outranks whatever the
     * plugin called it at creation, so renames have to reach the registry.
     */
    fun onTabRenamed(listener: (oldName: String, newName: String) -> Unit)

    /** Called with the name whenever a Host is closed. */
    fun onTabClosed(listener: (name: String) -> Unit)
}
