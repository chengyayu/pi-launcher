package com.chengyayu.pilauncher.actions

import com.intellij.openapi.project.Project
import com.chengyayu.pilauncher.domain.PiFileReference
import com.chengyayu.pilauncher.services.PiSessionService

/**
 * Shared behaviour for "send something to Pi" actions: resolve the session,
 * start it if needed, insert the reference and focus the terminal.
 */
internal object PiPrompt {

    /**
     * Insert [references] into Pi's prompt, launching Pi first when needed.
     * A trailing space is added so the user can keep typing immediately.
     */
    fun insertReferences(project: Project, references: List<PiFileReference>) {
        if (references.isEmpty()) return

        val text = references.joinToString(" ") { it.render() }
        if (text.isBlank()) return

        val session = PiSessionService.getInstance(project)
        if (!session.isRunning()) {
            session.launch()
        }
        session.insertText("$text ")
        session.focus()
    }
}
