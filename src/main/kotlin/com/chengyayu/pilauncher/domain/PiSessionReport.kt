package com.chengyayu.pilauncher.domain

/**
 * One read of Pi's report file: the state Pi last announced, and the identity it stores
 * this conversation under.
 *
 * Both come from the same file because both come from the same reporter, and reading it
 * once per poll is what keeps the two from describing different moments.
 */
data class PiSessionReport(
    val status: PiStatus?,
    /** Null until Pi has started the session, or when Pi cannot say. */
    val identity: String?
)
