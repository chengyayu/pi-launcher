package com.chengyayu.pilauncher.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.chengyayu.pilauncher.domain.PiFileReference

/** Sends one or more files or folders from the Project View / editor tab as `@relative/path` references. */
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
     * Resolving the selection reads PSI, which the platform only allows off the EDT; on the
     * default update thread every right-click in the Project View logs an internal error.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
