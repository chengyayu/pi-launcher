package com.chengyayu.pilauncher.actions

import com.chengyayu.pilauncher.services.PiSessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * One-click launch: starts Pi in the Terminal tool window, or focuses the
 * existing tab when it is already running.
 */
class LaunchPiAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        PiSessionService.getInstance(project).launch()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
