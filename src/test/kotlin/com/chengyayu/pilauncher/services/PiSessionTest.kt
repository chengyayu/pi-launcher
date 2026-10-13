package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.util.PiScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lifecycle of a single session: start command, prompt input, exit polling. */
class PiSessionTest {

    /** Advanced by tests that exercise the startup grace period. */
    private var clockMillis = 0L

    /**
     * Sends the start command straight away but captures the repeating exit poll
     * so a test can decide when (and whether) it runs.
     */
    private class ManualScheduler : PiScheduler {
        var repeating: (() -> Unit)? = null

        override fun schedule(delayMs: Int, repeat: Boolean, action: () -> Unit): Disposable {
            if (repeat) repeating = action else action()
            return NoopDisposable
        }
    }

    private var exited: PiSession? = null

    private var manual: ManualScheduler? = null

    private fun newSession(
        tabName: String = "Pi 1",
        startCommand: String = "pi --no-themes",
        scheduler: PiScheduler? = null,
        clock: (() -> Long)? = null,
        terminal: PiTerminal = FakeTerminal().also { terminals += it }
    ): PiSession {
        val pollScheduler = scheduler ?: ManualScheduler().also { manual = it }
        return PiSession(
            tabName = tabName,
            terminal = terminal,
            startCommand = startCommand,
            onExited = { exited = it },
            commandDispatcher = PiCommandDispatcher(settleMillis = 0, scheduler = ImmediateScheduler),
            scheduler = pollScheduler,
            clock = clock ?: { 0L }
        )
    }

    @Test
    fun `start submits the start command`() {
        newSession(startCommand = "pi --model p/m --no-themes").use { session ->
            session.start()
        }

        val sent = terminals.single().sent.single()
        assertEquals("pi --model p/m --no-themes", sent.text)
        assertTrue(sent.submitted, "the start command must be submitted")
    }

    @Test
    fun `insertText types without submitting`() {
        newSession().use { session ->
            session.start()
            session.insertText("@lib/a.go#L1-6 ")
        }

        val typed = terminals.single().sent.last()
        assertEquals("@lib/a.go#L1-6 ", typed.text)
        assertFalse(typed.submitted, "insertText must not press enter")
    }

    @Test
    fun `submitText types and submits`() {
        newSession().use { session ->
            session.start()
            session.submitText("hi")
        }

        assertEquals(true, terminals.single().sent.last().submitted)
    }

    @Test
    fun `input that arrives before the terminal is ready waits for the start command`() {
        val terminal = GatedTerminal()
        newSession(terminal = terminal).use { session ->
            session.start()
            session.insertText("@lib/a.go#L1-6 ")

            assertEquals(
                listOf("pi --no-themes"),
                terminal.attempts.map { it.text },
                "typing into a tab that has no shell yet is how the reference ends up " +
                    "in the shell the launch command is about to run"
            )

            terminal.release()
        }

        assertEquals(
            listOf("pi --no-themes", "@lib/a.go#L1-6 "),
            terminal.sent.map { it.text },
            "the held input must follow the start command, in arrival order"
        )
        assertEquals(listOf(true, false), terminal.sent.map { it.submitted })
    }

    @Test
    fun `held input is sent in arrival order and flushed only once`() {
        val terminal = GatedTerminal()
        newSession(terminal = terminal).use { session ->
            session.start()
            session.insertText("@a.go ")
            session.submitText("explain")

            terminal.release()
            terminal.release()
        }

        assertEquals(
            listOf("pi --no-themes", "@a.go ", "explain"),
            terminal.sent.map { it.text }
        )
        assertEquals(listOf(true, false, true), terminal.sent.map { it.submitted })
    }

    @Test
    fun `status falls back to exited once pi stops running`() {
        newSession(clock = { clockMillis }).use { session ->
            session.start()
            assertTrue(session.isRunning)

            terminals.single().commandRunning = false
            clockMillis += 10_000 // past the startup grace
            repeat(2) { manual!!.repeating!!.invoke() }

            assertFalse(session.isRunning, "an exited Pi must not stay running")
            assertEquals(session, exited, "the registry must be told about the exit")
        }
    }

