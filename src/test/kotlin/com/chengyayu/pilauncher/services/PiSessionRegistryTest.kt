package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.infrastructure.PiTerminalProvider
import com.chengyayu.pilauncher.util.PiScheduler
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Launch semantics, tab naming, the Active Session pointer and exit rollover.
 * Single-session lifecycle (start command, exit polling) is PiSessionTest's job.
 */
class PiSessionRegistryTest {

    private val provider = FakeTerminalProvider()
    private val notifier = FakeNotifier()
    private val counts = CopyOnWriteArrayList<Int>()

    /** Advances 10s per read, so every poll is past the startup grace. */
    private var tick = 0L

    private val polls = mutableListOf<() -> Unit>()

    private val pollScheduler = PiScheduler { _, repeat, action ->
        if (repeat) polls += action else action()
        NoopDisposable
    }

    /** One call = one observation per session; two in a row register an exit. */
    private fun runPolls() = polls.forEach { it() }

    private fun newRegistry() = PiSessionRegistry(
        workingDirectory = { "/work" },
        startCommand = { "pi --no-themes" },
        terminals = provider,
        notifier = notifier,
        publishCount = { counts += it },
        commandDispatcher = PiCommandDispatcher(settleMillis = 0, scheduler = ImmediateScheduler),
        scheduler = pollScheduler,
        clock = { tick += 10_000; tick }
    )

    @Test
    fun `launch with no sessions creates a tab and starts pi`() {
        newRegistry().use {
            it.launch()
        }

        assertEquals("/work", provider.createdWorkingDirectory)
        assertEquals("Pi 1", provider.created.single().tabName)

        val sent = provider.created.single().terminal.sent.single()
        assertEquals("pi --no-themes", sent.text)
        assertTrue(sent.submitted, "the start command must be submitted")
    }

    @Test
    fun `launching twice only focuses the active session`() {
        newRegistry().use {
            it.launch()
            it.launch()
        }

        assertEquals(1, provider.created.size, "a second launch must not create another tab")
        assertEquals(1, provider.focusCount, "the second launch focuses the existing tab")
    }

    @Test
    fun `a new session gets the next free number`() {
        newRegistry().use {
            it.launch()
            it.newSession()
        }

        assertEquals(listOf("Pi 1", "Pi 2"), provider.created.map { it.tabName })
    }

    @Test
    fun `numbers are not reused while the old tab is still open`() {
        newRegistry().use {
            it.launch()
            // The session exits (its tab stays open), then a new one starts.
            provider.created.single().commandRunning = false
            runPolls()
            runPolls()
            it.launch()

            assertEquals(listOf("Pi 1", "Pi 2"), provider.created.map { it.tabName })
        }
    }

    @Test
    fun `existing tabs outside the registry also block their number`() {
        provider.existingTabNames = setOf("Pi 1", "Local")

        newRegistry().use {
            it.launch()
        }

        assertEquals("Pi 2", provider.created.single().tabName)
    }

    @Test
    fun `send goes to the active session, the most recently created`() {
        newRegistry().use {
            it.launch()
            it.newSession()
            it.insertText("@a.go ")
            it.submitText("hi")
        }

        val first = provider.created[0].terminal.sent
        val second = provider.created[1].terminal.sent

        assertEquals(listOf("pi --no-themes"), first.map { it.text }, "only the start command")
        assertEquals(
            listOf("pi --no-themes", "@a.go ", "hi"),
            second.map { it.text },
            "prompt input must reach the newest session"
        )
    }

    @Test
    fun `selecting a Pi tab makes that session active`() {
        newRegistry().use {
            it.launch()
            it.newSession()

            provider.select("Pi 1")
            it.insertText("@b.go ")
        }

        val first = provider.created[0].terminal.sent
        assertEquals(listOf("pi --no-themes", "@b.go "), first.map { it.text })
    }

    @Test
    fun `selecting a tab that is not a live session changes nothing`() {
        newRegistry().use {
            it.launch()

            provider.select("Local")
            provider.select("Pi 2")

            it.insertText("@c.go ")
        }

        val first = provider.created[0].terminal.sent
        assertEquals(listOf("pi --no-themes", "@c.go "), first.map { it.text })
    }

    @Test
    fun `an exited session leaves the registry and the active pointer rolls over`() {
        newRegistry().use {
            it.launch()
            it.newSession()

            // "Pi 2" is active; both exit (two poll rounds register the streak).
            provider.created[1].commandRunning = false
            provider.created[0].commandRunning = false
            runPolls()
            runPolls()

            assertEquals(0, it.runningCount, "exited sessions must leave the registry")
            assertNull(it.active)
            assertEquals(listOf(1, 2, 1, 0), counts, "the count must follow every change")
        }
    }

