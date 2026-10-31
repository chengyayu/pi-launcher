package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Opened through `openFileEditor` because `openFile(file, focus)` is deprecated in 2026.3 and
 * its three-argument replacement throws `UnsupportedOperationException`.
 */
internal fun openFileInEditor(project: Project, file: VirtualFile, focus: Boolean) {
    FileEditorManager.getInstance(project).openFileEditor(Navigatable(file), focus)
}

private class Navigatable(private val file: VirtualFile) : FileEditorNavigatable {

    override fun getFile(): VirtualFile = file

    /** Where the user is, rather than in a second editor window. */
    override fun isUseCurrentWindow(): Boolean = true

    /** Never as a preview tab: a dashboard or a changed file deserves a real tab. */
    override fun isUsePreviewTab(): Boolean = false
}
