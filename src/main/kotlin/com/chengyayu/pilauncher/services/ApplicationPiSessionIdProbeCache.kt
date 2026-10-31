package com.chengyayu.pilauncher.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.chengyayu.pilauncher.infrastructure.PiSessionIdProbeCache

/**
 * The application-wide answer to "does this Pi know `--session-id`?".
 *
 * Shared between projects for the same reason as [ApplicationPiStatusProbeCache]: the
 * answer belongs to the Pi installation, and asking costs a subprocess.
 */
@Service(Service.Level.APP)
class ApplicationPiSessionIdProbeCache : PiSessionIdProbeCache {

    @Volatile
    private var answer: Boolean? = null

    override fun answerOrProbe(detect: () -> Boolean): Boolean {
        answer?.let { return it }

        // A project that asks while the first answer is still coming waits here
        // instead of starting a second subprocess. Both callers run off the EDT.
        return synchronized(this) {
            answer ?: detect().also { answer = it }
        }
    }

    companion object {
        fun getInstance(): ApplicationPiSessionIdProbeCache = service()
    }
}
