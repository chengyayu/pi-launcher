package com.chengyayu.pilauncher.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem

/** Resolves what an action was invoked on, in the shapes the platform delivers (editor, project view, PSI). */
internal object EventFiles {

    fun from(e: AnActionEvent): List<VirtualFile> {
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.takeIf { it.isNotEmpty() }?.let { return it.toList() }

        e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { return listOf(it) }

        e.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
            ?.mapNotNull { (it as? PsiFileSystemItem)?.virtualFile }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        e.getData(CommonDataKeys.PSI_FILE)?.virtualFile?.let { return listOf(it) }

        return emptyList()
    }

    /**
     * Files and folders alike: a folder is a path too, and Pi reads paths. Filtering out
     * directories is what used to grey out Send to Pi for a folder.
     */
    fun selectable(files: List<VirtualFile>): List<VirtualFile> = files

    fun selectable(e: AnActionEvent): List<VirtualFile> = selectable(from(e))
}
