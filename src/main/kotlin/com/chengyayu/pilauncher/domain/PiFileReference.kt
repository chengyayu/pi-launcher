package com.chengyayu.pilauncher.domain

/**
 * A reference to a file, optionally narrowed to a line range, in the format the
 * Pi prompt understands: `@relative/path#L10-25`.
 */
data class PiFileReference(
    val path: String,
    val startLine: Int? = null,
    val endLine: Int? = null
) {
    fun render(): String {
        val anchor = when {
            startLine == null -> ""
            endLine == null || endLine <= startLine -> "#L$startLine"
            else -> "#L$startLine-$endLine"
        }
        return "@$path$anchor"
    }

    companion object {
        /** A whole-file reference. */
        fun ofFile(projectBasePath: String?, filePath: String): PiFileReference =
            PiFileReference(relativize(projectBasePath, filePath))

        /** A reference to an inclusive, 1-based line range. */
        fun ofRange(projectBasePath: String?, filePath: String, startLine: Int, endLine: Int): PiFileReference =
            PiFileReference(relativize(projectBasePath, filePath), startLine, endLine)

        /** Files inside the project are referenced relative to its root. */
        fun relativize(projectBasePath: String?, filePath: String): String {
            if (projectBasePath.isNullOrEmpty() || !filePath.startsWith(projectBasePath)) return filePath
            return filePath.removePrefix(projectBasePath).removePrefix("/")
        }
    }
}
