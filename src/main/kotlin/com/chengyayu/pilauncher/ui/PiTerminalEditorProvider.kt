package com.chengyayu.pilauncher.ui

import com.chengyayu.pilauncher.infrastructure.PiTerminalVirtualFile
import com.chengyayu.pilauncher.infrastructure.callOrNull
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shows one Pi Session's terminal as an editor tab (`docs/adr/0006`).
 *
 * The component *is* the terminal widget's own component - the platform's
 * `TerminalSessionEditor` does exactly this - so the editor adds an editor tab around
 * the terminal and nothing else. Closing the tab disposes the widget's lifetime, which
 * ends the run; the conversation is parked by the registry, not by this class.
 */
class PiTerminalEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file is PiTerminalVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        PiTerminalEditor(file as PiTerminalVirtualFile)

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    /** The terminal is the editor: no text editor may be shown beside it. */
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    private companion object {
        const val EDITOR_TYPE_ID = "PiLauncherTerminal"
    }
}

private class PiTerminalEditor(private val file: PiTerminalVirtualFile) : UserDataHolderBase(), FileEditor {

    private val component: JComponent =
        file.widget.callOrNull("getComponent") as? JComponent ?: JPanel()

    private val focusTarget: JComponent =
        file.widget.callOrNull("getPreferredFocusableComponent") as? JComponent ?: component

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = focusTarget

    override fun getName(): String = file.name

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    /** Closing the tab kills the shell, and with it Pi; the widget's lifetime is the run. */
    override fun dispose() = Disposer.dispose(file.lifetime)
}
