package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.domain.PiStatusReadout
import com.chengyayu.pilauncher.ui.PiDashboardVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

/**
 * Status bar entry for the Pi Sessions of this project.
 *
 * It listens on the topic rather than reading the registry, keeping the registry
 * free of any dependency on the UI that displays it.
 *
 * The text names what Pi says it is doing - `blocked` first, because it is the only
 * state that needs the user - and a session that has not reported yet is counted as
 * `unknown` rather than assumed idle (`docs/adr/0004`).
 */
class PiStatusWidget(private val project: Project) :
    StatusBarWidget,
    StatusBarWidget.TextPresentation,
    Disposable {

    private var statusBar: StatusBar? = null

    @Volatile
    private var sessions: List<PiSessionReadout> = emptyList()

    private val connection = project.messageBus.connect(this).apply {
        subscribe(
            PiSessionStatusListener.TOPIC,
            PiSessionStatusListener { newSessions ->
                sessions = newSessions
                statusBar?.updateWidget(PiStatusWidgetFactory.ID)
            }
        )
    }

    override fun ID(): String = PiStatusWidgetFactory.ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // The widget can be installed after sessions started; catch up once.
        sessions = PiSessionService.getInstance(project).readouts
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String = PiStatusReadout.text(sessions)

    override fun getTooltipText(): String = "${PiStatusReadout.tooltip(sessions)} · $CLICK_HINT"

    /**
     * Clicking opens the Session Dashboard; Alt-click starts a session without
     * leaving whatever is on screen.
     */
    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
        if (event.isAltDown) {
            PiSessionService.getInstance(project).newSession()
        } else {
            PiDashboardVirtualFile.open(project)
        }
    }

    override fun getAlignment(): Float = 0f

    override fun dispose() {
        connection.disconnect()
        statusBar = null
    }

    private companion object {
        const val CLICK_HINT = "Click: dashboard · Alt+click: new session"
    }
}
