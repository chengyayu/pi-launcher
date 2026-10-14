package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.infrastructure.PiStatusProbeCache
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * The one probe result for this IDE session, shared by every project.
 *
 * Probing runs `pi --help`, and the answer depends only on the Pi installation, so
 * opening a second project must not pay for it again.
 */
@Service(Service.Level.APP)
class ApplicationPiStatusProbeCache : PiStatusProbeCache {

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
        fun getInstance(): ApplicationPiStatusProbeCache = service()
    }
}
