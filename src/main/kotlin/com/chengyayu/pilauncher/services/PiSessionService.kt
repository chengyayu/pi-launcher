package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.chengyayu.pilauncher.domain.PiCommandLine
import com.chengyayu.pilauncher.domain.PiSessionReadout
import com.chengyayu.pilauncher.infrastructure.IdePiNotifier
import com.chengyayu.pilauncher.infrastructure.IdeTerminalProvider
import com.chengyayu.pilauncher.infrastructure.PiStatusExtension
import com.chengyayu.pilauncher.infrastructure.projectKey
import java.nio.file.Path
import com.chengyayu.pilauncher.settings.PiSettings
import com.chengyayu.pilauncher.settings.toLaunchOptions

/**
 * Project-facing entry point; the logic lives in [PiSessionRegistry], because a
 * platform service may only be constructed from a `Project`.
 */
@Service(Service.Level.PROJECT)
class PiSessionService(private val project: Project) : Disposable {

    /**
     * Extracted once per project, outside the project so a stray file cannot be
     * committed, and under a key of its own so clearing this project's leftovers can
     * never touch another project's running sessions.
     */
    private val statusChannels = PiStatusExtension(
        piCommand = { piCommand() },
        baseDirectory = Path.of(PathManager.getTempPath(), "pi-launcher", projectKey(project)),
        probeCache = ApplicationPiStatusProbeCache.getInstance()
    )

    init {
        // PiFileWatcher subscribes to the count topic in its initializer, and
        // project services are created lazily, so it must be touched here.
        PiFileWatcher.getInstance(project)

        // Finding out whether Pi can host the status extension costs a subprocess,
        // so it happens in the background. A session launched before the answer
        // arrives simply reports Inferred Status.
        ApplicationManager.getApplication().executeOnPooledThread { statusChannels.probe() }
    }

    private val registry = PiSessionRegistry(
        workingDirectory = { project.basePath ?: System.getProperty("user.home") },
        // Settings are a snapshot taken when a session starts.
        startCommand = { extensionPath ->
            PiCommandLine.render(piLaunchOptions(), extensionPath)
        },
        terminals = IdeTerminalProvider(project),
        notifier = IdePiNotifier(project),
        publishCount = PiSessionListener.publisherFor(project),
        publishReadouts = PiSessionStatusListener.publisherFor(project),
        statusChannels = statusChannels
    )

    private fun piLaunchOptions() = PiSettings.getInstance().state.toLaunchOptions()

    private fun piCommand(): String = piLaunchOptions().command

    val runningCount: Int get() = registry.runningCount

    /** Every live session, for the status bar and the Session Dashboard. */
    val readouts: List<PiSessionReadout> get() = registry.readouts

    fun isRunning(): Boolean = registry.isRunning()

    fun launch() = registry.launch()

    fun newSession() = registry.newSession()

    fun submitText(text: String) = registry.submitText(text)

    fun insertText(text: String) = registry.insertText(text)

    fun focus() = registry.focus()

    /** Bring the session in [tabName] to the front, if it is still running. */
    fun focus(tabName: String) = registry.focus(tabName)

    override fun dispose() = registry.dispose()

    companion object {
        const val BASE_TAB_NAME = PiSessionRegistry.BASE_TAB_NAME

        fun getInstance(project: Project): PiSessionService = project.service()
    }
}
