package com.chengyayu.pilauncher.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PiFileReferenceTest {

    @Test
    fun `whole file reference has no line anchor`() {
        assertEquals(
            "@lib/a.go",
            PiFileReference.ofFile("/proj", "/proj/lib/a.go").render()
        )
    }

    @Test
    fun `single line range collapses to one anchor`() {
        assertEquals(
            "@lib/a.go#L10",
            PiFileReference.ofRange("/proj", "/proj/lib/a.go", 10, 10).render()
        )
    }

    @Test
    fun `multi line range keeps both bounds`() {
        assertEquals(
            "@lib/a.go#L10-25",
            PiFileReference.ofRange("/proj", "/proj/lib/a.go", 10, 25).render()
        )
    }

    @Test
    fun `inverted range falls back to the start line`() {
        assertEquals(
            "@lib/a.go#L25",
            PiFileReference.ofRange("/proj", "/proj/lib/a.go", 25, 10).render()
        )
    }

    @Test
    fun `files outside the project keep their absolute path`() {
        assertEquals(
            "@/tmp/other.go",
            PiFileReference.ofFile("/proj", "/tmp/other.go").render()
        )
    }

    @Test
    fun `missing project base path leaves the path untouched`() {
        assertEquals("@/tmp/other.go", PiFileReference.ofFile(null, "/tmp/other.go").render())
        assertEquals("@/tmp/other.go", PiFileReference.ofFile("", "/tmp/other.go").render())
    }

    @Test
    fun `project root file is referenced without a leading slash`() {
        assertEquals("@main.go", PiFileReference.ofFile("/proj", "/proj/main.go").render())
    }

    @Test
    fun `nested paths are relativized`() {
        assertEquals(
            "@a/b/c/d.go",
            PiFileReference.ofFile("/proj", "/proj/a/b/c/d.go").render()
        )
    }
}
