package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Shared helpers deciding whether a VFS change is something the Pi plugin
 * should react to.
 *
 * Keeping these guards in place is what stops a `go build` / `gofmt -w ./...` /
 * `git checkout` inside the project from touching the editor for hundreds of
 * files at once.
 */
object PiVfsFilter {

    /**
     * A change is relevant only when it is a real content change of a file that
     * belongs to the project's content (not an excluded directory, not ignored
     * by VCS/file-type rules, not binary).
     *
     * Safe to call from a background thread (VFS change listener thread).
     */
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
