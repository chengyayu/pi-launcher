package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.domain.PiSessionId
import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiStatusChannelProvider
import com.chengyayu.pilauncher.infrastructure.PiSessionHost
import com.chengyayu.pilauncher.util.PiScheduler
import java.util.UUID

/**
 * The Pi Sessions of a project: the running ones, the parked ones, and the Active Session.
 *
 * A run ends with its `pi` process or its Host, but a session that has an identity is parked
 * rather than forgotten and can be reopened on Pi's own session file (`docs/adr/0006`).
 */
internal class PiSessionRegistry(
    private val workingDirectory: () -> String,
    private val startCommand: (extensionPath: String?) -> String,
    private val host: PiSessionHost,
    private val notifier: PiNotifier,
    private val publishCount: (Int) -> Unit,
    private val publishReadouts: (List<PiSessionReadout>) -> Unit = {},
    private val statusChannels: PiStatusChannelProvider = PiStatusChannelProvider.NONE,
    /** Reopens a parked session on the conversation Pi already stores. */
    private val resumeCommand: (id: String, extensionPath: String?) -> String = { id, _ -> "pi --session $id" },
    private val store: PiSessionStore = PiSessionStore.inMemory(),
    private val commandDispatcher: PiCommandDispatcher = PiCommandDispatcher(),
    private val scheduler: PiScheduler = PiScheduler.swing,
    private val clock: () -> Long = System::currentTimeMillis
) : Disposable {

    private val logger = Logger.getInstance(PiSessionRegistry::class.java)

    /** Creation-ordered, so "most recent" is the last entry. */
    private val sessions = LinkedHashMap<String, PiSession>()

    private val parked = LinkedHashMap<String, PiSessionRecord>()

    @Volatile
    var active: PiSession? = null
        private set

    val runningCount: Int get() = sessions.size

    /** Every session the dashboard shows. */
    val readouts: List<PiSessionReadout> get() = snapshot()

    init {
        // A hand-edited id must not reach a `pi --session <id>` command line.
        store.load()
            .filter { PiSessionId.isValid(it.id) }
            .sortedBy { it.closedAt }
            .forEach { parked[it.id] = it }
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
        startRun(tabName = nextTabName(), id = null) { extensionPath ->
            startCommand(extensionPath)
        }
    }

    /** Continues from the last thing Pi persisted; it does not reattach to the process that is gone. */
    fun resume(id: String) {
        sessions.values.firstOrNull { it.id == id }?.let {
            focusSession(it)
            return
        }

        // Look before taking: a Host can refuse to open, and the record must survive that.
        val record = parked[id] ?: return
        val tabName = if (record.displayName.isFree()) record.displayName else nextTabName()
        startRun(tabName = tabName, id = id) { extensionPath ->
            resumeCommand(id, extensionPath)
        } ?: return

        // Only now is it no longer parked, so the dashboard is not left with a duplicate.
        parked.remove(id)
        persist()
        publishState()
    }

    fun submitText(text: String) = active?.submitText(text)

    fun insertText(text: String) = active?.insertText(text)

    fun focus() = active?.let(::focusSession)

    /** Bring the Host of [tabName] to the front, if that session is still running. */
    fun focus(tabName: String) = sessionNamed(tabName)?.let(::focusSession)

    /** A live session is renamed through its Host, which is where its name is shown. */
    fun renameParked(id: String, name: String) {
        val record = parked[id] ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == record.displayName) return

        parked[id] = record.copy(displayName = trimmed)
        persist()
        publishState()
    }

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
    private fun startRun(tabName: String, id: String?, command: (extensionPath: String?) -> String): PiSession? {
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
            id = id,
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
        logger.info("Pi session $id started in $tabName")
        return session
    }

    private fun snapshot(): List<PiSessionReadout> =
        sessions.values.map {
            PiSessionReadout(it.tabName, it.status, it === active, running = true, id = it.id)
        } + parked.values.map {
            PiSessionReadout(it.displayName, status = null, active = false, running = false, id = it.id)
        }

    /** Published together so the two listeners cannot describe different sets. */
    private fun publishState() {
        publishCount(sessions.size)
        publishReadouts(snapshot())
    }

    private fun persist() {
        store.save(parked.values.toList())
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

    /** The run is over: park the conversation when it has an identity, then forget the run. */
    private fun drop(session: PiSession) {
        if (!remove(session)) return

        if (active === session) active = sessions.values.lastOrNull()

        session.id?.let { id ->
            parked[id] = PiSessionRecord(id, session.tabName, clock())
            trimParked()
            persist()
        }

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

    /** Keeps the plugin's shortlist only; the conversations themselves are Pi's and stay. */
    private fun trimParked() {
        while (parked.size > MAX_PARKED) {
            parked.remove(parked.keys.first())
        }
    }

    /** Counts tabs still open in the Terminal window too: duplicate display names would make focusing unreliable. */
    private fun nextTabName(): String {
        val taken = sessions.values.map { it.tabName }.toSet() +
            parked.values.map { it.displayName } +
            host.shownNames()
        return generateSequence(1) { it + 1 }
            .map { "$BASE_TAB_NAME $it" }
            .first { it !in taken }
    }

    private fun String.isFree(): Boolean =
        isNotBlank() && sessionNamed(this) == null && this !in host.shownNames()

    /** Only the runs end here: parked sessions are the point of parking them. */
    override fun dispose() {
        sessions.values.forEach {
            statusChannels.close(it.channelId)
            it.dispose()
        }
        sessions.clear()
        active = null
        persist()
    }

    companion object {
        const val BASE_TAB_NAME = "Pi"

        /** How many parked sessions the dashboard offers to reopen. */
        const val MAX_PARKED = 20
    }
}
