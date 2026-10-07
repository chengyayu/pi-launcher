package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.domain.PiCommandLine
import com.chengyayu.pilauncher.domain.PiLaunchOptions
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.infrastructure.PiTerminalProvider
import com.chengyayu.pilauncher.util.PiScheduler

/**
 * The Pi session logic: one terminal tab, started once, with the start command
 * derived from the current settings.
 *
 * This is a plain class rather than an IntelliJ service on purpose. Services may
 * only have a `(Project)` / `(Project, CoroutineScope)` constructor, and keeping
 * the collaborators here also means the behaviour can be driven with fakes in
 * tests - hence the absence of any `Project` dependency.
 *
 * There is no notification on exit - an exit is often intentional (`/exit`,
 * Ctrl+D) and cannot be told apart from a crash - but the status does follow it,
 * so the status bar stops claiming Pi is running once it is gone.
 */
internal class PiSession(
    private val workingDirectory: () -> String,
    private val publishStatus: (PiSessionStatus) -> Unit,
    private val terminals: PiTerminalProvider,
    private val notifier: PiNotifier,
    private val launchOptions: () -> PiLaunchOptions,
    private val commandDispatcher: PiCommandDispatcher = PiCommandDispatcher(),
    private val scheduler: PiScheduler = PiScheduler.swing,
    private val clock: () -> Long = System::currentTimeMillis
) : Disposable {

    private val logger = Logger.getInstance(PiSession::class.java)
    private val state = PiSessionState(publishStatus)

    private var runningCheck: Disposable? = null

    val status: PiSessionStatus get() = state.status

    fun isRunning(): Boolean = state.status == PiSessionStatus.RUNNING

    /** Start Pi, or focus it when it is already running. */
    fun launch() {
        if (isRunning() && isTerminalAttached()) {
            terminals.focusTerminalTab(TAB_NAME)
            return
        }

        if (isRunning()) {
            logger.info("Pi terminal is gone; restarting")
            reset()
        }

        try {
            val terminal = terminals.createTerminalTab(workingDirectory(), TAB_NAME)
            state.markRunning(terminal)
            commandDispatcher.submit(terminal, PiCommandLine.render(launchOptions()))
            startWatchingForExit(terminal)
            logger.info("Pi session started")
        } catch (e: Exception) {
            // Handled: the user gets a notification, so this is a warning rather
            // than an IDE internal error.
            logger.warn("Failed to launch Pi", e)
            state.markIdle()
            notifier.error("Failed to start Pi: ${e.message}")
        }
    }

    /** Send [text] to Pi and submit it. */
    fun submitText(text: String) {
        state.terminal?.send(text, submit = true)
    }

    /** Type [text] into Pi's prompt without submitting it. */
    fun insertText(text: String) {
        state.terminal?.send(text, submit = false)
    }

    fun focus() {
        terminals.focusTerminalTab(TAB_NAME)
    }

    /** Drop all session state so the next [launch] starts fresh. */
    fun reset() {
        stopWatchingForExit()
        state.markIdle()
    }

    /**
     * Polls whether Pi is still running so the status bar does not claim it is
     * after the user quit it.
     *
     * Two guards keep the heuristic honest:
     *  - a startup grace, because the shell has no child until Pi is actually
     *    spawned (and the user's shell rc may take a while);
     *  - a streak requirement, so a single transient gap does not count.
     *
     * A `null` answer means the platform could not tell, in which case the
     * previous state is kept rather than inventing an exit.
     */
    private fun startWatchingForExit(terminal: PiTerminal) {
        stopWatchingForExit()

        val startedAt = clock()
        var emptyObservations = 0

        runningCheck = scheduler.schedule(EXIT_POLL_INTERVAL_MILLIS, repeat = true) {
            if (!isRunning()) return@schedule
            if (clock() - startedAt < STARTUP_GRACE_MILLIS) return@schedule

            when (terminal.isForegroundCommandRunning()) {
                false -> {
                    emptyObservations++
                    if (emptyObservations >= EXIT_STREAK_THRESHOLD) {
                        logger.info("Pi is no longer running")
                        reset()
                    }
                }

                // true, or unknown: Pi is (probably) still there.
                else -> emptyObservations = 0
            }
        }
    }

    private fun stopWatchingForExit() {
        runningCheck?.dispose()
        runningCheck = null
    }

    private fun isTerminalAttached(): Boolean = state.terminal?.isAttached() == true

    override fun dispose() = reset()

    companion object {
        const val TAB_NAME = "Pi"

        private const val EXIT_POLL_INTERVAL_MILLIS = 2_000

        /** Pi spawns asynchronously; ignore the window before it exists. */
        private const val STARTUP_GRACE_MILLIS = 6_000

        /** Consecutive "no child process" answers required to call it exited. */
        private const val EXIT_STREAK_THRESHOLD = 2
    }
}
