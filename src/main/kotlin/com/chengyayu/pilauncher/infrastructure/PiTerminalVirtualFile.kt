package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile

/**
 * The virtual file an editor-tab Pi Session lives in: there is no file on disk, and the
 * only thing it carries is the terminal widget and the disposable that owns it.
 *
 * The platform does the same for its own terminal sessions
 * (`TerminalSessionVirtualFileImpl`): the widget's lifetime belongs to the editor, not
 * to a Terminal tool window tab, so closing the tab ends the run (`docs/adr/0006`).
 */
class PiTerminalVirtualFile(
    name: String,
    val terminal: PiTerminal,
    /** The engine's `TerminalWidget`, kept untyped for the reason in `docs/adr/0002`. */
    val widget: Any,
    /** Disposed when the editor tab closes, which kills the shell and with it Pi. */
    val lifetime: Disposable
) : LightVirtualFile(name, PlainTextFileType.INSTANCE, "")
