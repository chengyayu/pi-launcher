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
 * The Pi Sessions of a project: those with a Host, those that used to have one, and the
 * Active Session pointer.
 *
 * A session is live only while its `pi` runs: an exit or a closed Host ends the run and
 * the pointer rolls over to the most recent remaining session. The conversation is not
 * ended with it - a session with an identity is parked, not forgotten, and can be
 * reopened on Pi's own session file (`docs/adr/0006`).
 *
 * Sessions without an identity (this Pi cannot be told one) behave exactly as before:
 * closing the Host forgets them.
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

    /** Creation-ordered, so "most recent" is the last entry. Keyed by identity, tab name when there is none. */
    private val sessions = LinkedHashMap<String, PiSession>()

    /** Sessions with no Host, most recently closed last. */
    private val parked = LinkedHashMap<String, PiSessionRecord>()

    @Volatile
    var active: PiSession? = null
        private set

    val runningCount: Int get() = sessions.size

    /** Every session the dashboard shows: the live ones, then the parked ones. */
    val readouts: List<PiSessionReadout> get() = snapshot()

    init {
        // The state file is data from outside the plugin's control, and this id ends up in
        // a `pi --session <id>` command line: only what Pi itself would accept survives.
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

    /** Start a session in a fresh Host and make it active. */
    fun newSession() {
        startRun(tabName = nextTabName(), id = null) { extensionPath ->
            startCommand(extensionPath)
        }
    }

    /**
     * Reopen [id] in a new Host, on the conversation Pi still has on disk.
     *
     * Whatever the run was doing when its Host closed is gone: this continues from the
     * last thing Pi persisted, it does not reattach to a process.
     */
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

        // Now that it is running, it is no longer parked - the session is both at once
        // until this line, so the dashboard is told again rather than left with a duplicate.
        parked.remove(id)
        persist()
        publishState()
    }

    fun submitText(text: String) = active?.submitText(text)

    fun insertText(text: String) = active?.insertText(text)

    fun focus() = active?.let(::focusSession)

    /** Bring the Host of [tabName] to the front, if that session is still running. */
    fun focus(tabName: String) = sessionNamed(tabName)?.let(::focusSession)

    /** Rename a parked session. A live one is renamed through its Host, which is where its name is shown. */
    fun renameParked(id: String, name: String) {
        val record = parked[id] ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == record.displayName) return

        parked[id] = record.copy(displayName = trimmed)
        persist()
        publishState()
    }

    /** A live session's Host was selected; exited sessions are not revived. */
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

    /**
     * Opens a Host and starts Pi in it: one run, on a new or an existing identity.
     *
     * Returns the session, or null when no Host could be opened - the caller decides what
     * that means for the identity it was reopening.
     */
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

    /**
     * The count and the readouts travel together: they describe the same set of
     * sessions, and publishing them from one place is what keeps the status bar's
     * text and its tooltip from ever describing different moments.
     */
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

    /**
     * The user renamed a Host, so the session follows the label they gave it.
     *
     * Only the label changes: the map is keyed by identity, so nothing moves and the
     * "most recent" order that decides the next Active Session is preserved.
     */
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

    /**
     * Removed by identity, not by the session's current label: a renamed Host keeps the
     * key it was filed under, and looking that key up by name would leave a dead
     * session in the registry forever.
     */
    private fun remove(session: PiSession): Boolean {
        val key = sessions.entries.firstOrNull { it.value === session }?.key ?: return false
        sessions.remove(key)
        return true
    }

    /**
     * Keeps the parked list to the most recent [MAX_PARKED].
     *
     * The conversations themselves are Pi's and stay where they are; this list is only
     * the plugin's shortlist of runs it can offer to reopen.
     */
    private fun trimParked() {
        while (parked.size > MAX_PARKED) {
            parked.remove(parked.keys.first())
        }
    }

    /**
     * The smallest free number, counting both live sessions and tabs still open
     * in the Terminal window: an exited session's tab keeps its name, and
     * duplicate display names would make focusing unreliable.
     */
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

    /**
     * Parked sessions survive the project closing: that is the whole point of parking
     * them. Only the runs are ended.
     */
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
