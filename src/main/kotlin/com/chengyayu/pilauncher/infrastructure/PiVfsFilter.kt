package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Guards that keep a `go build`, `gofmt -w ./...` or `git checkout` inside the project from
 * opening hundreds of editor tabs at once.
 */
object PiVfsFilter {

    /** A project file that is neither ignored nor binary; safe from the VFS thread. */
    fun isRelevantChange(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid || file.isDirectory) return false

        val basePath = project.basePath ?: return false
        if (!FileUtil.isAncestor(basePath, file.path, false)) return false

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        if (!fileIndex.isInContent(file)) return false

        if (FileTypeRegistry.getInstance().isFileIgnored(file)) return false
        if (file.fileType.isBinary) return false

        return true
    }
}
