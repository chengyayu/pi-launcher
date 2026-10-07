package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.chengyayu.pilauncher.domain.PiLaunchOptions
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.infrastructure.PiTerminalProvider
import com.chengyayu.pilauncher.util.PiScheduler
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiSessionTest {

    private val terminals = FakeTerminalProvider()
    private val notifier = FakeNotifier()
    private val statuses = CopyOnWriteArrayList<PiSessionStatus>()

    /** Advanced by tests that exercise the startup grace period. */
    private var clockMillis = 0L

    /** Runs scheduled work immediately so the tests stay synchronous. */
    private val immediateScheduler = PiScheduler { _, _, action ->
        action()
        NoopDisposable
    }

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

    private fun newSession(
        workingDirectory: String = "/work",
        options: PiLaunchOptions = PiLaunchOptions(),
        scheduler: PiScheduler = immediateScheduler,
        clock: () -> Long = { clockMillis }
    ) = PiSession(
        workingDirectory = { workingDirectory },
        publishStatus = { statuses += it },
        terminals = terminals,
        notifier = notifier,
        launchOptions = { options },
        commandDispatcher = PiCommandDispatcher(settleMillis = 0, scheduler = immediateScheduler),
        scheduler = scheduler,
        clock = clock
    )

    @Test
    fun `launch creates a tab in the project directory and starts pi`() {
        newSession(workingDirectory = "/work", options = PiLaunchOptions(model = "p/m")).use {
            it.launch()
        }

        assertEquals("/work", terminals.createdWorkingDirectory)
        assertEquals(PiSession.TAB_NAME, terminals.createdTabName)

        val command = terminals.lastTerminal?.sent?.single()
        assertEquals("pi --model p/m --no-themes", command?.text)
        assertTrue(command?.submitted == true, "the start command must be submitted")
    }

    @Test
    fun `launching twice only focuses the existing terminal`() {
        newSession().use {
            it.launch()
            it.launch()
        }

        assertEquals(1, terminals.created.size, "a second launch must not create another tab")
        assertEquals(1, terminals.focusCount)
    }

    @Test
    fun `a detached terminal is replaced on the next launch`() {
        newSession().use { session ->
            session.launch()
            terminals.lastTerminal?.attached = false
            session.launch()
        }

        assertEquals(2, terminals.created.size, "a dead tab must be recreated")
    }

    @Test
    fun `insertText types without submitting`() {
        newSession().use { session ->
            session.launch()
            session.insertText("@lib/a.go#L1-6 ")
        }

        val typed = terminals.lastTerminal?.sent?.last()
        assertEquals("@lib/a.go#L1-6 ", typed?.text)
        assertFalse(typed?.submitted == true, "insertText must not press enter")
    }

    @Test
    fun `submitText types and submits`() {
        newSession().use { session ->
            session.launch()
            session.submitText("hi")
        }

        assertEquals(true, terminals.lastTerminal?.sent?.last()?.submitted)
    }

    @Test
    fun `status is published on start and reset`() {
        newSession().use { session ->
            session.launch()
            session.reset()
        }

        assertEquals(listOf(PiSessionStatus.RUNNING, PiSessionStatus.IDLE), statuses)
    }

    @Test
    fun `a failing terminal provider reports an error and stays idle`() {
        terminals.failOnCreate = true

        newSession().use { session ->
            session.launch()
            assertFalse(session.isRunning(), "a failed launch must not report running")
        }

        assertTrue(notifier.errors.single().startsWith("Failed to start Pi"))
    }

    @Test
    fun `reset clears the command target`() {
        newSession().use { session ->
            session.launch()
            session.reset()
            session.insertText("ignored")
        }

        assertEquals(1, terminals.lastTerminal?.sent?.size, "after reset nothing should be sent")
    }

    @Test
    fun `status falls back to idle once pi stops running`() {
        val scheduler = ManualScheduler()
        newSession(scheduler = scheduler, clock = { clockMillis }).use { session ->
            session.launch()
            assertEquals(PiSessionStatus.RUNNING, session.status)

            terminals.lastTerminal!!.commandRunning = false
            clockMillis += 10_000 // past the startup grace
            repeat(2) { scheduler.repeating!!.invoke() }

            assertFalse(session.isRunning(), "an exited Pi must not stay 'Running'")
        }
    }

    @Test
    fun `a single empty observation is not enough to call it exited`() {
        val scheduler = ManualScheduler()
        newSession(scheduler = scheduler, clock = { clockMillis }).use { session ->
            session.launch()
            clockMillis += 10_000

            terminals.lastTerminal!!.commandRunning = false
            scheduler.repeating!!.invoke()
            assertTrue(session.isRunning(), "one gap must not end the session")

            terminals.lastTerminal!!.commandRunning = true
            scheduler.repeating!!.invoke()
            assertTrue(session.isRunning())
        }
    }

    @Test
    fun `the startup grace protects against the shell having no child yet`() {
        val scheduler = ManualScheduler()
        newSession(scheduler = scheduler, clock = { clockMillis }).use { session ->
            session.launch()

            // Right after launch the shell has not spawned Pi yet.
            terminals.lastTerminal!!.commandRunning = false
            repeat(5) { scheduler.repeating!!.invoke() }

            assertTrue(session.isRunning(), "Pi must not be reported exited before it could start")
        }
    }

    @Test
    fun `an unknown running state keeps the previous status`() {
        val scheduler = ManualScheduler()
        newSession(scheduler = scheduler, clock = { clockMillis }).use { session ->
            session.launch()
            clockMillis += 10_000

            terminals.lastTerminal!!.commandRunning = null
            repeat(3) { scheduler.repeating!!.invoke() }

            assertTrue(session.isRunning(), "an unknown answer must not be treated as an exit")
        }
    }

    @Test
    fun `a running command keeps the session alive`() {
        val scheduler = ManualScheduler()
        newSession(scheduler = scheduler, clock = { clockMillis }).use { session ->
            session.launch()
            clockMillis += 10_000

            terminals.lastTerminal!!.commandRunning = true
            repeat(3) { scheduler.repeating!!.invoke() }

            assertTrue(session.isRunning())
        }
    }

    @Test
    fun `the exit poll stops after reset`() {
        val scheduler = ManualScheduler()
        newSession(scheduler = scheduler).use { session ->
            session.launch()
            session.reset()

            // Even if the platform reports the command is gone, a reset session
            // must not publish another transition.
            terminals.lastTerminal!!.commandRunning = false
            scheduler.repeating?.invoke()

            assertEquals(listOf(PiSessionStatus.RUNNING, PiSessionStatus.IDLE), statuses)
        }
    }

    // ---- fakes -------------------------------------------------------------

    private object NoopDisposable : Disposable {
        override fun dispose() = Unit
    }

    private class FakeTerminalProvider : PiTerminalProvider {
        val created = mutableListOf<FakeTerminal>()
        var createdWorkingDirectory: String? = null
        var createdTabName: String? = null
        var focusCount = 0
        var failOnCreate = false

        val lastTerminal: FakeTerminal? get() = created.lastOrNull()

        override fun createTerminalTab(workingDirectory: String, tabName: String): PiTerminal {
            if (failOnCreate) error("terminal unavailable")
            createdWorkingDirectory = workingDirectory
            createdTabName = tabName
            return FakeTerminal().also { created += it }
        }

        override fun focusTerminalTab(tabName: String) {
            focusCount++
        }

        override fun currentTerminal(): PiTerminal? = created.lastOrNull()
    }

    private class FakeTerminal : PiTerminal {
        data class Sent(val text: String, val submitted: Boolean)

        val sent = mutableListOf<Sent>()
        var attached = true
        var commandRunning: Boolean? = true

        override val component: JComponent = JPanel()

        override fun isForegroundCommandRunning(): Boolean? = commandRunning

        override fun send(text: String, submit: Boolean) {
            sent += Sent(text, submit)
        }

        override fun isAttached(): Boolean = attached
    }

    private class FakeNotifier : PiNotifier {
        val errors = mutableListOf<String>()

        override fun error(message: String) {
            errors += message
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
