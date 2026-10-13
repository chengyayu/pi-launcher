package com.chengyayu.pilauncher.domain

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The file the status extension writes (`docs/adr/0005`). The payloads below are what
 * that extension produces, so these tests pin the contract the two halves share.
 */
class PiStatusReportTest {

    @Test
    fun `decodes a bare state`() {
        val status = PiStatusReport.parse("state=working\n")

        assertEquals(PiStatus.State.WORKING, status?.state)
        assertNull(status?.kind)
        assertNull(status?.message)
    }

    @Test
    fun `decodes a state with no activity`() {
        val status = PiStatusReport.parse("state=idle\n")

        assertNull(status?.activity)
    }

    @Test
    fun `decodes every state the extension can report`() {
        val states = mapOf(
            "idle" to PiStatus.State.IDLE,
            "working" to PiStatus.State.WORKING,
            "blocked" to PiStatus.State.BLOCKED,
            "done" to PiStatus.State.DONE,
            "error" to PiStatus.State.ERROR
        )

        states.forEach { (wire, expected) ->
            assertEquals(expected, PiStatusReport.parse("state=$wire\n")?.state, wire)
        }
    }

    @Test
    fun `decodes a blocking prompt with its kind and title`() {
        val status = PiStatusReport.parse(
            "state=blocked\nkind=confirm\nmessage=${base64("Allow tool call?")}\n"
        )

        assertEquals(PiStatus.State.BLOCKED, status?.state)
        assertEquals(PiStatus.Kind.CONFIRM, status?.kind)
        assertEquals("Allow tool call?", status?.message)
    }

    @Test
    fun `decodes every prompt kind Pi raises`() {
        val kinds = mapOf(
            "confirm" to PiStatus.Kind.CONFIRM,
            "select" to PiStatus.Kind.SELECT,
            "input" to PiStatus.Kind.INPUT,
            "editor" to PiStatus.Kind.EDITOR,
            "custom" to PiStatus.Kind.CUSTOM
        )

        kinds.forEach { (wire, expected) ->
            assertEquals(expected, PiStatusReport.parse("state=blocked\nkind=$wire\n")?.kind, wire)
        }
    }

    @Test
    fun `decodes a multi-byte message`() {
        val status = PiStatusReport.parse("state=blocked\nmessage=${base64("继续执行吗？")}\n")

        assertEquals("继续执行吗？", status?.message)
    }

    /** Base64 padding contains '=', which must not be read as a key separator. */
    @Test
    fun `decodes a message whose padding looks like a separator`() {
        assertEquals("a", PiStatusReport.parse("state=working\nmessage=${base64("a")}\n")?.message)
    }

    @Test
    fun `decodes what Pi is doing while it works`() {
        val status = PiStatusReport.parse("state=working\nactivity=${base64("bash: go build ./...")}\n")

        assertEquals(PiStatus.State.WORKING, status?.state)
        assertEquals("bash: go build ./...", status?.activity)
        assertNull(status?.message, "an activity is not an explanation")
    }

    @Test
    fun `keeps an explanation and an activity apart`() {
        val status = PiStatusReport.parse(
            "state=blocked\nkind=confirm\nmessage=${base64("Allow tool call?")}\nactivity=${base64("bash: rm -rf build")}\n"
        )

        assertEquals("Allow tool call?", status?.message)
        assertEquals("bash: rm -rf build", status?.activity)
    }

    @Test
    fun `ignores unknown keys so a newer extension still works`() {
        val status = PiStatusReport.parse("state=error\nkind=x\nfuture=1\nmessage=${base64("boom")}\n")

        assertEquals(PiStatus.State.ERROR, status?.state)
        assertEquals("boom", status?.message)
    }

    @Test
    fun `keeps the state when the message is not decodable`() {
        val status = PiStatusReport.parse("state=blocked\nkind=input\nmessage=!!!not-base64!!!\n")

        assertEquals(PiStatus.State.BLOCKED, status?.state)
        assertEquals(PiStatus.Kind.INPUT, status?.kind)
        assertNull(status?.message)
    }

    /** A half-written or truncated file must read as "no report", never as a state. */
    @Test
    fun `rejects anything that is not a report`() {
        assertNull(PiStatusReport.parse(""))
        assertNull(PiStatusReport.parse("\n\n"))
        assertNull(PiStatusReport.parse("state=\n"))
        assertNull(PiStatusReport.parse("state=thinking\n"))
        assertNull(PiStatusReport.parse("kind=confirm\nmessage=x\n"))
        assertNull(PiStatusReport.parse("state\n"))
    }

    @Test
    fun `tolerates blank lines and spaces around keys`() {
        val status = PiStatusReport.parse("\n state = done \n\n")

        assertEquals(PiStatus.State.DONE, status?.state)
    }

    private fun base64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
