package com.chengyayu.pilauncher.infrastructure

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * User-facing notifications, so the session logic does not depend on the
 * notification API directly (and can be exercised with a no-op implementation).
 *
 * Deliberately minimal. The plugin only speaks up when something the user asked
 * for failed; it never announces routine work, and it never reports that Pi
 * exited, because an exit is usually intentional and cannot be distinguished
 * from a crash.
 */
interface PiNotifier {
    fun error(message: String)
}

class IdePiNotifier(private val project: Project) : PiNotifier {

    override fun error(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, NotificationType.ERROR)
            .notify(project)
    }

    private companion object {
        const val GROUP_ID = "Pi Launcher"
    }
}
