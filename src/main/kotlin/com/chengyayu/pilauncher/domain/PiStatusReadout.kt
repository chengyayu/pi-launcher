package com.chengyayu.pilauncher.domain

/**
 * One Pi Session reduced to what the status bar and the Session Dashboard show.
 *
 * [status] is null when Pi has reported nothing; that is not `idle`.
 */
data class PiSessionReadout(
    val tabName: String,
    val status: PiStatus?,
    val active: Boolean
)

/** Renders [PiSessionReadout]s so every surface words a state the same way. */
object PiStatusReadout {

    private const val IDLE_TEXT = "π all-idle"

    /** Shown for a session Pi has not reported on; never rendered as a state. */
    const val NO_STATUS = "no status from Pi"

    /**
     * The states that are present, in the order that matters: what needs the user, then
     * what is happening, then what is over.
     */
    fun text(readouts: List<PiSessionReadout>): String {
        if (readouts.isEmpty()) return IDLE_TEXT

        val states = PiStatus.State.entries
            .filter { state -> readouts.any { it.status?.state == state } }
            .sortedBy { ORDER.indexOf(it) }

        val parts = states.map { state -> "${readouts.count { it.status?.state == state }} ${label(state)}" }
            .toMutableList()
        readouts.count { it.status == null }.takeIf { it > 0 }?.let { parts += "$it unknown" }

        return "π " + parts.joinToString(" · ")
    }

    /** As few words as possible: a state alone, unless Pi's own words are the point. */
    fun tooltip(readouts: List<PiSessionReadout>): String {
        if (readouts.isEmpty()) return "No Pi sessions."

        return readouts.joinToString(" · ", transform = ::shortLine)
    }

    /** The tab name, marked when this is the Active Session. */
    fun sessionLabel(readout: PiSessionReadout): String =
        if (readout.active) "• ${readout.tabName}" else readout.tabName

    fun stateLabel(readout: PiSessionReadout): String =
        readout.status?.let { label(it.state) } ?: NO_STATUS

    /** Pi's own words when it gave any, otherwise what it is doing. */
    fun detail(readout: PiSessionReadout): String? =
        readout.status?.let { it.message ?: it.activity }

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

    private val ORDER = listOf(
        PiStatus.State.BLOCKED,
        PiStatus.State.ERROR,
        PiStatus.State.WORKING,
        PiStatus.State.DONE,
        PiStatus.State.IDLE
    )
}
