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
 * Creation belongs to [PiSessionRegistry]; a session starts with its Host already
 * attached. An exit is not reported to the user - `/exit` and a crash cannot be told
 * apart - but [onExited] tells the registry to stop counting it.
 *
 * Input sent before the start command has reached the terminal is held back: the
 * pty belongs to a plain shell until then, and text typed into it would be run
 * as part of the `pi …` command line instead of reaching Pi's prompt
 * (`docs/adr/0003`).
 */
internal class PiSession(
    /** The Host's display name, as the user sees it. Renaming the Host updates it. */
    @Volatile
    var tabName: String,
    /**
     * Pi's identity for this conversation: the id a resumed session already has, or the
     * one Pi reports once it has started (`docs/adr/0006`). It outlives this run;
     * [channelId] does not.
     */
    id: String?,
    /** The status channel's directory name: unique per run, discarded when the run ends. */
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
     * The last Session Status Pi reported, or null when none arrived yet.
     *
     * Null is a real answer, not a missing one: it means this session has no
     * Status Source or has not spoken yet, and callers must show Inferred Status
     * rather than treat the session as idle.
     */
    @Volatile
    var status: PiStatus? = null
        private set

    private var runningCheck: Disposable? = null

    private var statusCheck: Disposable? = null

    /**
     * Input that arrived before the shell started Pi, in arrival order. Null once the
     * input has been released, when later input goes straight through.
     */
    private var queuedInput: MutableList<PromptInput>? = mutableListOf()

    val isRunning: Boolean get() = !exited

    fun start() {
        commandDispatcher.submit(terminal, startCommand, onDelivered = ::flushWhenRunning)
        watchForExit()
        watchStatus()
    }

    /** Send [text] and submit it. */
    fun submitText(text: String) = send(text, submit = true)

    /** Type [text] into the prompt without submitting it. */
    fun insertText(text: String) = send(text, submit = false)

    /**
     * Type the files the user dropped onto this session's terminal as prompt
     * references, leaving them unsent.
     *
     * The paths are the drop's, not the project's: the same `@` rendering as Send to
     * Pi, so a dropped screenshot outside the project keeps its absolute path
     * (`docs/adr/0008`).
     */
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
     * Releases queued input once the shell reports Pi running.
     *
     * "The widget accepted the launch command" is not "the shell has run it": the block
     * terminal's `sendCommandToExecute` returns first, and a reference flushed in that gap
     * lands in front of the command line, where the shell runs both as one command
     * (`docs/adr/0003`). A foreground child is the observable end of that gap. When the
     * engine cannot tell (`null`), or the wait runs out, the input is released anyway
     * rather than stranded.
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

    /**
     * Polls the status channel Pi writes to.
     *
     * A poll rather than a filesystem watch: the reporter replaces its file
     * atomically on every event, and one `stat` every few hundred milliseconds is
     * both cheaper and far less surprising than a watch on a file that keeps being
     * renamed out from under the watch service.
     */
    private fun watchStatus() {
        val source = statusSource ?: return

        statusCheck = scheduler.schedule(STATUS_POLL_INTERVAL_MILLIS, repeat = true) {
            if (exited) return@schedule

            val report = source.read() ?: return@schedule
            val learned = adoptIdentity(report.identity)

            if (report.status == status && !learned) return@schedule

            status = report.status
            onStatusChanged(this)
        }
    }

    /**
     * Takes Pi's identity the first time Pi says it, and never again.
     *
     * The id is what the dashboard reopens a closed session with, so it has to be the one
     * Pi stored the conversation under; a later report must not be able to change it.
     */
    private fun adoptIdentity(reported: String?): Boolean {
        if (id != null || reported == null) return false
        if (!PiSessionId.isValid(reported)) return false

        id = reported
        return true
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