    @Test
    fun `an exited active session rolls over to the remaining one`() {
        newRegistry().use {
            it.launch()
            it.newSession()

            provider.created[1].commandRunning = false
            runPolls()
            runPolls()
            assertTrue(it.isRunning(), "one session must remain")
            assertEquals("Pi 1", it.active?.tabName, "the remaining session becomes active")

            it.insertText("@d.go ")
            val first = provider.created[0].terminal.sent
            assertEquals(listOf("pi --no-themes", "@d.go "), first.map { it.text })
        }
    }

    @Test
    fun `a failing terminal provider reports an error and keeps the registry clean`() {
        provider.failOnCreate = true

        newRegistry().use {
            it.launch()

            assertTrue(!it.isRunning(), "a failed launch must not report running")
            assertTrue(notifier.errors.single().startsWith("Failed to start Pi"))
        }
    }

    @Test
    fun `the running count is published as sessions come and go`() {
        newRegistry().use {
            it.launch()
            it.newSession()
        }

        assertEquals(listOf(1, 2), counts)
    }

    @Test
    fun `closing a tab drops its session from the count`() {
        newRegistry().use {
            it.launch()
            it.newSession()

            provider.close("Pi 1")

            assertEquals(1, it.runningCount)
            assertEquals("Pi 2", it.active?.tabName, "the remaining session stays active")
            assertEquals(listOf(1, 2, 1), counts)
        }
    }

    @Test
    fun `closing the active tab rolls the active pointer over`() {
        newRegistry().use {
            it.launch()
            it.newSession()

            provider.close("Pi 2")

            assertEquals("Pi 1", it.active?.tabName)
            it.insertText("@e.go ")
            val first = provider.created[0].terminal.sent
            assertEquals(listOf("pi --no-themes", "@e.go "), first.map { it.text })
        }
    }

    @Test
    fun `a closed tab frees its number for the next session`() {
        newRegistry().use {
            it.launch()
            provider.close("Pi 1")
            it.launch()
        }

        assertEquals(listOf("Pi 1", "Pi 1"), provider.created.map { it.tabName })
    }

    @Test
    fun `focus with no sessions is a no-op`() {
        newRegistry().use {
            it.focus()
        }

        assertEquals(0, provider.focusCount)
    }

    // ---- fakes -------------------------------------------------------------

    private object NoopDisposable : Disposable {
        override fun dispose() = Unit
    }

    private object ImmediateScheduler : PiScheduler {
        override fun schedule(delayMs: Int, repeat: Boolean, action: () -> Unit): Disposable {
            // Exit polls run straight away, so tests stay synchronous.
            action()
            return NoopDisposable
        }
    }

    private class CreatedTab(val tabName: String, val terminal: FakeTerminal) {
        var commandRunning: Boolean?
            get() = terminal.commandRunning
            set(value) {
                terminal.commandRunning = value
            }
    }

    private class FakeTerminalProvider : PiTerminalProvider {
        val created = mutableListOf<CreatedTab>()
        val closed = mutableListOf<String>()
        var createdWorkingDirectory: String? = null
        var focusCount = 0
        var failOnCreate = false
        var existingTabNames: Set<String> = emptySet()

        private var selectionListener: ((String) -> Unit)? = null
        private var closeListener: ((String) -> Unit)? = null

        override fun createTerminalTab(workingDirectory: String, tabName: String): PiTerminal {
            if (failOnCreate) error("terminal unavailable")
            createdWorkingDirectory = workingDirectory
            val terminal = FakeTerminal()
            created += CreatedTab(tabName, terminal)
            return terminal
        }

        override fun focusTerminalTab(tabName: String) {
            focusCount++
        }

        override fun existingTabNames(): Set<String> =
            existingTabNames + created.map { it.tabName }.toSet() - closed.toSet()

        override fun onTabSelected(listener: (String) -> Unit) {
            selectionListener = listener
        }

        override fun onTabClosed(listener: (String) -> Unit) {
            closeListener = listener
        }

        /** Simulates the user clicking a Terminal tab. */
        fun select(tabName: String) {
            selectionListener?.invoke(tabName)
        }

        /** Simulates the user closing a Terminal tab. */
        fun close(tabName: String) {
            closed += tabName
            closeListener?.invoke(tabName)
        }
    }

    private class FakeTerminal : PiTerminal {
        data class Sent(val text: String, val submitted: Boolean)

        val sent = mutableListOf<Sent>()
        var commandRunning: Boolean? = true

        override fun isForegroundCommandRunning(): Boolean? = commandRunning

        override fun send(text: String, submit: Boolean) {
            sent += Sent(text, submit)
        }
    }

    private class FakeNotifier : PiNotifier {
        val errors = mutableListOf<String>()

        override fun error(message: String) {
            errors += message
        }
    }

    private inline fun PiSessionRegistry.use(block: (PiSessionRegistry) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
