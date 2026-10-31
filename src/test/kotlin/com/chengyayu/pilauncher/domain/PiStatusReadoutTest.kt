package com.chengyayu.pilauncher.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the status bar says. A session without a Session Status must read as unknown; calling it
 * idle is the lie the status extension exists to prevent (`docs/adr/0005`).
 */
class PiStatusReadoutTest {

    @Test
    fun `says all-idle when nothing is running`() {
        assertEquals("π all-idle", PiStatusReadout.text(emptyList()))
        assertEquals("No Pi sessions.", PiStatusReadout.tooltip(emptyList()))
    }

    @Test
    fun `counts sessions by the state Pi reported`() {
        val sessions = listOf(
            readout("Pi 1", PiStatus.State.WORKING),
            readout("Pi 2", PiStatus.State.WORKING),
            readout("Pi 3", PiStatus.State.IDLE)
        )

        assertEquals("π 2 working · 1 idle", PiStatusReadout.text(sessions))
    }

    /** Blocked is the state that needs the user, so it is named first. */
    @Test
    fun `puts blocked in front of everything else`() {
        val sessions = listOf(
            readout("Pi 1", PiStatus.State.IDLE),
            readout("Pi 2", PiStatus.State.WORKING),
            readout("Pi 3", PiStatus.State.BLOCKED)
        )

        assertEquals("π 1 blocked · 1 working · 1 idle", PiStatusReadout.text(sessions))
    }

    @Test
    fun `counts a session without a report as unknown, not idle`() {
        val sessions = listOf(
            readout("Pi 1", null),
            readout("Pi 2", PiStatus.State.IDLE)
        )

        assertEquals("π 1 idle · 1 unknown", PiStatusReadout.text(sessions))
    }

    @Test
    fun `names the error state`() {
        val sessions = listOf(readout("Pi 1", PiStatus.State.ERROR))

        assertEquals("π 1 error", PiStatusReadout.text(sessions))
    }

    @Test
    fun `lists every session in the tooltip, tersely`() {
        val sessions = listOf(
            PiSessionReadout("Pi 1", PiStatus(PiStatus.State.BLOCKED, PiStatus.Kind.CONFIRM, "Approve bash?"), true),
            PiSessionReadout("Pi 2", PiStatus(PiStatus.State.WORKING, message = "Pi 2"), false),
            readout("Pi 3", null)
        )

        val tooltip = PiStatusReadout.tooltip(sessions)

        assertTrue(tooltip.contains("• Pi 1: blocked — Approve bash?"), tooltip)
        assertTrue(tooltip.contains("Pi 2: working"), tooltip)
        assertFalse(
            tooltip.contains("Pi 2: working — Pi 2"),
            "a working session's own message stays in the dashboard: $tooltip"
        )
        assertTrue(tooltip.contains("Pi 3: no status from Pi"), tooltip)
    }

    @Test
    fun `names a session and its state for the dashboard`() {
        val active = PiSessionReadout("Pi 1", PiStatus(PiStatus.State.WORKING), active = true)
        val idle = PiSessionReadout("Pi 2", PiStatus(PiStatus.State.IDLE), active = false)

        assertEquals("• Pi 1", PiStatusReadout.sessionLabel(active))
        assertEquals("Pi 2", PiStatusReadout.sessionLabel(idle))
        assertEquals("working", PiStatusReadout.stateLabel(active))
        assertEquals("• Pi 1: working", PiStatusReadout.tooltip(listOf(active)))
    }

    @Test
    fun `names a missing report instead of inventing a state`() {
        val unknown = readout("Pi 3", null)

        assertEquals(PiStatusReadout.NO_STATUS, PiStatusReadout.stateLabel(unknown))
        assertEquals("Pi 3: ${PiStatusReadout.NO_STATUS}", PiStatusReadout.tooltip(listOf(unknown)))
        assertNull(PiStatusReadout.detail(unknown))
    }

