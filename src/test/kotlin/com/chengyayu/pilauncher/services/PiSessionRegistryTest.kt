package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.chengyayu.pilauncher.domain.PiSessionReport
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiStatusChannel
import com.chengyayu.pilauncher.infrastructure.PiStatusChannelProvider
import com.chengyayu.pilauncher.infrastructure.PiTerminal
import com.chengyayu.pilauncher.infrastructure.PiSessionHost
import com.chengyayu.pilauncher.util.PiScheduler
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Launch semantics, tab naming, the Active Session pointer and exit rollover.
 * Single-session lifecycle (start command, exit polling) is PiSessionTest's job.
 */
class PiSessionRegistryTest {

    private val provider = FakeSessionHost()
    private val notifier = FakeNotifier()
    private val counts = CopyOnWriteArrayList<Int>()

    private val ID = "6f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f"

    /** Advances 10s per read, so every poll is past the startup grace. */
    private var tick = 0L

    private val polls = mutableListOf<() -> Unit>()

    private val startCommands = CopyOnWriteArrayList<String?>()

    private val resumeCommands = CopyOnWriteArrayList<String>()

    private val pollScheduler = PiScheduler { _, repeat, action ->
        if (repeat) polls += action else action()
        NoopDisposable
    }

    /** One call = one observation per session; two in a row register an exit. */
    private fun runPolls() = polls.forEach { it() }

    /** A Pi that reports [identity] for every session, the way the status extension does. */
    private fun channels(identity: String?): PiStatusChannelProvider = object : PiStatusChannelProvider {
        override fun open(sessionId: String): PiStatusChannel = object : PiStatusChannel {
            override val extensionPath: String = "/tmp/status.ts"
            override fun read(): PiSessionReport = PiSessionReport(status = null, identity = identity)
        }

        override fun close(sessionId: String) = Unit
    }

