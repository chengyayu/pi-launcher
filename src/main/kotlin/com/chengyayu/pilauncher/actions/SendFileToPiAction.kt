package com.chengyayu.pilauncher.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.chengyayu.pilauncher.domain.PiFileReference

/**
 * Send one or more files from the Project View / editor tab to Pi as
 * `@relative/path` references.
 */
class SendFileToPiAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val references = EventFiles.selectable(e).map {
            PiFileReference.ofFile(project.basePath, it.path)
        }

        PiPrompt.insertReferences(project, references)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
        e.presentation.isEnabled = EventFiles.selectable(e).isNotEmpty()
    }

    /**
     * Resolving the selected files reads PSI (`psi.Element.array`), which the
     * platform only allows off the EDT; leaving the default update thread makes
     * every right-click in the Project View log an internal error.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
