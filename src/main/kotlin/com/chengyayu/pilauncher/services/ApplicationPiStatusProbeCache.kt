package com.chengyayu.pilauncher.services

import com.chengyayu.pilauncher.infrastructure.PiStatusProbeCache
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/** One probe result per IDE session: probing runs `pi --help`, so a second project must not pay for it again. */
@Service(Service.Level.APP)
class ApplicationPiStatusProbeCache : PiStatusProbeCache {

    @Volatile
    private var answer: Boolean? = null

    override fun answerOrProbe(detect: () -> Boolean): Boolean {
        answer?.let { return it }

        // A caller asking while the first answer is still coming waits instead of starting a second subprocess.
        return synchronized(this) {
            answer ?: detect().also { answer = it }
        }
    }

    companion object {
        fun getInstance(): ApplicationPiStatusProbeCache = service()
    }
}
