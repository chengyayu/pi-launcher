package com.chengyayu.pilauncher.infrastructure

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileCopyPasteUtil
import java.awt.datatransfer.DataFlavor

/**
 * What a terminal drop carries.
 *
 * A drop from Finder arrives as a `DnDNativeTarget.EventInfo`, a drop from the
 * IDE's own tree as a `TransferableWrapper`; the platform's helper understands
 * both. A drop with neither files nor text yields nothing, which is how the
 * caller tells "this is not ours" from "nothing to insert".
 */
internal object DroppedFiles {

    fun pathsOf(attachedObject: Any?): List<String> {
        if (attachedObject == null) return emptyList()

        return FileCopyPasteUtil.getFileListFromAttachedObject(attachedObject)
            .mapNotNull { it.absolutePath }
            .filter { it.isNotBlank() }
    }

    /** The text a native drop carries, if any; the same flavour the platform reads. */
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

