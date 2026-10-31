package com.chengyayu.pilauncher.infrastructure

/** "Can this Pi host the status extension?" - asked once per IDE session, since probing runs a subprocess. */
fun interface PiStatusProbeCache {

    /** The remembered answer, or the result of running [detect] and remembering it. */
    fun answerOrProbe(detect: () -> Boolean): Boolean

    companion object {
        /** Asks every time; for tests and callers without a cache. */
        val UNSHARED: PiStatusProbeCache = PiStatusProbeCache { it() }
    }
}
