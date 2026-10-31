package com.chengyayu.pilauncher.infrastructure

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileFlavorProvider
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DroppedFilesTest {

    private class FakeFileList(private val files: List<File>) : FileFlavorProvider {
        override fun asFileList(): List<File> = files
    }

    /** The constructor is package-private in the platform; production only ever reads these. */
    private fun nativeDrop(text: String): Any {
        val constructor = DnDNativeTarget.EventInfo::class.java.getDeclaredConstructor(
            Array<DataFlavor>::class.java,
            Transferable::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(arrayOf(DataFlavor.stringFlavor), StringSelection(text))
    }

    @Test
    fun `absolute paths are kept in drop order`() {
        val attached = FakeFileList(listOf(File("/tmp/b.png"), File("/work/a.go")))

        assertEquals(listOf("/tmp/b.png", "/work/a.go"), DroppedFiles.pathsOf(attached))
    }

    @Test
    fun `a drop without files yields nothing`() {
        assertTrue(DroppedFiles.pathsOf(null).isEmpty())
        assertTrue(DroppedFiles.pathsOf(FakeFileList(emptyList())).isEmpty())
        assertTrue(DroppedFiles.pathsOf("just text").isEmpty(), "a text drop is not a file drop")
    }

    @Test
    fun `text is read from a native drop`() {
        assertEquals("hello", DroppedFiles.textOf(nativeDrop("hello")))
    }

    @Test
    fun `an internal drop carries no text`() {
        assertNull(DroppedFiles.textOf(FakeFileList(emptyList())))
        assertNull(DroppedFiles.textOf(null))
    }
}
