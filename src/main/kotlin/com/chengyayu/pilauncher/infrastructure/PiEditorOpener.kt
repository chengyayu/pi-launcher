package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Opens [file] in the editor area, focused or in the background.
 *
 * `FileEditorManager.openFile(file, focus)` is deprecated as of 2026.3, and the
 * three-argument overload that looks like its replacement is not implemented by the
 * platform's own manager - it throws `UnsupportedOperationException`. `openFileEditor`
 * is the primitive both are built on (the deprecated call itself delegates to it), and
 * it takes the focus decision explicitly.
 */
internal fun openFileInEditor(project: Project, file: VirtualFile, focus: Boolean) {
    FileEditorManager.getInstance(project).openFileEditor(Navigatable(file), focus)
}

private class Navigatable(private val file: VirtualFile) : FileEditorNavigatable {

    override fun getFile(): VirtualFile = file

    /** Open where the user is, rather than in a second editor window. */
    override fun isUseCurrentWindow(): Boolean = true

    /** Never as a preview tab: a dashboard or a changed file deserves a real tab. */
    override fun isUsePreviewTab(): Boolean = false
}