    @Test
    fun `shows what Pi is doing when there is nothing to explain`() {
        val working = PiSessionReadout(
            "Pi 1",
            PiStatus(PiStatus.State.WORKING, activity = "bash: go build ./..."),
            active = true
        )

        assertEquals("bash: go build ./...", PiStatusReadout.detail(working))
    }

    /** Hover text stays terse: the tool call itself belongs in the dashboard. */
    @Test
    fun `leaves the activity out of the tooltip`() {
        val working = PiSessionReadout(
            "Pi 1",
            PiStatus(PiStatus.State.WORKING, activity = "bash: go build ./..."),
            active = true
        )

        assertEquals("• Pi 1: working", PiStatusReadout.tooltip(listOf(working)))
    }

    @Test
    fun `keeps the detail in the tooltip for the states that need the user`() {
        val blocked = PiSessionReadout(
            "Pi 1",
            PiStatus(PiStatus.State.BLOCKED, PiStatus.Kind.CONFIRM, message = "Allow tool call?"),
            active = true
        )
        val failed = PiSessionReadout(
            "Pi 2",
            PiStatus(PiStatus.State.ERROR, message = "Rate limit exceeded"),
            active = false
        )

        assertEquals(
            "• Pi 1: blocked — Allow tool call? · Pi 2: error — Rate limit exceeded",
            PiStatusReadout.tooltip(listOf(blocked, failed))
        )
    }

    @Test
    fun `an explanation outranks the activity, because it is what needs the user`() {
        val blocked = PiSessionReadout(
            "Pi 1",
            PiStatus(
                PiStatus.State.BLOCKED,
                PiStatus.Kind.CONFIRM,
                message = "Allow tool call?",
                activity = "bash: rm -rf build"
            ),
            active = false
        )

        assertEquals("Allow tool call?", PiStatusReadout.detail(blocked))
    }

    @Test
    fun `has nothing to add for a session that is simply idle`() {
        val idle = readout("Pi 1", PiStatus.State.IDLE)

        assertNull(PiStatusReadout.detail(idle))
        assertEquals("Pi 1: idle", PiStatusReadout.tooltip(listOf(idle)))
    }

    @Test
    fun `carries Pi's own explanation through to the dashboard`() {
        val blocked = PiSessionReadout(
            "Pi 1",
            PiStatus(PiStatus.State.BLOCKED, PiStatus.Kind.CONFIRM, "Allow tool call?"),
            active = true
        )

        assertEquals("Allow tool call?", PiStatusReadout.detail(blocked))
        assertEquals("• Pi 1: blocked — Allow tool call?", PiStatusReadout.tooltip(listOf(blocked)))
    }

    /**
     * A Pi Session with no Host is none of the three things above: Pi is not running, so
     * it has no state, and whatever it reported belongs to the run that is over
     * (`docs/adr/0006`).
     */
    @Test
    fun `a session with no Host reads as not running`() {
        val parked = PiSessionReadout(
            "Pi 1",
            PiStatus(PiStatus.State.WORKING),
            active = false,
            running = false,
            id = "pilaunch-0a1b2c3d"
        )

        assertEquals(PiStatusReadout.NOT_RUNNING, PiStatusReadout.stateLabel(parked))
        assertNull(PiStatusReadout.detail(parked), "the last run's words are stale")
    }

    @Test
    fun `the status bar describes the runs, not the conversations`() {
        val running = readout("Pi 1", PiStatus.State.WORKING)
        val parked = PiSessionReadout("Pi 2", status = null, active = false, running = false)

        assertEquals("π 1 working", PiStatusReadout.text(listOf(running, parked)))
        assertEquals("Pi 1: working", PiStatusReadout.tooltip(listOf(running, parked)))
    }

    @Test
    fun `says all-idle when everything that is left has no Host`() {
        val parked = PiSessionReadout("Pi 1", status = null, active = false, running = false)

        assertEquals("π all-idle", PiStatusReadout.text(listOf(parked)))
        assertEquals("No Pi sessions running.", PiStatusReadout.tooltip(listOf(parked)))
    }

    private fun readout(tabName: String, state: PiStatus.State?) =
        PiSessionReadout(tabName, state?.let { PiStatus(it) }, active = false)
}