    @Test
    fun `a single empty observation is not enough to call it exited`() {
        newSession(clock = { clockMillis }).use { session ->
            session.start()
            clockMillis += 10_000

            terminals.single().commandRunning = false
            manual!!.repeating!!.invoke()
            assertTrue(session.isRunning, "one gap must not end the session")

            terminals.single().commandRunning = true
            manual!!.repeating!!.invoke()
            assertTrue(session.isRunning)
        }
    }

    @Test
    fun `the startup grace protects against the shell having no child yet`() {
        newSession().use { session ->
            session.start()

            // Right after launch the shell has not spawned Pi yet.
            terminals.single().commandRunning = false
            repeat(5) { manual!!.repeating!!.invoke() }

            assertTrue(session.isRunning, "Pi must not be reported exited before it could start")
        }
    }

    @Test
    fun `an unknown running state keeps the previous status`() {
        newSession(clock = { clockMillis }).use { session ->
            session.start()
            clockMillis += 10_000

            terminals.single().commandRunning = null
            repeat(3) { manual!!.repeating!!.invoke() }

            assertTrue(session.isRunning, "an unknown answer must not be treated as an exit")
        }
    }

    @Test
    fun `a running command keeps the session alive`() {
        newSession(clock = { clockMillis }).use { session ->
            session.start()
            clockMillis += 10_000

            terminals.single().commandRunning = true
            repeat(3) { manual!!.repeating!!.invoke() }

            assertTrue(session.isRunning)
        }
    }

    @Test
    fun `the exit poll stops after dispose`() {
        newSession(clock = { clockMillis }).use { session ->
            session.start()
            clockMillis += 10_000
            session.dispose()

            // Even if the platform reports the command is gone, a disposed
            // session must not report an exit.
            terminals.single().commandRunning = false
            manual!!.repeating?.invoke()

            assertNull(exited, "a disposed session must not report an exit")
        }
    }

    // ---- fakes -------------------------------------------------------------

    private val terminals = mutableListOf<FakeTerminal>()

    private object NoopDisposable : Disposable {
        override fun dispose() = Unit
    }

    private object ImmediateScheduler : PiScheduler {
        override fun schedule(delayMs: Int, repeat: Boolean, action: () -> Unit): Disposable {
            action()
            return NoopDisposable
        }
    }

    private class FakeTerminal : PiTerminal {
        data class Sent(val text: String, val submitted: Boolean)

        val sent = mutableListOf<Sent>()
        var commandRunning: Boolean? = true

        override fun isForegroundCommandRunning(): Boolean? = commandRunning

        override fun send(text: String, submit: Boolean, onDelivered: () -> Unit) {
            sent += Sent(text, submit)
            onDelivered()
        }
    }

    /**
     * A tab in a Terminal window that was never opened: there is no pty for a
     * while, so a send is refused and only the pty's arrival delivers anything -
     * which is what the adapter's own retry loop ends up doing.
     */
    private class GatedTerminal : PiTerminal {
        data class Sent(val text: String, val submitted: Boolean)

        val sent = mutableListOf<Sent>()

        /** Every send the session tried, refused ones included. */
        val attempts = mutableListOf<Sent>()

        var commandRunning: Boolean? = true

        private var open = false
        private val refused = mutableListOf<Pair<Sent, () -> Unit>>()

        override fun isForegroundCommandRunning(): Boolean? = commandRunning

        override fun send(text: String, submit: Boolean, onDelivered: () -> Unit) {
            val attempt = Sent(text, submit)
            attempts += attempt

            if (!open) {
                refused += attempt to onDelivered
                return
            }

            sent += attempt
            onDelivered()
        }

        /** The pty appears: what was refused goes through, in attempt order. */
        fun release() {
            open = true
            val pending = refused.toList()
            refused.clear()
            pending.forEach { (attempt, onDelivered) ->
                sent += attempt
                onDelivered()
            }
        }
    }

    private inline fun PiSession.use(block: (PiSession) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
