package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.settings.PiSettings
import kotlin.test.Test
import kotlin.test.assertFalse

class PiFileWatcherTest {

    /**
     * A session can rewrite dozens of files. Opening them as editor tabs is only
     * acceptable when the user explicitly asks for it, so the default must stay
     * off - if it ever flips, every Pi session would start opening editors.
     */
    @Test
    fun `auto-open defaults to off`() {
        assertFalse(PiSettings.State().autoOpenFiles)
    }
}
