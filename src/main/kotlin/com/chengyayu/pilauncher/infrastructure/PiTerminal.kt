package com.chengyayu.pilauncher.infrastructure

/** One terminal tab, as far as a Pi Session cares about it. */
interface PiTerminal {

    /** Type [text] into the terminal, optionally submitting it. */
    fun send(text: String, submit: Boolean)

    /**
     * Whether a foreground command is running in this terminal.
     *
     * `null` means the engine could not tell; callers must then keep their
     * previous state instead of guessing an exit.
     */
    fun isForegroundCommandRunning(): Boolean?
}

/** Creates and navigates the Terminal tool window tabs that Pi Sessions run in. */
interface PiTerminalProvider {

    /** Open a tab named [tabName] rooted at [workingDirectory] and focus it. */
    fun createTerminalTab(workingDirectory: String, tabName: String): PiTerminal

    /** Bring the tab named [tabName] to the front, if it still exists. */
    fun focusTerminalTab(tabName: String)

    /** Display names of the tabs currently present in the Terminal window. */
    fun existingTabNames(): Set<String>

    /** Called with the tab name whenever the user selects a tab. */
    fun onTabSelected(listener: (String) -> Unit)

    /** Called with the tab name whenever a tab is closed. */
    fun onTabClosed(listener: (String) -> Unit)
}
