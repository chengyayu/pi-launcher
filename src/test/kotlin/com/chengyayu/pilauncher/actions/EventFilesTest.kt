package com.chengyayu.pilauncher.actions

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the Project View hands to Send to Pi: a file and a folder are both paths. */
class EventFilesTest {

    @Test
    fun `files and folders are both sendable`() {
        val file = FakeFile("a.go")
        val folder = FakeFile("lib", directory = true)

        assertEquals(listOf(file, folder), EventFiles.selectable(listOf(file, folder)))
    }

    @Test
    fun `nothing selected is nothing to send`() {
        assertTrue(EventFiles.selectable(emptyList()).isEmpty())
    }

    private class FakeFile(
        private val name: String,
        private val directory: Boolean = false
    ) : VirtualFile() {

        override fun getName(): String = name

        override fun getFileSystem(): VirtualFileSystem = throw UnsupportedOperationException()

        override fun getPath(): String = "/proj/$name"

        override fun isWritable(): Boolean = false

        override fun isDirectory(): Boolean = directory

        override fun isValid(): Boolean = true

        override fun getParent(): VirtualFile? = null

        override fun getChildren(): Array<VirtualFile> = emptyArray()

        override fun getOutputStream(
            requestor: Any?,
            newModificationStamp: Long,
            newTimeStamp: Long
        ): OutputStream = throw UnsupportedOperationException()

        override fun contentsToByteArray(): ByteArray = throw UnsupportedOperationException()

        override fun getTimeStamp(): Long = 0

        override fun getLength(): Long = 0

        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = Unit

        override fun getInputStream(): InputStream = throw UnsupportedOperationException()
    }
}
