package com.chengyayu.pilauncher.infrastructure

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileCopyPasteUtil
import java.awt.datatransfer.DataFlavor

/**
 * What a terminal drop carries: a Finder drop and a drop from the IDE's tree arrive in
 * different shapes, and the platform's helper understands both.
 */
internal object DroppedFiles {

    fun pathsOf(attachedObject: Any?): List<String> {
        if (attachedObject == null) return emptyList()

        return FileCopyPasteUtil.getFileListFromAttachedObject(attachedObject)
            .mapNotNull { it.absolutePath }
            .filter { it.isNotBlank() }
    }

    /** The text a native drop carries, if any. */
    fun textOf(attachedObject: Any?): String? {
        val info = attachedObject as? DnDNativeTarget.EventInfo ?: return null
        val transferable = info.transferable ?: return null
        if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null

        return try {
            transferable.getTransferData(DataFlavor.stringFlavor) as? String
        } catch (_: Exception) {
            null
        }
    }
}

