package com.chengyayu.pilauncher.util

import com.intellij.openapi.Disposable
import javax.swing.Timer

/** Delayed work behind an interface, so session logic runs without real Swing timers. */
internal fun interface PiScheduler {
    fun schedule(delayMs: Int, repeat: Boolean, action: () -> Unit): Disposable

    companion object {
        /** Swing timers, so callbacks land on the EDT. */
        val swing: PiScheduler = PiScheduler { delayMs, repeat, action ->
            val timer = Timer(delayMs) { action() }
            timer.isRepeats = repeat
            timer.start()
            object : Disposable {
                override fun dispose() = timer.stop()
            }
        }
    }
}
