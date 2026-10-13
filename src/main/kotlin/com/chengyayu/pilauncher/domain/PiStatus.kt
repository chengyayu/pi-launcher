package com.chengyayu.pilauncher.domain

/**
 * A Session Status: what Pi reports about its own execution, as opposed to what the
 * plugin can infer from a process being alive.
 *
 * Pi publishes this through the status extension the plugin loads into it
 * (`docs/adr/0005`); it is the only trustworthy source, so a missing report must
 * degrade to Inferred Status rather than to a guess dressed up as this.
 */
data class PiStatus(
    val state: State,
    /** What Pi is waiting on. Pi only sets it for [State.BLOCKED]. */
    val kind: Kind? = null,
    /** Pi's own words about this state: a dialog title, an error's first line. */
    val message: String? = null,
    /** What Pi is doing right now, such as the tool call it is running. */
    val activity: String? = null
) {

    enum class State(val wireName: String) {
        /** An agent run or a compaction is in progress. */
        WORKING("working"),

        /** A blocking dialog is waiting for the user. */
        BLOCKED("blocked"),

        /** A run finished. */
        DONE("done"),

        /** A run ended with an error that Pi did not retry. */
        ERROR("error"),

        /** Pi started, or the user cancelled the run. */
        IDLE("idle");

        companion object {
            fun fromWire(name: String): State? = entries.firstOrNull { it.wireName == name }
        }
    }

    /** The kind of blocking prompt Pi raised; these are the extension UI prompts. */
    enum class Kind(val wireName: String) {
        CONFIRM("confirm"),
        SELECT("select"),
        INPUT("input"),
        EDITOR("editor"),
        CUSTOM("custom");

        companion object {
            fun fromWire(name: String): Kind? = entries.firstOrNull { it.wireName == name }
        }
    }
}
