package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.util.PiScheduler

/**
 * Submits the Pi start command to a freshly created terminal tab.
 *
 * The terminal component exists before its shell is attached and the widget
 * offers no readiness signal, so the command is sent after a short settle delay.
 */
internal class PiCommandDispatcher(
    private val settleMillis: Int = SETTLE_MILLIS,
    private val scheduler: PiScheduler = PiScheduler.swing
) {
    fun submit(terminal: PiTerminal, command: String, onSent: () -> Unit = {}) {
        scheduler.schedule(settleMillis, repeat = false) {
            terminal.send(command, submit = true)
            onSent()
        }
    }

    companion object {
        const val SETTLE_MILLIS = 300
    }
}
