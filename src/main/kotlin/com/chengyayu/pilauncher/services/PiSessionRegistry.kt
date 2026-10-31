package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiStatusChannelProvider
import com.chengyayu.pilauncher.infrastructure.PiSessionHost
import com.chengyayu.pilauncher.util.PiScheduler
import java.util.UUID

/**
 * The Pi Sessions of a project: the running ones and the Active Session.
 *
 * A Pi Session lives exactly as long as its run: when the `pi` process exits or its Host
 * closes, the session is gone from here (`docs/adr/0009`).
 */
internal class PiSessionRegistry(
    private val workingDirectory: () -> String,
    private val startCommand: (extensionPath: String?) -> String,
    private val host: PiSessionHost,
    private val notifier: PiNotifier,
    private val publishCount: (Int) -> Unit,
    private val publishReadouts: (List<PiSessionReadout>) -> Unit = {},
    private val statusChannels: PiStatusChannelProvider = PiStatusChannelProvider.NONE,
    private val commandDispatcher: PiCommandDispatcher = PiCommandDispatcher(),
    private val scheduler: PiScheduler = PiScheduler.swing,
    private val clock: () -> Long = System::currentTimeMillis
) : Disposable {

    private val logger = Logger.getInstance(PiSessionRegistry::class.java)

    /** Creation-ordered, so "most recent" is the last entry. */
    private val sessions = LinkedHashMap<String, PiSession>()

    @Volatile
    var active: PiSession? = null
        private set

    val runningCount: Int get() = sessions.size

    /** Every session the dashboard shows: one per run. */
    val readouts: List<PiSessionReadout> get() = snapshot()

    init {
        host.onTabSelected(::selectByName)
        host.onTabClosed(::onTabClosed)
        host.onTabRenamed(::onTabRenamed)
    }

    fun isRunning(): Boolean = sessions.isNotEmpty()

    /** Focus the Active Session, or start one when nothing is running. */
    fun launch() {
        val session = active
        if (session == null) newSession() else focusSession(session)
    }

    fun newSession() {
        startRun(tabName = nextTabName()) { extensionPath ->
            startCommand(extensionPath)
        }
    }

    fun submitText(text: String) = active?.submitText(text)

    fun insertText(text: String) = active?.insertText(text)

    fun focus() = active?.let(::focusSession)

    /** Bring the Host of [tabName] to the front, if that session is still running. */
    fun focus(tabName: String) = sessionNamed(tabName)?.let(::focusSession)

    fun selectByName(tabName: String) {
        sessionNamed(tabName)?.let {
            active = it
            publishState()
        }
    }

    private fun sessionNamed(tabName: String): PiSession? =
        sessions.values.firstOrNull { it.tabName == tabName }

    /** Immutable per run, which is what a map key has to be: the id arrives later, the name can change. */
    private fun keyOf(session: PiSession): String = session.channelId

    /** Null when no Host could be opened; the caller decides what that means for its identity. */
    private fun startRun(tabName: String, command: (extensionPath: String?) -> String): PiSession? {
        // Unique per run: a closed tab's directory is not reused by the next one.
        val channelId = "$tabName-${UUID.randomUUID().toString().take(8)}"
        val channel = statusChannels.open(channelId)
        val terminal = try {
            host.open(workingDirectory(), tabName)
        } catch (e: Exception) {
            // The user is notified, so this is not an IDE internal error.
            logger.warn("Failed to launch Pi in $tabName", e)
            notifier.error("Failed to start Pi: ${e.message}")
            return null
        }

        val session = PiSession(
            tabName = tabName,
            id = null,
            channelId = channelId,
            terminal = terminal,
            startCommand = command(channel?.extensionPath),
            statusSource = channel,
            onExited = ::drop,
            onStatusChanged = { publishState() },
            commandDispatcher = commandDispatcher,
            scheduler = scheduler,
            clock = clock
        )

        // A drop is prompt input, so it queues behind the start command like any other input (`docs/adr/0008`).
        terminal.onFilesDropped { paths -> session.insertFiles(workingDirectory(), paths) }

        sessions[keyOf(session)] = session
        active = session
        publishState()
        session.start()
        logger.info("Pi session started in $tabName")
        return session
    }

    private fun snapshot(): List<PiSessionReadout> =
        sessions.values.map {
            PiSessionReadout(it.tabName, it.status, it === active)
        }

    /** Published together so the two listeners cannot describe different sets. */
    private fun publishState() {
        publishCount(sessions.size)
        publishReadouts(snapshot())
    }

    private fun focusSession(session: PiSession) {
        active = session
        publishState()
        host.focus(session.tabName)
    }

    /** Only the label changes: the map is keyed by identity, so the creation order is preserved. */
    private fun onTabRenamed(oldName: String, newName: String) {
        if (oldName == newName) return
        val session = sessionNamed(oldName) ?: return

        logger.info("Tab $oldName was renamed to $newName")
        session.tabName = newName
        publishState()
    }

    private fun onTabClosed(tabName: String) {
        val session = sessionNamed(tabName) ?: return
        logger.info("Tab $tabName was closed")
        drop(session)
    }

    /** The run is over, so the session is too: what Pi stores is Pi's, nothing here outlives it. */
    private fun drop(session: PiSession) {
        if (!remove(session)) return

        if (active === session) active = sessions.values.lastOrNull()

        publishState()
        statusChannels.close(session.channelId)
        session.dispose()
    }

    /** By identity, not by current label: a renamed Host keeps the key it was filed under. */
    private fun remove(session: PiSession): Boolean {
        val key = sessions.entries.firstOrNull { it.value === session }?.key ?: return false
        sessions.remove(key)
        return true
    }

    /** Counts tabs still open in the Terminal window too: duplicate display names would make focusing unreliable. */
    private fun nextTabName(): String {
        val taken = sessions.values.map { it.tabName }.toSet() + host.shownNames()
        return generateSequence(1) { it + 1 }
            .map { "$BASE_TAB_NAME $it" }
            .first { it !in taken }
    }

    override fun dispose() {
        sessions.values.forEach {
            statusChannels.close(it.channelId)
            it.dispose()
        }
        sessions.clear()
        active = null
    }

    companion object {
        const val BASE_TAB_NAME = "Pi"
    }
}
