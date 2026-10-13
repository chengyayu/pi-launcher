package com.chengyayu.pilauncher.domain

import java.util.Base64

/**
 * Decodes the status reports the Pi Launcher extension writes for one session
 * (`docs/adr/0005`).
 *
 * The format is one `key=value` per line, with `message` base64-encoded UTF-8 so it
 * may contain newlines and control characters:
 *
 * ```
 * state=blocked
 * kind=confirm
 * message=QWxsb3cgdG9vbCBjYWxsPw==
 * ```
 *
 * `message` is Pi's own words about the state, `activity` is what it is doing right
 * now; a session can have either, both, or neither.
 *
 * Pure string handling, so the wire format is unit testable without Pi.
 */
object PiStatusReport {

    /** Written next to the extension, so `__dirname` locates it. */
    const val FILE_NAME = "status"

    private const val STATE = "state"
    private const val KIND = "kind"
    private const val MESSAGE = "message"
    private const val ACTIVITY = "activity"

    /**
     * Decodes [text], or `null` when it is not a report this plugin understands.
     *
     * Unknown keys are ignored rather than rejected: a newer extension must not
     * blind an older plugin.
     */
    fun parse(text: String): PiStatus? {
        val fields = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            // The first '=' separates key from value, so base64 padding survives.
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            fields[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }

        val state = fields[STATE]?.let(PiStatus.State::fromWire) ?: return null

        return PiStatus(
            state = state,
            kind = fields[KIND]?.let(PiStatus.Kind::fromWire),
            message = fields[MESSAGE]?.let(::decodeMessage),
            activity = fields[ACTIVITY]?.let(::decodeMessage)
        )
    }

    /**
     * A report that fails to decode must not cost us the state it carried, and the
     * plugin never trusts the file enough to let a bad line throw.
     */
    private fun decodeMessage(encoded: String): String? = try {
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8).ifBlank { null }
    } catch (_: IllegalArgumentException) {
        null
    }
}
