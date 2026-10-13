package com.chengyayu.pilauncher.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * The Session Dashboard's virtual file: there is no file on disk, and there is only
 * ever one of these per project, so opening the dashboard always lands on the same
 * tab instead of piling up duplicates.
 *
 * The platform keys editors by `VirtualFile` identity, which is what makes the
 * singleton work. Nothing here is persisted, so a restart simply forgets the tab.
 */
internal class PiDashboardVirtualFile : LightVirtualFile(FILE_NAME, PlainTextFileType.INSTANCE, "") {

    companion object {
        private const val FILE_NAME = "Pi Dashboard"

        private val KEY: Key<PiDashboardVirtualFile> = Key.create("pi.launcher.dashboard")

        /** Open the dashboard, or bring the existing tab to the front. */
        fun open(project: Project) {
            FileEditorManager.getInstance(project).openFile(instance(project), true)
        }

        private fun instance(project: Project): PiDashboardVirtualFile =
            project.getUserData(KEY) ?: PiDashboardVirtualFile().also { project.putUserData(KEY, it) }
    }
}

/** Builds the dashboard tab, and claims only its own virtual file. */
class PiDashboardEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file is PiDashboardVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        PiDashboardEditor(project, file)

    override fun getEditorTypeId(): String = "PiLauncherDashboard"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private class PiDashboardEditor(
    project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val panel = PiDashboardPanel(project)

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel

    override fun getName(): String = file.name

    override fun getFile(): VirtualFile = file

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun setState(state: FileEditorState) = Unit

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() {
        panel.dispose()
    }
}
