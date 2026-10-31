package com.chengyayu.pilauncher.infrastructure

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * The plugin only speaks up when something the user asked for failed: it never announces
 * routine work and never reports an exit, which is usually intentional.
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
