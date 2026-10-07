package com.chengyayu.pilauncher.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.chengyayu.pilauncher.infrastructure.PiTerminal

/**
 * Holds the current terminal/status and publishes transitions.
 *
 * Only real changes are published, so a repeated `markIdle` is free.
 */
internal class PiSessionState(private val publish: (PiSessionStatus) -> Unit) {

    @Volatile
    var status: PiSessionStatus = PiSessionStatus.IDLE
        private set

    @Volatile
    var terminal: PiTerminal? = null
        private set

    fun markRunning(terminal: PiTerminal) {
        this.terminal = terminal
        transitionTo(PiSessionStatus.RUNNING)
    }

    fun markIdle() {
        terminal = null
        transitionTo(PiSessionStatus.IDLE)
    }

    private fun transitionTo(next: PiSessionStatus) {
        if (status == next) return
        status = next
        publish(next)
    }

    companion object {
        /** Publishes on the project message bus, safely from any thread. */
        fun publisherFor(project: Project): (PiSessionStatus) -> Unit = { status ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    project.messageBus.syncPublisher(PiSessionListener.TOPIC).sessionStatusChanged(status)
                }
            }
        }
    }
}
