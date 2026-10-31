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
 * Status bar entry for this project's Pi Sessions. It listens on the topic rather than reading
 * the registry, so the registry knows nothing about the UI that displays it.
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
        // The widget can be installed after sessions started.
        sessions = PiSessionService.getInstance(project).readouts
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String = PiStatusReadout.text(sessions)

    override fun getTooltipText(): String = "${PiStatusReadout.tooltip(sessions)} · $CLICK_HINT"

    /** Clicking opens the Session Dashboard; Alt-click starts a session without leaving what is on screen. */
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
