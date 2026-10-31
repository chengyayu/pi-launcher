package com.chengyayu.pilauncher.infrastructure

import com.chengyayu.pilauncher.domain.PiSessionReport
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
 * Each session gets its own copy of the extension in a private directory, so the copy finds
 * the report it writes through `__dirname` without any environment variable. Everything here
 * is best effort: a Pi that cannot load extensions costs a Session Status, never a session.
 */
internal class PiStatusExtension(
    private val piCommand: () -> String,
    private val baseDirectory: Path,
    private val detectSupport: (extensionPath: String) -> Boolean = { probeExtensionSupport(piCommand(), it) },
    /** Shared between projects: asking Pi costs a subprocess. */
    private val probeCache: PiStatusProbeCache = PiStatusProbeCache.UNSHARED
) : PiStatusChannelProvider {

    private val logger = Logger.getInstance(PiStatusExtension::class.java)

    /** The extension source, read once. */
    private val extensionText: String? by lazy { readResource() }

    /**
     * Never guessed: passing a flag Pi does not know would stop it from starting at all.
     * Null until the probe answers.
     */
    @Volatile
    private var extensionSupported: Boolean? = null

    /** Called when the project opens, so a session started by hand a moment later has an answer. */
    fun probe() {
        if (extensionSupported != null) return

        discardStaleSessions()

        val startedAt = System.currentTimeMillis()
        extensionSupported = probeCache.answerOrProbe(::askPi)
        logger.info(
            "Pi status extension supported: $extensionSupported " +
                "(asked in ${System.currentTimeMillis() - startedAt} ms" +
                if (System.currentTimeMillis() - startedAt == 0L) ", from cache)" else ")"
        )
    }

    /** Runs only when no project has asked yet: it stages a copy and spends a subprocess. */
    private fun askPi(): Boolean {
        val probeExtension = prepareProbeExtension()
        if (probeExtension == null) {
            logger.info("The plugin's own status extension resource is missing")
            return false
        }

        return detectSupport(probeExtension)
    }

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
            // A session started before the probe answers reports Inferred Status rather than waiting.
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
        override fun read(): PiSessionReport? = try {
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
        val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9._-]")
    }
}

private const val EXTENSION_FLAG = "--extension"
private const val HELP_FLAG = "--help"
private const val PROBE_TIMEOUT_MILLIS = 20_000

/**
 * Asks Pi itself whether it knows `--extension`, because a flag it rejects would stop a session
 * from starting. A failure here is an answer too: the command is the user's and may not be on PATH.
 */
private fun probeExtensionSupport(command: String, extensionPath: String): Boolean = try {
    val line = GeneralCommandLine(ParametersListUtil.parse(command))
        // The terminal's environment, not the IDE's: an npm or version-manager Pi is on the
        // terminal's PATH but usually not on the PATH an IDE launched from the Dock inherits.
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        // The extension must follow the flag, or Pi reads `--help` as the extension and never prints it.
        .withParameters(EXTENSION_FLAG)
        .withParameters(extensionPath)
        .withParameters(HELP_FLAG)
    val output = CapturingProcessHandler(line).runProcess(PROBE_TIMEOUT_MILLIS)
    (output.stdout + output.stderr).contains("--extension")
} catch (e: Exception) {
    Logger.getInstance("com.chengyayu.pilauncher.infrastructure.PiStatusExtension")
        .info("Could not ask Pi about --extension: ${e.message}")
    false
}
