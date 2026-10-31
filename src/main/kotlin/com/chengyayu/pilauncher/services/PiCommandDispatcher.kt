package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.util.PiScheduler

/**
 * Submits the start command to a fresh tab: the widget offers no readiness signal, so it
 * waits out a short settle delay first.
 */
internal class PiCommandDispatcher(
    private val settleMillis: Int = SETTLE_MILLIS,
    private val scheduler: PiScheduler = PiScheduler.swing
) {
    fun submit(terminal: PiTerminal, command: String, onDelivered: () -> Unit = {}) {
        scheduler.schedule(settleMillis, repeat = false) {
            terminal.send(command, submit = true, onDelivered = onDelivered)
        }
    }

    companion object {
        const val SETTLE_MILLIS = 300
    }
}
