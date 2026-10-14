package com.chengyayu.pilauncher.infrastructure

/**
 * Answers "can this Pi host the status extension?", once per IDE session.
 *
 * The answer depends only on the Pi installation, which is the same in every project,
 * and finding out costs a subprocess - so a second project must not ask again.
 */
fun interface PiStatusProbeCache {

    /** Returns the remembered answer, or runs [detect] and remembers its result. */
    fun answerOrProbe(detect: () -> Boolean): Boolean

    companion object {
        /** Asks [detect] every time; for tests and for callers without a cache. */
        val UNSHARED: PiStatusProbeCache = PiStatusProbeCache { it() }
    }
}
