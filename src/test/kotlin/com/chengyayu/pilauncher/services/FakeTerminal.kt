package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.infrastructure.PiTerminal

/** One attempt to type into the pty, as a test terminal saw it. */
internal data class Sent(val text: String, val submitted: Boolean)

/**
 * A terminal that records what a session tried to send.
 *
 * Shared by the session and registry suites: both assert on the text and on whether
 * Enter followed it, and a second copy is a second place for that recording to drift.
 */
internal class FakeTerminal : PiTerminal {

    val sent = mutableListOf<Sent>()

    @Volatile
    var commandRunning: Boolean? = true

    private var dropListener: ((List<String>) -> Unit)? = null

    override fun isForegroundCommandRunning(): Boolean? = commandRunning

    override fun onFilesDropped(listener: (List<String>) -> Unit) {
        dropListener = listener
    }

    override fun send(text: String, submit: Boolean, onDelivered: () -> Unit) {
        sent += Sent(text, submit)
        onDelivered()
    }

    /** Simulates the user dropping files onto the terminal. */
    fun drop(paths: List<String>) {
        dropListener?.invoke(paths)
    }
}
