package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.util.PiScheduler

/**
 * One running Pi: a terminal tab hosting a `pi` process, watched until it exits.
 *
 * Creation belongs to [PiSessionRegistry]; a session starts with its terminal
 * already attached. An exit is not reported to the user - `/exit` and a crash
 * cannot be told apart - but [onExited] tells the registry to stop counting it.
 */
internal class PiSession(
    val tabName: String,
    private val terminal: PiTerminal,
    private val startCommand: String,
    private val onExited: (PiSession) -> Unit,
    private val commandDispatcher: PiCommandDispatcher = PiCommandDispatcher(),
    private val scheduler: PiScheduler = PiScheduler.swing,
    private val clock: () -> Long = System::currentTimeMillis
) : Disposable {

    private val logger = Logger.getInstance(PiSession::class.java)

    @Volatile
    private var exited = false

    private var runningCheck: Disposable? = null

    val isRunning: Boolean get() = !exited

    fun start() {
        commandDispatcher.submit(terminal, startCommand)
        watchForExit()
    }

    /** Send [text] and submit it. */
    fun submitText(text: String) = terminal.send(text, submit = true)

    /** Type [text] into the prompt without submitting it. */
    fun insertText(text: String) = terminal.send(text, submit = false)

    /**
     * Polls for the exit. The startup grace absorbs the window before the shell
     * has a child, and a streak requirement absorbs transient gaps.
     */
    private fun watchForExit() {
        val startedAt = clock()
        var emptyObservations = 0

        runningCheck = scheduler.schedule(EXIT_POLL_INTERVAL_MILLIS, repeat = true) {
            if (exited || clock() - startedAt < STARTUP_GRACE_MILLIS) return@schedule

            // A null answer means the platform could not tell: keep the state.
            if (terminal.isForegroundCommandRunning() != false) {
                emptyObservations = 0
                return@schedule
            }

            if (++emptyObservations < EXIT_STREAK_THRESHOLD) return@schedule

            logger.info("Pi is no longer running in $tabName")
            exited = true
            runningCheck?.dispose()
            runningCheck = null
            onExited(this)
        }
    }

    override fun dispose() {
        runningCheck?.dispose()
        runningCheck = null
        exited = true
    }

    private companion object {
        const val EXIT_POLL_INTERVAL_MILLIS = 2_000
        const val STARTUP_GRACE_MILLIS = 6_000
        const val EXIT_STREAK_THRESHOLD = 2
    }
}
