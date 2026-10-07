package com.chengyayu.pilauncher.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem

/**
 * Resolves the files an action was invoked on, tolerating the several shapes the
 * platform delivers them in (editor, project view, PSI).
 */
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

    /** Files that can be referenced, i.e. everything but directories. */
    fun selectable(e: AnActionEvent): List<VirtualFile> = from(e).filterNot { it.isDirectory }
}
