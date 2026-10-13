package com.chengyayu.pilauncher.infrastructure

import com.chengyayu.pilauncher.domain.PiStatus

/** Reads the Session Status Pi last reported; null when it has reported none. */
fun interface PiStatusSource {
    fun read(): PiStatus?
}

/**
 * One session's channel for Pi's own status reports (`docs/adr/0005`).
 *
 * Pi cannot be observed from outside, so the plugin puts a reporter inside it: the
 * channel's extension file is handed to Pi's `--extension` flag and writes the
 * report the channel reads back.
 */
interface PiStatusChannel : PiStatusSource {

    /** Path passed to Pi's `--extension` flag, quoted by [com.chengyayu.pilauncher.domain.PiCommandLine]. */
    val extensionPath: String
}

/**
 * Opens and closes per-session status channels.
 *
 * A provider that cannot host one returns `null`, and the session then has no Status
 * Source at all: it degrades to Inferred Status instead of guessing.
 */
interface PiStatusChannelProvider {

    /** A channel for [sessionId], or null when this Pi cannot host one. */
    fun open(sessionId: String): PiStatusChannel?

    /** Forget everything written for [sessionId]. */
    fun close(sessionId: String)

    companion object {
        /** For Pi builds that cannot load extensions, and for tests. */
        val NONE: PiStatusChannelProvider = object : PiStatusChannelProvider {
            override fun open(sessionId: String): PiStatusChannel? = null
            override fun close(sessionId: String) = Unit
        }
    }
}
