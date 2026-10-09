package com.chengyayu.pilauncher.actions

import com.chengyayu.pilauncher.services.PiSessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Starts an additional Pi Session in its own Terminal tab, alongside the ones
 * already running. Reachable from the Terminal tab context menu and from
 * Search Everywhere; no default shortcut, bind your own if needed.
 */
class NewPiSessionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        PiSessionService.getInstance(project).newSession()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
