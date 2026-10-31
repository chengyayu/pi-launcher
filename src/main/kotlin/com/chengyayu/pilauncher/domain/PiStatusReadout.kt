package com.chengyayu.pilauncher.domain

/**
 * One Pi Session reduced to what a reader of the status bar or the Session Dashboard
 * needs.
 *
 * [status] is null when Pi has not reported yet or has no Status Source at all;
 * that is not the same as idle and must not be rendered as if it were.
 *
 * [running] is false for a session that has no Host: the conversation still exists in
 * Pi's session file, but nothing is executing, so it has no Session Status and cannot
 * be guessed at (`docs/adr/0006`).
 */
data class PiSessionReadout(
    val tabName: String,
    val status: PiStatus?,
    val active: Boolean,
    val running: Boolean = true,
    val id: String? = null
)

/**
 * Renders [PiSessionReadout]s for every surface that shows them.
 *
 * Pure string handling, so the wording is unit testable, and so the status bar, its
 * tooltip and the dashboard cannot drift apart on what a state is called.
 */
object PiStatusReadout {

    private const val IDLE_TEXT = "π all-idle"

    /** Shown for a session Pi has not reported on; never rendered as a state. */
    const val NO_STATUS = "no status from Pi"

    /**
     * Shown for a Pi Session with no Host. Deliberately none of Pi's own words: Pi is
     * not running, so it has said nothing about this state.
     */
    const val NOT_RUNNING = "not running"

    /**
     * `π all-idle`, or the non-empty states in the order that matters to a user:
     * what needs them first, then what is happening, then what is over.
     *
     * Only running sessions count: a session with no Host has no state to report, and
     * counting it as `unknown` would say something the plugin does not know.
     */
    fun text(readouts: List<PiSessionReadout>): String {
        val running = readouts.filter { it.running }
        if (running.isEmpty()) return IDLE_TEXT

        val states = PiStatus.State.entries
            .filter { state -> running.any { it.status?.state == state } }
            .sortedBy { ORDER.indexOf(it) }

        val parts = states.map { state -> "${running.count { it.status?.state == state }} ${label(state)}" }
            .toMutableList()
        running.count { it.status == null }.takeIf { it > 0 }?.let { parts += "$it unknown" }

        return "π " + parts.joinToString(" · ")
    }

    /**
     * The hover text: as few words as possible.
     *
     * It is meant to answer "is anything waiting for me?" without a trip to the
     * dashboard, so a session is named by its state alone - except where Pi's own
     * words are the point, which is exactly the two states that need the user.
     */
    fun tooltip(readouts: List<PiSessionReadout>): String {
        if (readouts.isEmpty()) return "No Pi sessions."

        val running = readouts.filter { it.running }
        if (running.isEmpty()) return NO_RUNNING_TEXT

        return running.joinToString(" · ", transform = ::shortLine)
    }

    /** The tab name, marked when this is the Active Session. */
    fun sessionLabel(readout: PiSessionReadout): String =
        if (readout.active) "• ${readout.tabName}" else readout.tabName

    /** This session's state, or [NO_STATUS] when Pi has not said, or [NOT_RUNNING] without a Host. */
    fun stateLabel(readout: PiSessionReadout): String = when {
        !readout.running -> NOT_RUNNING
        else -> readout.status?.let { label(it.state) } ?: NO_STATUS
    }

    /**
     * What there is to say beyond the state: Pi's own explanation when it gave one
     * (a dialog title, an error), otherwise what it is doing right now.
     *
     * A session with no Host has nothing to say: whatever it last reported belongs to
     * the run that is over.
     */
    fun detail(readout: PiSessionReadout): String? =
        if (!readout.running) null else readout.status?.let { it.message ?: it.activity }

    /** `Pi 1: blocked — Allow tool call?`, or just `Pi 2: working`. */
    private fun shortLine(readout: PiSessionReadout): String {
        val head = "${sessionLabel(readout)}: ${stateLabel(readout)}"
        val detail = detail(readout).takeIf { readout.status?.state in NEEDS_ATTENTION }
        return detail?.let { "$head — $it" } ?: head
    }

    private fun label(state: PiStatus.State): String = when (state) {
        PiStatus.State.WORKING -> "working"
        PiStatus.State.BLOCKED -> "blocked"
        PiStatus.State.DONE -> "done"
        PiStatus.State.ERROR -> "error"
        PiStatus.State.IDLE -> "idle"
    }

    /** The states whose detail belongs in a tooltip: both ask something of the user. */
    private val NEEDS_ATTENTION = setOf(PiStatus.State.BLOCKED, PiStatus.State.ERROR)

    private const val NO_RUNNING_TEXT = "No Pi sessions running."

    private val ORDER = listOf(
        PiStatus.State.BLOCKED,
        PiStatus.State.ERROR,
        PiStatus.State.WORKING,
        PiStatus.State.DONE,
        PiStatus.State.IDLE
    )
}
