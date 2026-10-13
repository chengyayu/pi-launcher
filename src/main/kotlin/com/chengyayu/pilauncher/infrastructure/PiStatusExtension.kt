package com.chengyayu.pilauncher.infrastructure

import com.chengyayu.pilauncher.domain.PiStatus
import com.chengyayu.pilauncher.domain.PiStatusReport
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Hosts Pi's status extension and reads back what it reports (`docs/adr/0005`).
 *
 * Pi cannot be observed from outside - its own status protocol is an OSC sequence the
 * IDE terminal drops - so the plugin puts a reporter *inside* it. Each session gets
 * its own copy of [EXTENSION_RESOURCE] in a private directory, the copy's path is
 * handed to Pi's `--extension` flag, and the `status` file the copy writes beside
 * itself is what this provider reads.
 *
 * One copy per session is what lets the extension locate its report through
 * `__dirname`: no environment variable has to survive the user's shell, and no
 * terminal internals are touched.
 *
 * Everything here is best effort. A Pi that cannot load extensions, a missing
 * resource, an unwritable directory - each of those costs Session Status and returns
 * `null`, which callers render as Inferred Status. None of them may stop a session.
 */
internal class PiStatusExtension(
    private val piCommand: () -> String,
    private val baseDirectory: Path,
    private val detectSupport: (extensionPath: String) -> Boolean = { probeExtensionSupport(piCommand(), it) }
) : PiStatusChannelProvider {

    private val logger = Logger.getInstance(PiStatusExtension::class.java)

    /** The extension source, read once; null when the plugin's own resource is missing. */
    private val extensionText: String? by lazy { readResource() }

    /**
     * Whether Pi accepts `--extension`. Null until the probe answers, and `false`
     * means every session degrades. It is never guessed: passing a flag Pi does not
     * know would stop it from starting at all.
     */
    @Volatile
    private var extensionSupported: Boolean? = null

    /**
     * Decides once whether status reporting is possible, in the background.
     *
     * Called when the project opens rather than on the first launch, so a session
     * started by hand a moment later already has an answer.
     */
    fun probe() {
        if (extensionSupported != null) return

        discardStaleSessions()

        val probeExtension = prepareProbeExtension()
        if (probeExtension == null) {
            extensionSupported = false
            logger.info("Pi status extension supported: false (the plugin's own resource is missing)")
            return
        }

        val startedAt = System.currentTimeMillis()
        extensionSupported = detectSupport(probeExtension)
        logger.info(
            "Pi status extension supported: $extensionSupported " +
                "(probed in ${System.currentTimeMillis() - startedAt} ms)"
        )
    }

    /** A copy Pi can be asked to load without touching any session's directory. */
    private fun prepareProbeExtension(): String? {
        val text = extensionText ?: return null
        return try {
            val directory = baseDirectory.resolve(PROBE_DIRECTORY)
            directory.createDirectories()
            directory.resolve(EXTENSION_FILE_NAME).also { it.writeText(text) }.toString()
        } catch (e: Exception) {
            logger.warn("Could not stage the status extension for probing", e)
            null
        }
    }

    override fun open(sessionId: String): PiStatusChannel? {
        val supported = extensionSupported
        if (supported == null) {
            // The probe runs when the project opens; a session launched in the same
            // breath reports Inferred Status rather than waiting on it.
            logger.info("Pi status support is not known yet; $sessionId will infer its status")
            return null
        }
        if (!supported) return null

        val text = extensionText ?: return null
        val directory = sessionDirectory(sessionId)

        return try {
            directory.createDirectories()
            val extension = directory.resolve(EXTENSION_FILE_NAME)
            extension.writeText(text)
            SessionChannel(extension.toString(), directory.resolve(PiStatusReport.FILE_NAME))
        } catch (e: Exception) {
            logger.warn("Could not prepare a status channel for $sessionId", e)
            null
        }
    }

    override fun close(sessionId: String) {
        deleteRecursively(sessionDirectory(sessionId), "the status channel for $sessionId")
    }

    /** Sessions of a previous IDE run can never be reported on again. */
    private fun discardStaleSessions() {
        deleteRecursively(baseDirectory.resolve(SESSIONS_DIRECTORY), "stale status channels")
    }

    private fun deleteRecursively(directory: Path, what: String) {
        if (!directory.exists()) return
        try {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            logger.warn("Could not remove $what", e)
        }
    }

    private fun sessionDirectory(sessionId: String): Path =
        baseDirectory.resolve(SESSIONS_DIRECTORY).resolve(sessionId.replace(UNSAFE_CHARACTERS, "_"))

    private fun readResource(): String? = try {
        PiStatusExtension::class.java.getResourceAsStream(EXTENSION_RESOURCE)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        logger.warn("Could not read $EXTENSION_RESOURCE", e)
        null
    }

    private class SessionChannel(
        override val extensionPath: String,
        private val statusFile: Path
    ) : PiStatusChannel {

        /** The extension writes atomically, so a read never sees half a report. */
        override fun read(): PiStatus? = try {
            if (statusFile.exists()) PiStatusReport.parse(statusFile.readText()) else null
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val EXTENSION_RESOURCE = "/pi/pi-launcher-status.ts"
        const val EXTENSION_FILE_NAME = "status.ts"
        const val SESSIONS_DIRECTORY = "sessions"
        const val PROBE_DIRECTORY = "probe"
        const val EXTENSION_FLAG = "--extension"
        const val HELP_FLAG = "--help"
        const val PROBE_TIMEOUT_MILLIS = 20_000
        val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9._-]")
    }
}

/**
 * Asks Pi itself whether it knows `--extension`.
 *
 * Top level rather than a member: it is the default for a constructor parameter, and
 * a session must never be handed a flag its Pi would reject - that would stop it from
 * starting at all. The command is the user's and may not even be on PATH, so a failure
 * here is an answer too.
 */
private fun probeExtensionSupport(command: String, extensionPath: String): Boolean = try {
    val line = GeneralCommandLine(ParametersListUtil.parse(command))
        // The shell's environment, not the IDE's: a Pi installed through npm or a
        // version manager is on the terminal's PATH but usually not on the PATH an
        // IDE launched from the Dock inherits, and the terminal is where Pi runs.
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        // The real extension, so this also proves Pi can load ours. The path must
        // follow the flag: Pi would otherwise read `--help` as the extension to load
        // and never print help at all, which is how this probe first got it wrong.
        .withParameters("--extension")
        .withParameters(extensionPath)
        .withParameters("--help")
    val output = CapturingProcessHandler(line).runProcess(20_000)
    (output.stdout + output.stderr).contains("--extension")
} catch (e: Exception) {
    Logger.getInstance("com.chengyayu.pilauncher.infrastructure.PiStatusExtension")
        .info("Could not ask Pi about --extension: ${e.message}")
    false
}
