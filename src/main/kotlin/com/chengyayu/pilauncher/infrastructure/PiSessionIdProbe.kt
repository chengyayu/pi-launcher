package com.chengyayu.pilauncher.infrastructure

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.execution.ParametersListUtil

/**
 * Answers "does this Pi know `--session-id`?", once per IDE session.
 *
 * The answer depends only on the Pi installation, which is the same in every project,
 * and finding out costs a subprocess - so a second project must not ask again.
 */
fun interface PiSessionIdProbeCache {

    /** Returns the remembered answer, or runs [detect] and remembers its result. */
    fun answerOrProbe(detect: () -> Boolean): Boolean

    companion object {
        /** Asks [detect] every time; for tests and for callers without a cache. */
        val UNSHARED: PiSessionIdProbeCache = PiSessionIdProbeCache { it() }
    }
}

/**
 * Decides whether a Pi Session can be given an identity (`docs/adr/0006`).
 *
 * Unknown counts as "no". A Pi that does not know the flag would refuse to start, and
 * an unidentified session is still a working session - the same trade as the status
 * extension, for the same reason.
 */
class PiSessionIdProbe(
    private val piCommand: () -> String,
    private val cache: PiSessionIdProbeCache = PiSessionIdProbeCache.UNSHARED,
    private val detectSupport: (command: String) -> Boolean = ::asksPiForSessionIds
) {

    @Volatile
    private var supported: Boolean? = null

    /** Asks Pi once, in the background; a session started before the answer has no identity. */
    fun probe() {
        if (supported != null) return
        supported = cache.answerOrProbe { detectSupport(piCommand()) }
    }

    fun isSupported(): Boolean = supported ?: false
}

/**
 * Asks Pi itself whether it knows `--session-id`.
 *
 * A failure is an answer too: the command is the user's and may not even be on PATH.
 */
private fun asksPiForSessionIds(command: String): Boolean = try {
    val line = GeneralCommandLine(ParametersListUtil.parse(command))
        // The shell's environment, not the IDE's: a Pi installed through npm or a
        // version manager is on the terminal's PATH but usually not on the PATH an
        // IDE launched from the Dock inherits, and the terminal is where Pi runs.
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withParameters(HELP_FLAG)
    val output = CapturingProcessHandler(line).runProcess(PROBE_TIMEOUT_MILLIS)
    (output.stdout + output.stderr).contains(SESSION_ID_FLAG)
} catch (e: Exception) {
    Logger.getInstance(PiSessionIdProbe::class.java)
        .info("Could not ask Pi about $SESSION_ID_FLAG: ${e.message}")
    false
}

private const val SESSION_ID_FLAG = "--session-id"
private const val HELP_FLAG = "--help"
private const val PROBE_TIMEOUT_MILLIS = 20_000
