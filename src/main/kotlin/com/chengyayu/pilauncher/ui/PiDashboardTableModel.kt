package com.chengyayu.pilauncher.ui

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.domain.PiStatusReadout
import javax.swing.table.AbstractTableModel

/**
 * The Session Dashboard's rows: one per Pi Session, in registry order.
 *
 * Kept apart from the panel so the mapping from a readout to a cell is testable
 * without a UI.
 */
internal class PiDashboardTableModel : AbstractTableModel() {

    private var sessions: List<PiSessionReadout> = emptyList()

    fun setSessions(readouts: List<PiSessionReadout>) {
        sessions = readouts
        fireTableDataChanged()
    }

    fun sessionAt(row: Int): PiSessionReadout? = sessions.getOrNull(row)

    override fun getRowCount(): Int = sessions.size

    override fun getColumnCount(): Int = COLUMNS.size

    override fun getColumnName(column: Int): String = COLUMNS[column]

    override fun isCellEditable(row: Int, column: Int): Boolean = false

    override fun getValueAt(row: Int, column: Int): Any {
        val session = sessions[row]
        return when (column) {
            COLUMN_SESSION -> PiStatusReadout.sessionLabel(session)
            COLUMN_STATUS -> PiStatusReadout.stateLabel(session)
            COLUMN_DETAIL -> PiStatusReadout.detail(session) ?: ""
            else -> ""
        }
    }

    private companion object {
        const val COLUMN_SESSION = 0
        const val COLUMN_STATUS = 1
        const val COLUMN_DETAIL = 2
        val COLUMNS = listOf("Session", "Status", "Detail")
    }
}
