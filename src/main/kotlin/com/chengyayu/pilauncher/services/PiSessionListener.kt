package com.chengyayu.pilauncher.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * Notified whenever the running-session count changes, so the status bar can
 * refresh and file watching can follow the sessions.
 */
fun interface PiSessionListener {
    fun sessionCountChanged(runningCount: Int)

    companion object {
        @JvmField
        val TOPIC: Topic<PiSessionListener> = Topic.create(
            "Pi session status",
            PiSessionListener::class.java
        )

        /** Publishes on the project message bus, safely from any thread. */
        fun publisherFor(project: Project): (Int) -> Unit = { runningCount ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    project.messageBus.syncPublisher(TOPIC).sessionCountChanged(runningCount)
                }
            }
        }
    }
}
