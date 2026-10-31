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
 * The Session Dashboard: the running Pi Sessions and the Session Status Pi reports for them
 * (`docs/adr/0005`). It never invents a state, and it only offers actions that cannot disturb
 * a running session - showing it, or starting another.
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
            /** Looking at a session means going to it. */
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount != 1 || table.rowAtPoint(event.point) < 0) return
                focusSelected()
            }
        })
        table.selectionModel.addListSelectionListener { updateActions() }

        content.add(emptyState(), CARD_EMPTY)
        content.add(JBScrollPane(table), CARD_TABLE)

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
        val selected = selected()?.let(::keyOf)
        model.setSessions(sessions)
        restoreSelection(selected)
        cards.show(content, if (sessions.isEmpty()) CARD_EMPTY else CARD_TABLE)
        updateActions()
    }

    /** A status update must not steal the user's selection. */
    private fun restoreSelection(key: String?) {
        if (key == null) return
        val row = (0 until model.rowCount)
            .firstOrNull { model.sessionAt(it)?.let(::keyOf) == key } ?: return
        table.setRowSelectionInterval(row, row)
    }

    private fun selected(): PiSessionReadout? = model.sessionAt(table.selectedRow)

    private fun keyOf(readout: PiSessionReadout): String = readout.tabName

    private fun updateActions() {
        showInTerminal.isEnabled = selected() != null
    }

    private fun focusSelected() {
        selected()?.let { service.focus(it.tabName) }
    }

    private fun emptyState(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(
            JBLabel("No Pi sessions running. Start one with New Pi Session below."),
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
