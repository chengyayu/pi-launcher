package com.chengyayu.pilauncher.ui

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.domain.PiStatus
import com.chengyayu.pilauncher.domain.PiStatusReadout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The dashboard's rows, mapped from the same readouts the status bar renders. */
class PiDashboardTableModelTest {

    private val model = PiDashboardTableModel()

    @Test
    fun `starts with no rows`() {
        assertEquals(0, model.rowCount)
    }

    @Test
    fun `one row per session, in registry order`() {
        model.setSessions(listOf(readout("Pi 1", PiStatus.State.IDLE), readout("Pi 2", null)))

        assertEquals(2, model.rowCount)
        assertEquals("Pi 1", model.getValueAt(0, COLUMN_SESSION))
        assertEquals("Pi 2", model.getValueAt(1, COLUMN_SESSION))
    }

    @Test
    fun `names its columns`() {
        val names = (0 until model.columnCount).map(model::getColumnName)

        assertEquals(listOf("Session", "Status", "Detail"), names)
    }

    @Test
    fun `shows the state Pi reported, and no state when it reported none`() {
        model.setSessions(
            listOf(
                readout("Pi 1", PiStatus.State.BLOCKED, "Allow tool call?"),
                readout("Pi 2", null)
            )
        )

        assertEquals("blocked", model.getValueAt(0, COLUMN_STATUS))
        assertEquals("Allow tool call?", model.getValueAt(0, COLUMN_DETAIL))
        assertEquals(PiStatusReadout.NO_STATUS, model.getValueAt(1, COLUMN_STATUS))
        assertEquals("", model.getValueAt(1, COLUMN_DETAIL))
    }

    @Test
    fun `marks the active session`() {
        model.setSessions(listOf(PiSessionReadout("Pi 1", PiStatus(PiStatus.State.IDLE), active = true)))

        assertEquals("• Pi 1", model.getValueAt(0, COLUMN_SESSION))
    }

    @Test
    fun `reports the readout behind a row so the panel can act on it`() {
        model.setSessions(listOf(readout("Pi 1", PiStatus.State.IDLE)))

        assertEquals("Pi 1", model.sessionAt(0)?.tabName)
        assertNull(model.sessionAt(1))
    }

    @Test
    fun `cells are read-only so the dashboard never edits a session`() {
        model.setSessions(listOf(readout("Pi 1", PiStatus.State.IDLE)))

        assertFalse(model.isCellEditable(0, COLUMN_STATUS))
    }

    @Test
    fun `replacing the sessions replaces the rows`() {
        model.setSessions(listOf(readout("Pi 1", PiStatus.State.IDLE), readout("Pi 2", null)))
        model.setSessions(emptyList())

        assertEquals(0, model.rowCount)
    }

    private fun readout(tabName: String, state: PiStatus.State?, message: String? = null) =
        PiSessionReadout(tabName, state?.let { PiStatus(it, message = message) }, active = false)

    private companion object {
        const val COLUMN_SESSION = 0
        const val COLUMN_STATUS = 1
        const val COLUMN_DETAIL = 2
    }
}
