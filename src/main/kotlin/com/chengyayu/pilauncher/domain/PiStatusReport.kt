package com.chengyayu.pilauncher.domain

import java.util.Base64

/**
 * Decodes what the Pi Launcher extension writes for one session (`docs/adr/0005`).
 *
 * One `key=value` per line; `message` and `activity` are base64-encoded UTF-8 so they may
 * carry newlines and control characters:
 *
 * ```
 * state=blocked
 * kind=confirm
 * message=QWxsb3cgdG9vbCBjYWxsPw==
 * ```
 */
object PiStatusReport {

    /** Written next to the extension, so `__dirname` locates it. */
    const val FILE_NAME = "status"

    /** Pi's own id for the conversation (`docs/adr/0006`). */
    private const val IDENTITY = "id"

    private const val STATE = "state"
    private const val KIND = "kind"
    private const val MESSAGE = "message"
    private const val ACTIVITY = "activity"

    /**
     * Null when the text is not a report this plugin understands. Unknown keys are ignored:
     * a newer extension must not blind an older plugin.
     */
    fun parse(text: String): PiSessionReport? {
        val fields = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            fields[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }

        val state = fields[STATE]?.let(PiStatus.State::fromWire) ?: return null

        return PiSessionReport(
            status = PiStatus(
                state = state,
                kind = fields[KIND]?.let(PiStatus.Kind::fromWire),
                message = fields[MESSAGE]?.let(::decodeMessage),
                activity = fields[ACTIVITY]?.let(::decodeMessage)
            ),
            identity = fields[IDENTITY]?.takeIf { it.isNotBlank() }
        )
    }

    /** A line that fails to decode must not cost the state it carried. */
    private fun decodeMessage(encoded: String): String? = try {
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8).ifBlank { null }
    } catch (_: IllegalArgumentException) {
        null
    }
}
