package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * The whole set of sessions, as a snapshot rather than a delta, so the status bar and the
 * dashboard cannot drift out of step by missing an event.
 */
fun interface PiSessionStatusListener {
    fun sessionsChanged(sessions: List<PiSessionReadout>)

    companion object {
        @JvmField
        val TOPIC: Topic<PiSessionStatusListener> = Topic.create(
            "Pi session statuses",
            PiSessionStatusListener::class.java
        )

        /** Publishes on the project message bus, safely from any thread. */
        fun publisherFor(project: Project): (List<PiSessionReadout>) -> Unit = { sessions ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    project.messageBus.syncPublisher(TOPIC).sessionsChanged(sessions)
                }
            }
        }
    }
}