    private fun newRegistry(
        statusChannels: PiStatusChannelProvider = PiStatusChannelProvider.NONE,
        store: PiSessionStore = PiSessionStore.inMemory()
    ) = PiSessionRegistry(
        workingDirectory = { "/work" },
        startCommand = { extensionPath ->
            startCommands += extensionPath
            "pi --no-themes"
        },
        resumeCommand = { id, _ ->
            resumeCommands += id
            "pi --session $id"
        },
        store = store,
        host = provider,
        notifier = notifier,
        publishCount = { counts += it },
        statusChannels = statusChannels,
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
    fun `renaming a tab renames its session`() {
        newRegistry().use { registry ->
            registry.launch()
            provider.rename("Pi 1", "A股")

            assertEquals("A股", registry.readouts.single().tabName)
        }
    }

    @Test
    fun `a renamed session keeps its place and still becomes active`() {
        newRegistry().use { registry ->
            registry.launch()
            registry.newSession()
            provider.rename("Pi 1", "A股")
            provider.rename("Pi 2", "B股")

            assertEquals(listOf("A股", "B股"), registry.readouts.map { it.tabName }, "order must survive a rename")

            provider.select("A股")
            assertEquals("A股", registry.active?.tabName)
        }
    }

    @Test
    fun `a renamed session is still the one its tab closes`() {
        newRegistry().use { registry ->
            registry.launch()
            provider.rename("Pi 1", "A股")
            provider.close("A股")

            assertTrue(registry.readouts.isEmpty(), "closing the renamed tab must drop its session")
        }
    }

    @Test
    fun `renaming an unknown or unchanged tab changes nothing`() {
        newRegistry().use { registry ->
            registry.launch()
            provider.rename("Pi 9", "nope")
            provider.rename("Pi 1", "Pi 1")

            assertEquals(listOf("Pi 1"), registry.readouts.map { it.tabName })
        }
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
        provider.otherNamesOnScreen = setOf("Pi 1", "Local")

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

    // ---- identity and parked sessions (docs/adr/0006) ----------------------

    @Test
    fun `the identity Pi reports becomes the session's`() {
        newRegistry(statusChannels = channels(ID)).use { registry ->
            registry.launch()
            runPolls()

            assertEquals(ID, registry.readouts.single().id)
        }
    }

    @Test
    fun `a Pi that cannot report an identity starts an unidentified session`() {
        newRegistry(statusChannels = channels(null)).use { registry ->
            registry.launch()
            runPolls()

            assertNull(registry.readouts.single().id)
        }
    }

    @Test
    fun `closing a Host parks the conversation instead of forgetting it`() {
        newRegistry(statusChannels = channels(ID)).use { registry ->
            registry.launch()
            runPolls()
            provider.close("Pi 1")

            assertEquals(0, registry.runningCount, "the run is over")
            val parked = registry.readouts.single()
            assertFalse(parked.running, "the session is still there, without a Host")
            assertEquals("Pi 1", parked.tabName)
            assertEquals(ID, parked.id)
        }
    }

    @Test
    fun `a parked session is reopened on the conversation Pi stores`() {
        val store = PiSessionStore.inMemory()
        newRegistry(statusChannels = channels(ID), store = store).use { registry ->
            registry.launch()
            runPolls()
            provider.close("Pi 1")

            registry.resume(ID)

            assertEquals(1, registry.runningCount)
            assertEquals(ID, resumeCommands.single())
            assertEquals(
                "pi --session $ID",
                provider.created.last().terminal.sent.single().text,
                "the resumed run continues the conversation, it does not start a new one"
            )
            assertTrue(registry.readouts.none { !it.running }, "the session is live again")
        }
    }

    /**
     * Reopening needs a Host, and a Host can refuse. The conversation is Pi's and is
     * still on disk, so a failure here may not cost the plugin's record of it.
     */
    @Test
    fun `a Host that refuses to open leaves the session to be reopened again`() {
        newRegistry(statusChannels = channels(ID)).use { registry ->
            registry.launch()
            runPolls()
            provider.close("Pi 1")

            provider.failOnCreate = true
            registry.resume(ID)

            val stillParked = registry.readouts.single()
            assertFalse(stillParked.running, "nothing opened, so nothing is running")
            assertEquals(ID, stillParked.id)

            provider.failOnCreate = false
            registry.resume(ID)

            assertTrue(registry.readouts.single().running, "the second attempt must work")
        }
    }

    @Test
    fun `resuming a running session only focuses it`() {
        newRegistry(statusChannels = channels(ID)).use { registry ->
            registry.launch()
            runPolls()

            registry.resume(ID)

            assertEquals(1, provider.created.size, "a live session is not reopened")
            assertEquals(1, provider.focusCount)
        }
    }

    @Test
    fun `parked sessions are remembered across registries`() {
        val store = PiSessionStore.inMemory()
        newRegistry(statusChannels = channels(ID), store = store).use { first ->
            first.launch()
            runPolls()
            provider.close("Pi 1")
        }

        newRegistry(statusChannels = channels(null), store = store).use { second ->
            assertEquals(listOf("Pi 1"), second.readouts.map { it.tabName })
            assertFalse(second.readouts.single().running)
        }
    }

    @Test
    fun `renaming a parked session changes only its label`() {
        newRegistry(statusChannels = channels(ID)).use { registry ->
            registry.launch()
            runPolls()
            provider.close("Pi 1")

            registry.renameParked(ID, "api work")

            val parked = registry.readouts.single()
            assertEquals("api work", parked.tabName)
            assertEquals(ID, parked.id, "the identity is not the label")
        }
    }

    @Test
    fun `an id this plugin did not create is never handed to Pi`() {
        newRegistry(statusChannels = channels(ID)).use { registry ->
            registry.launch()
            runPolls()
            provider.close("Pi 1")

            registry.resume("--session-id evil; rm -rf /")

            assertEquals(1, provider.created.size, "no Host may be opened for a foreign id")
            assertTrue(resumeCommands.isEmpty())
        }
    }

    @Test
    fun `files dropped on a tab are typed into its prompt`() {
        newRegistry().use { registry ->
            registry.launch()

            provider.created.single().terminal.drop(listOf("/work/lib/a.go", "/tmp/shot.png"))

            val typed = provider.created.single().terminal.sent.last()
            assertEquals("@lib/a.go @/tmp/shot.png ", typed.text)
            assertFalse(typed.submitted, "a drop must not submit the prompt")
        }
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

    private class FakeSessionHost : PiSessionHost {
        val created = mutableListOf<CreatedTab>()
        val closed = mutableListOf<String>()
        var createdWorkingDirectory: String? = null
        var focusCount = 0
        var failOnCreate = false
        var otherNamesOnScreen: Set<String> = emptySet()

        private var selectionListener: ((String) -> Unit)? = null
        private var closeListener: ((String) -> Unit)? = null
        private var renameListener: ((String, String) -> Unit)? = null

        override fun open(workingDirectory: String, name: String): PiTerminal {
            if (failOnCreate) error("terminal unavailable")
            createdWorkingDirectory = workingDirectory
            val terminal = FakeTerminal()
            created += CreatedTab(name, terminal)
            return terminal
        }

        override fun focus(name: String) {
            focusCount++
        }

        override fun shownNames(): Set<String> =
            otherNamesOnScreen + created.map { it.tabName }.toSet() - closed.toSet()

        override fun onTabSelected(listener: (String) -> Unit) {
            selectionListener = listener
        }

        override fun onTabClosed(listener: (String) -> Unit) {
            closeListener = listener
        }

        override fun onTabRenamed(listener: (String, String) -> Unit) {
            renameListener = listener
        }

        /** Simulates the user renaming a Terminal tab. */
        fun rename(oldName: String, newName: String) {
            renameListener?.invoke(oldName, newName)
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
