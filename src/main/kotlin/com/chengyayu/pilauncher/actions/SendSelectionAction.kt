package com.chengyayu.pilauncher.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.chengyayu.pilauncher.domain.PiFileReference

/**
 * Send the selected code to Pi as `@relative/path#Lstart-end`.
 *
 * With no selection the whole file is referenced.
 */
class SendSelectionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = EventFiles.from(e).firstOrNull() ?: return

        val selection = editor.selectionModel
        val document = editor.document

        val reference = if (selection.hasSelection()) {
            PiFileReference.ofRange(
                projectBasePath = project.basePath,
                filePath = file.path,
                startLine = document.getLineNumber(selection.selectionStart) + 1,
                endLine = document.getLineNumber(selection.selectionEnd) + 1
            )
        } else {
            PiFileReference.ofFile(project.basePath, file.path)
        }

        PiPrompt.insertReferences(project, listOf(reference))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }
}
