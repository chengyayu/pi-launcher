package com.chengyayu.pilauncher.domain

/**
 * A model advertised by the Pi CLI in `~/.pi/agent/models.json`.
 */
data class PiModel(
    val id: String,
    val name: String,
    val provider: String
) {
    /** Value accepted by the `pi --model` flag. */
    val commandArg: String get() = "$provider/$id"

    /** Label shown in the settings drop-down. */
    val displayName: String get() = if (name.isBlank()) commandArg else "$name ($commandArg)"
}
