package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * Notified with the whole set of sessions whenever one is created, closed,
 * focused, or reports a new Session Status.
 *
 * A snapshot rather than a delta: the status bar and the Session Dashboard both
 * render the same list, and neither should be able to drift out of step by
 * missing an event.
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
