package com.chengyayu.pilauncher.domain

/**
 * What Pi reports about its own execution, via the extension the plugin loads into it
 * (`docs/adr/0005`). Not to be confused with Inferred Status.
 */
data class PiStatus(
    val state: State,
    /** Only set for [State.BLOCKED]. */
    val kind: Kind? = null,
    /** Pi's own words: a dialog title, an error's first line. */
    val message: String? = null,
    /** The tool call it is running right now, if any. */
    val activity: String? = null
) {

    enum class State(val wireName: String) {
        WORKING("working"),

        /** A dialog is waiting for the user. */
        BLOCKED("blocked"),

        DONE("done"),

        /** Pi did not retry the error. */
        ERROR("error"),

        /** Pi started, or the user cancelled the run. */
        IDLE("idle");

        companion object {
            fun fromWire(name: String): State? = entries.firstOrNull { it.wireName == name }
        }
    }

    /** The blocking prompts the extension UI can raise. */
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
