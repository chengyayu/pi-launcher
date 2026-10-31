package com.chengyayu.pilauncher.domain

/** The user-facing inputs that determine how the Pi CLI is started. */
data class PiLaunchOptions(
    val command: String = DEFAULT_COMMAND,
    val model: String? = null,
    val thinkingLevel: String? = null,
    val extraArgs: String = ""
) {
    companion object {
        const val DEFAULT_COMMAND = "pi"

        /** Sentinel the settings UI uses for "let Pi decide". */
        const val DEFAULT_VALUE = "Default"
    }
}
