package com.chengyayu.pilauncher.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/** Registers the Pi entry in the status bar. */
class PiStatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ID
    override fun getDisplayName(): String = "Pi Launcher Status"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = PiStatusWidget(project)

    companion object {
        const val ID = "PiLauncherStatus"
    }
}
