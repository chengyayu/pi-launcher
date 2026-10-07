package com.chengyayu.pilauncher.services

import com.intellij.util.messages.Topic

/** Whether a Pi session is currently running. */
enum class PiSessionStatus { IDLE, RUNNING }

/**
 * Notified when the Pi session starts or stops, so the status bar can refresh.
 *
 * Publishing through the project message bus keeps the session free of any
 * dependency on the UI that displays it.
 */
fun interface PiSessionListener {
    fun sessionStatusChanged(status: PiSessionStatus)

    companion object {
        @JvmField
        val TOPIC: Topic<PiSessionListener> = Topic.create(
            "Pi session status",
            PiSessionListener::class.java
        )
    }
}
