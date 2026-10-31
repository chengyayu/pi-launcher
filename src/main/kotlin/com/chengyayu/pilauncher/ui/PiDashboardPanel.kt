package com.chengyayu.pilauncher.ui

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.services.PiSessionService
import com.chengyayu.pilauncher.services.PiSessionStatusListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
 * The Session Dashboard: every Pi Session, with the Session Status Pi reports for the
 * ones that are running (`docs/adr/0005`).
 *
 * It renders the state it is given and never invents one - a session without a report
 * reads "no status from Pi", and a session with no Host reads "not running", which are
 * three different things and stay three different things.
 *
 * It is also the one surface that does not depend on a Host, which is why a parked
 * session is reopened from here (`docs/adr/0006`).
 *
 * Deliberately quiet: showing a session in its Host, reopening a parked one, renaming
 * a parked one, and starting a new one. Anything that could disturb a *running* session
 * - interrupting it, killing its run - belongs in the Host it runs in, where the user
 * can see what happens.
 */
internal class PiDashboardPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val model = PiDashboardTableModel()
    private val table = JBTable(model)
    private val cards = CardLayout()
    private val content = JPanel(cards)
    private val showInTerminal = JButton("Show in Terminal")
    private val resume = JButton("Resume")
    private val rename = JButton("Rename…")

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
            /** Clicking a running session is a request to look at it, so it goes straight there. */
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount != 1 || table.rowAtPoint(event.point) < 0) return
                if (selected()?.running == true) focusSelected()
            }
        })
        table.selectionModel.addListSelectionListener { updateActions() }

        content.add(emptyState(), CARD_EMPTY)
        content.add(JBScrollPane(table), CARD_TABLE)

        showInTerminal.addActionListener { focusSelected() }
        resume.addActionListener { selected()?.id?.let(service::resume) }
        rename.addActionListener { renameSelected() }

        val newSession = JButton("New Pi Session")
        newSession.addActionListener { service.newSession() }

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(6)))
        actions.add(showInTerminal)
        actions.add(resume)
        actions.add(rename)
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

    private fun keyOf(readout: PiSessionReadout): String = readout.id ?: readout.tabName

    private fun updateActions() {
        val session = selected()
        showInTerminal.isEnabled = session?.running == true
        // Reopening and renaming a parked session are the two actions that only exist here.
        resume.isEnabled = session != null && !session.running && session.id != null
        rename.isEnabled = session != null && !session.running && session.id != null
    }

    private fun focusSelected() {
        selected()?.takeIf { it.running }?.let { service.focus(it.tabName) }
    }

    private fun renameSelected() {
        val session = selected()?.takeIf { !it.running } ?: return
        val name = Messages.showInputDialog(
            project,
            "Name for this Pi session.",
            "Rename Pi Session",
            null,
            session.tabName,
            null
        ) ?: return

        session.id?.let { service.renameParked(it, name) }
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
