package com.chengyayu.pilauncher.ui

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.services.PiSessionService
import com.chengyayu.pilauncher.services.PiSessionStatusListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * The Session Dashboard: every running Pi Session with the Session Status Pi reports
 * for it (`docs/adr/0005`).
 *
 * It renders the state it is given and never invents one - a session without a
 * report reads "no status from Pi", which is exactly what the user needs to know when
 * the Status Source is unavailable.
 *
 * Deliberately read-only apart from two safe actions: showing a session in the
 * Terminal window, and starting a new one. Anything that could disturb a running
 * session belongs in the terminal it runs in, where the user can see what happens.
 */
internal class PiDashboardPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val model = PiDashboardTableModel()
    private val table = JBTable(model)
    private val cards = CardLayout()
    private val content = JPanel(cards)
    private val showInTerminal = JButton("Show in Terminal")

    private val service: PiSessionService get() = PiSessionService.getInstance(project)

    private val connection = project.messageBus.connect(this).apply {
        subscribe(
            PiSessionStatusListener.TOPIC,
            PiSessionStatusListener { sessions -> render(sessions) }
        )
    }

    init {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(false)
        table.rowHeight = JBUI.scale(22)
        table.columnModel.getColumn(COLUMN_STATUS).preferredWidth = JBUI.scale(120)
        table.columnModel.getColumn(COLUMN_DETAIL).preferredWidth = JBUI.scale(320)

        table.addMouseListener(object : MouseAdapter() {
            /** Clicking a session is a request to look at it, so it goes straight there. */
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 1 && table.rowAtPoint(event.point) >= 0) focusSelected()
            }
        })
        table.selectionModel.addListSelectionListener { showInTerminal.isEnabled = selectedTabName() != null }

        content.add(emptyState(), CARD_EMPTY)
        content.add(JBScrollPane(table), CARD_TABLE)

        showInTerminal.isEnabled = false
        showInTerminal.addActionListener { focusSelected() }

        val newSession = JButton("New Pi Session")
        newSession.addActionListener { service.newSession() }

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(6)))
        actions.add(showInTerminal)
        actions.add(newSession)

        add(content, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)

        render(service.readouts)
    }

    /** Only ever called on the EDT: the topic publishes through `invokeLater`. */
    private fun render(sessions: List<PiSessionReadout>) {
        val selected = selectedTabName()
        model.setSessions(sessions)
        restoreSelection(selected)
        cards.show(content, if (sessions.isEmpty()) CARD_EMPTY else CARD_TABLE)
        showInTerminal.isEnabled = selectedTabName() != null
    }

    /** A status update must not steal the user's selection. */
    private fun restoreSelection(tabName: String?) {
        val row = (0 until model.rowCount)
            .firstOrNull { model.sessionAt(it)?.tabName == tabName } ?: return
        table.setRowSelectionInterval(row, row)
    }

    private fun selectedTabName(): String? =
        model.sessionAt(table.selectedRow)?.tabName

    private fun focusSelected() {
        selectedTabName()?.let { service.focus(it) }
    }

    private fun emptyState(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(
            JBLabel("No Pi sessions yet. Start one with New Pi Session below."),
            BorderLayout.CENTER
        )
        panel.border = JBUI.Borders.empty(JBUI.scale(16))
        return panel
    }

    override fun dispose() {
        connection.disconnect()
    }

    private companion object {
        const val CARD_EMPTY = "empty"
        const val CARD_TABLE = "table"
        const val COLUMN_STATUS = 1
        const val COLUMN_DETAIL = 2
    }
}
