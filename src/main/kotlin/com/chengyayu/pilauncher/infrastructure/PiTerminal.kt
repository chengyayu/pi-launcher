package com.chengyayu.pilauncher.infrastructure

/** One terminal on screen, as far as a Pi Session cares about it. */
interface PiTerminal {

    /**
     * Types [text], submitting it if asked. [onDelivered] fires once the text reached the pty
     * or every attempt failed - the only ordering signal there is, because a fresh tab has no
     * pty for a while.
     */
    fun send(text: String, submit: Boolean, onDelivered: () -> Unit)

    /** `null` when the engine cannot tell; callers must not read that as "exited". */
    fun isForegroundCommandRunning(): Boolean?

    /** Without this the platform's own drop handling opens a `Local` tab (`docs/adr/0008`). */
    fun onFilesDropped(listener: (List<String>) -> Unit)
}

/**
 * Where Pi Sessions are shown: at most one Host per session, and the two live and end together.
 */
interface PiSessionHost {

    /** Opens a terminal rooted at [workingDirectory] named [name], and focuses it. */
    fun open(workingDirectory: String, name: String): PiTerminal

    fun focus(name: String)

    /** Every Host on screen, including ones whose Pi exited: names have to stay unique. */
    fun shownNames(): Set<String>

    fun onTabSelected(listener: (name: String) -> Unit)

    /** The user's label outranks the one the plugin chose at creation. */
    fun onTabRenamed(listener: (oldName: String, newName: String) -> Unit)

    fun onTabClosed(listener: (name: String) -> Unit)
}
