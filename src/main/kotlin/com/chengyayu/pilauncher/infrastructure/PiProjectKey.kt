package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.project.Project
import java.io.File
import java.security.MessageDigest

/**
 * A stable, filesystem-safe directory name for a project. Hashed because directory names
 * collide, and stable so the next run still recognises and cleans up its own leftovers.
 */
internal fun projectKey(project: Project): String = projectKey(project.basePath, project.name)

internal fun projectKey(basePath: String?, name: String): String {
    val path = basePath ?: name
    val readable = File(path).name
        .ifBlank { "project" }
        .replace(UNSAFE_CHARACTERS, "_")
        .take(MAX_NAME_LENGTH)

    val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
    val fingerprint = digest.take(4).joinToString("") { "%02x".format(it) }

    return "$readable-$fingerprint"
}

private val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9._-]")
private const val MAX_NAME_LENGTH = 24
