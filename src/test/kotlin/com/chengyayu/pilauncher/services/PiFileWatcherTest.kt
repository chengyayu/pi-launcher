package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.settings.PiSettings
import kotlin.test.Test
import kotlin.test.assertFalse

class PiFileWatcherTest {

    /** Auto-open must stay off: otherwise every Pi session starts opening dozens of editors. */
    @Test
    fun `auto-open defaults to off`() {
        assertFalse(PiSettings.State().autoOpenFiles)
    }
}
