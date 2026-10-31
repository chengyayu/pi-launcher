package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.domain.PiFileReference
import com.chengyayu.pilauncher.domain.PiSessionId
import com.chengyayu.pilauncher.domain.PiStatus
import com.chengyayu.pilauncher.infrastructure.PiStatusSource
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.util.PiScheduler

/**
 * One running Pi: a Host showing a `pi` process, watched until it exits.
 *
 * An exit is not reported - `/exit` and a crash cannot be told apart - [onExited] only tells
 * the registry to stop counting the run. Input that arrives before the shell has started Pi is
 * held back, or the shell would run it as part of the command line (`docs/adr/0003`).
 */
internal class PiSession(
    /** The Host's display name; renaming the Host updates it. */
    @Volatile
    var tabName: String,
    /** Pi's id for this run's conversation, reported by the extension (`docs/adr/0006`). */
    id: String?,
    /** The status channel's directory name: unique per run. */
    val channelId: String,
    private val terminal: PiTerminal,
    private val startCommand: String,
    private val statusSource: PiStatusSource? = null,
    private val onExited: (PiSession) -> Unit,
    private val onStatusChanged: (PiSession) -> Unit = {},
    private val commandDispatcher: PiCommandDispatcher = PiCommandDispatcher(),
    private val scheduler: PiScheduler = PiScheduler.swing,
    private val clock: () -> Long = System::currentTimeMillis
) : Disposable {

    private val logger = Logger.getInstance(PiSession::class.java)

    @Volatile
    var id: String? = id
        private set

    @Volatile
    private var exited = false

    /**
     * Null when Pi has not reported yet or cannot report at all. A real answer, never `idle`.
     */
    @Volatile
    var status: PiStatus? = null
        private set

    private var runningCheck: Disposable? = null

    private var statusCheck: Disposable? = null

    /** Input that arrived before the shell started Pi, in arrival order; null once released. */
    private var queuedInput: MutableList<PromptInput>? = mutableListOf()

    val isRunning: Boolean get() = !exited

    fun start() {
        commandDispatcher.submit(terminal, startCommand, onDelivered = ::flushWhenRunning)
        watchForExit()
        watchStatus()
    }

    fun submitText(text: String) = send(text, submit = true)

    fun insertText(text: String) = send(text, submit = false)

    /** The same `@` rendering as Send to Pi, so a path outside the project stays absolute (`docs/adr/0008`). */
    fun insertFiles(basePath: String?, paths: List<String>) {
        if (paths.isEmpty()) return

        insertText(paths.joinToString(" ") { PiFileReference.ofFile(basePath, it).render() } + " ")
    }

    private fun send(text: String, submit: Boolean) = synchronized(this) {
        val queued = queuedInput
        if (queued != null) queued += PromptInput(text, submit)
        else terminal.send(text, submit) { }
    }

    /**
     * Releases queued input once the shell reports Pi running - `sendCommandToExecute` returns
     * before the shell has run the command, and a reference flushed in that gap lands in front
     * of the command line. `null` (engine cannot tell) and a timeout release it anyway
     * (`docs/adr/0003`).
     */
    private fun flushWhenRunning(attemptsLeft: Int = FLUSH_ATTEMPTS): Unit = synchronized(this) {
        val queued = queuedInput ?: return

        if (terminal.isForegroundCommandRunning() != false || attemptsLeft == 0) {
            queuedInput = null
            // Sent while holding the lock: input arriving now must not overtake it.
            queued.forEach { terminal.send(it.text, it.submit) { } }
            return
        }

        scheduler.schedule(FLUSH_POLL_MILLIS, repeat = false) { flushWhenRunning(attemptsLeft - 1) }
    }

    /** The startup grace absorbs the window before the shell has a child; the streak absorbs transient gaps. */
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

    /**
     * A poll rather than a filesystem watch: the reporter replaces its file atomically, so a
     * `stat` every few hundred milliseconds is cheaper than watching a file that keeps being
     * renamed out from under the service.
     */
    private fun watchStatus() {
        val source = statusSource ?: return

        statusCheck = scheduler.schedule(STATUS_POLL_INTERVAL_MILLIS, repeat = true) {
            if (exited) return@schedule

            val report = source.read() ?: return@schedule
            adoptIdentity(report.identity)

            if (report.status == status) return@schedule

            status = report.status
            onStatusChanged(this)
        }
    }

    /**
     * Only the first valid id Pi reports. Nothing here reopens it; the id says which conversation
     * a tab is driving when the log is read afterwards.
     */
    private fun adoptIdentity(reported: String?) {
        if (id != null || reported == null) return
        if (!PiSessionId.isValid(reported)) return

        id = reported
        logger.info("Pi session $reported is driving $tabName")
    }

    override fun dispose() {
        runningCheck?.dispose()
        runningCheck = null
        statusCheck?.dispose()
        statusCheck = null
        exited = true
    }

    private companion object {
        const val EXIT_POLL_INTERVAL_MILLIS = 2_000
        const val STATUS_POLL_INTERVAL_MILLIS = 400
        const val STARTUP_GRACE_MILLIS = 6_000
        const val EXIT_STREAK_THRESHOLD = 2

        /** How long queued input waits for the shell to report Pi running. */
        const val FLUSH_POLL_MILLIS = 100
        const val FLUSH_ATTEMPTS = 20
    }

    private data class PromptInput(val text: String, val submit: Boolean)
}
