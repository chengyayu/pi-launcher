package com.chengyayu.pilauncher.domain

/**
 * One read of Pi's report file: the state Pi last announced, and the identity of the
 * conversation. Both live in one file so one read cannot mix moments.
 */
data class PiSessionReport(
    val status: PiStatus?,
    /** Null until Pi has started the session. */
    val identity: String?
)
