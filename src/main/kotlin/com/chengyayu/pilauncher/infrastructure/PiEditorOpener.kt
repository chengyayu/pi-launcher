package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Through `OpenFileDescriptor`, not the `FileEditorManager.openFile*` overloads: 2026.3 deprecates
 * every blocking open in favour of a non-blocking one that does not exist below 2026.3.
 */
internal fun openFileInEditor(project: Project, file: VirtualFile, focus: Boolean) {
    OpenFileDescriptor(project, file)
        .setUseCurrentWindow(true)
        .setUsePreviewTab(false)
        .navigate(focus)
}
