package com.chengyayu.pilauncher.actions

import com.chengyayu.pilauncher.services.PiSessionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.event.InputEvent

/**
 * One-click launch: focuses the Active Session, or starts a new one when no session is
 * running. Alt-click always starts an additional session.
 *
 * Alt is the only way to ask for a second session: a separate "New Pi Session" button
 * next to the terminal's own `+` was one more icon saying what this one already says,
 * and a context menu entry nobody opens first.
 */
class LaunchPiAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = PiSessionService.getInstance(project)

        if (startsAnotherSession(e.inputEvent?.modifiersEx ?: 0)) service.newSession() else service.launch()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/** Alt means "another one, not the one I have". Read separately so it can be tested. */
internal fun startsAnotherSession(extendedModifiers: Int): Boolean =
    extendedModifiers and InputEvent.ALT_DOWN_MASK != 0
