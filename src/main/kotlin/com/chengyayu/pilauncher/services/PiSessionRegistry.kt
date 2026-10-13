package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.infrastructure.PiNotifier
import com.chengyayu.pilauncher.infrastructure.PiStatusChannelProvider
import com.chengyayu.pilauncher.infrastructure.PiTerminalProvider
import com.chengyayu.pilauncher.util.PiScheduler
import java.util.UUID

/**
 * The running Pi Sessions of a project and the Active Session pointer.
 *
 * A session is live only while its `pi` runs: an exit or a closed tab removes it,
 * and the pointer rolls over to the most recent remaining session. The pointer
 * follows tab selection, because the Active Session is the one the user is
 * looking at.
 */
internal class PiSessionRegistry(
    private val workingDirectory: () -> String,
    private val startCommand: (extensionPath: String?) -> String,
    private val terminals: PiTerminalProvider,
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

    /** Every live session, as the status bar and the Session Dashboard render it. */
    val readouts: List<PiSessionReadout> get() = snapshot()

    init {
        terminals.onTabSelected(::selectByName)
        terminals.onTabClosed(::onTabClosed)
        terminals.onTabRenamed(::onTabRenamed)
    }

    fun isRunning(): Boolean = sessions.isNotEmpty()

    /** Focus the Active Session, or start one when nothing is running. */
    fun launch() {
        val session = active
        if (session == null) newSession() else focusSession(session)
    }

    /** Start a session in a fresh tab and make it active. */
    fun newSession() {
        val tabName = nextTabName()
        // Unique per session: a closed tab's directory is not reused by the next one.
        val sessionId = "$tabName-${UUID.randomUUID().toString().take(8)}"
        val channel = statusChannels.open(sessionId)
        val terminal = try {
            terminals.createTerminalTab(workingDirectory(), tabName)
        } catch (e: Exception) {
            // The user is notified, so this is not an IDE internal error.
            logger.warn("Failed to launch Pi in $tabName", e)
            notifier.error("Failed to start Pi: ${e.message}")
            return
        }

        val session = PiSession(
            tabName = tabName,
            sessionId = sessionId,
            terminal = terminal,
            startCommand = startCommand(channel?.extensionPath),
            statusSource = channel,
            onExited = ::drop,
            onStatusChanged = { publishState() },
            commandDispatcher = commandDispatcher,
            scheduler = scheduler,
            clock = clock
        )

        sessions[tabName] = session
        active = session
        publishState()
        session.start()
        logger.info("Pi session started in $tabName")
    }

    fun submitText(text: String) = active?.submitText(text)

    fun insertText(text: String) = active?.insertText(text)

    fun focus() = active?.let(::focusSession)

    /** Bring a specific session's tab to the front, if it is still running. */
    fun focus(tabName: String) {
        sessions[tabName]?.let(::focusSession)
    }

    /** A live session's tab was selected; exited sessions are not revived. */
    fun selectByName(tabName: String) {
        sessions[tabName]?.let {
            active = it
            publishState()
        }
    }

    private fun snapshot(): List<PiSessionReadout> =
        sessions.values.map { PiSessionReadout(it.tabName, it.status, it === active) }

    /**
     * The count and the readouts travel together: they describe the same set of
     * sessions, and publishing them from one place is what keeps the status bar's
     * text and its tooltip from ever describing different moments.
     */
    private fun publishState() {
        publishCount(sessions.size)
        publishReadouts(snapshot())
    }

    private fun focusSession(session: PiSession) {
        active = session
        publishState()
        terminals.focusTerminalTab(session.tabName)
    }

    /**
     * The user renamed a tab, so the session follows the label they gave it.
     *
     * The map is rebuilt rather than removed-and-reinserted: its order is "created
     * last, last", and that order decides which session becomes Active when the
     * Active one goes away, so a rename must not shuffle the sessions around it.
     */
    private fun onTabRenamed(oldName: String, newName: String) {
        val session = sessions[oldName] ?: return
        if (oldName == newName) return

        logger.info("Tab $oldName was renamed to $newName")
        session.tabName = newName

        val renamed = LinkedHashMap<String, PiSession>(sessions.size)
        sessions.forEach { (name, each) -> renamed[if (each === session) newName else name] = each }
        sessions.clear()
        sessions.putAll(renamed)
        publishState()
    }

    private fun onTabClosed(tabName: String) {
        val session = sessions[tabName] ?: return
        logger.info("Tab $tabName was closed")
        drop(session)
    }

    /** The session is over: forget it, roll the pointer and re-publish the count. */
    private fun drop(session: PiSession) {
        if (sessions.remove(session.tabName) == null) return

        if (active === session) active = sessions.values.lastOrNull()
        publishState()
        statusChannels.close(session.sessionId)
        session.dispose()
    }

    /**
     * The smallest free number, counting both live sessions and tabs still open
     * in the Terminal window: an exited session's tab keeps its name, and
     * duplicate display names would make focusing unreliable.
     */
    private fun nextTabName(): String {
        val taken = sessions.keys + terminals.existingTabNames()
        return generateSequence(1) { it + 1 }
            .map { "$BASE_TAB_NAME $it" }
            .first { it !in taken }
    }

    override fun dispose() {
        sessions.values.forEach {
            statusChannels.close(it.sessionId)
            it.dispose()
        }
        sessions.clear()
        active = null
    }

    companion object {
        const val BASE_TAB_NAME = "Pi"
    }
}
