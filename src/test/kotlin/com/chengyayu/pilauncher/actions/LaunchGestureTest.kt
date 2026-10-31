package com.chengyayu.pilauncher.actions

import java.awt.event.InputEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which gesture the π button was: a plain click focuses the session you have, Alt starts another. */
class LaunchGestureTest {

    @Test
    fun `a plain click launches`() {
        assertFalse(startsAnotherSession(0))
    }

    @Test
    fun `alt-click always starts another session`() {
        assertTrue(startsAnotherSession(InputEvent.ALT_DOWN_MASK))
        assertTrue(startsAnotherSession(InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
    }

    @Test
    fun `other modifiers do not mean another session`() {
        assertFalse(startsAnotherSession(InputEvent.SHIFT_DOWN_MASK))
        assertFalse(startsAnotherSession(InputEvent.META_DOWN_MASK or InputEvent.CTRL_DOWN_MASK))
    }
}
