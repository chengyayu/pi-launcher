package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

/**
 * Status bar entry showing how many Pi sessions are running.
 *
 * It listens on the topic rather than reading the registry, keeping the registry
 * free of any dependency on the UI that displays it.
 */
class PiStatusWidget(private val project: Project) :
    StatusBarWidget,
    StatusBarWidget.TextPresentation,
    Disposable {

    private var statusBar: StatusBar? = null

    @Volatile
    private var runningCount: Int = 0

    private val connection = project.messageBus.connect(this).apply {
        subscribe(
            PiSessionListener.TOPIC,
            PiSessionListener { newCount ->
                runningCount = newCount
                statusBar?.updateWidget(PiStatusWidgetFactory.ID)
            }
        )
    }

    override fun ID(): String = PiStatusWidgetFactory.ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // The widget can be installed after sessions started; catch up once.
        runningCount = PiSessionService.getInstance(project).runningCount
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String = if (runningCount > 0) {
        "π $runningCount running"
    } else {
        "π all-idle"
    }

    override fun getTooltipText(): String = if (runningCount > 0) {
        "$runningCount Pi session${if (runningCount == 1) "" else "s"} running. Click to focus the active one."
    } else {
        "No Pi sessions running. Click to launch."
    }

    override fun getClickConsumer(): Consumer<MouseEvent> =
        Consumer { PiSessionService.getInstance(project).launch() }

    override fun getAlignment(): Float = 0f

    override fun dispose() {
        connection.disconnect()
        statusBar = null
    }
}
