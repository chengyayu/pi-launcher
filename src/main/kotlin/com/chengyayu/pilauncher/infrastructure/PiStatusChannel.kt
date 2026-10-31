package com.chengyayu.pilauncher.infrastructure

import com.chengyayu.pilauncher.domain.PiSessionReport

/** Reads what Pi last reported - state and identity; null when it reported nothing. */
fun interface PiStatusSource {
    fun read(): PiSessionReport?
}

/**
 * One session's status source: a file whose extension Pi loads with `--extension` and whose
 * report the plugin reads back (`docs/adr/0005`).
 */
interface PiStatusChannel : PiStatusSource {

    /** Quoted by [com.chengyayu.pilauncher.domain.PiCommandLine] before it reaches a command line. */
    val extensionPath: String
}

/** Opens and closes per-session status channels. */
interface PiStatusChannelProvider {

    /** Null when this Pi cannot host an extension: the session then reads `no status from Pi`. */
    fun open(sessionId: String): PiStatusChannel?

    fun close(sessionId: String)

    companion object {
        /** For Pi builds that cannot load extensions, and for tests. */
        val NONE: PiStatusChannelProvider = object : PiStatusChannelProvider {
            override fun open(sessionId: String): PiStatusChannel? = null
            override fun close(sessionId: String) = Unit
        }
    }
}
