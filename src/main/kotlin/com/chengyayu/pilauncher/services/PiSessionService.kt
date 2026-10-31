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
import com.chengyayu.pilauncher.infrastructure.ToolWindowSessionHost
import com.chengyayu.pilauncher.infrastructure.PiSessionHost
import com.chengyayu.pilauncher.infrastructure.PiStatusExtension
import com.chengyayu.pilauncher.infrastructure.projectKey
import java.nio.file.Path
import com.chengyayu.pilauncher.settings.PiSettings
import com.chengyayu.pilauncher.settings.toLaunchOptions

/** Project-facing entry point; the logic lives in [PiSessionRegistry]. */
@Service(Service.Level.PROJECT)
class PiSessionService(private val project: Project) : Disposable {

    /**
     * Outside the project so a stray file cannot be committed, and keyed per project so
     * clearing this project's leftovers cannot touch another project's sessions.
     */
    private val statusChannels = PiStatusExtension(
        piCommand = { piCommand() },
        baseDirectory = Path.of(PathManager.getTempPath(), "pi-launcher", projectKey(project)),
        probeCache = ApplicationPiStatusProbeCache.getInstance()
    )

    private val registry = PiSessionRegistry(
        workingDirectory = { project.basePath ?: System.getProperty("user.home") },
        // Settings are a snapshot taken when a session starts.
        startCommand = { extensionPath ->
            PiCommandLine.render(piLaunchOptions(), extensionPath)
        },
        host = ToolWindowSessionHost(project),
        notifier = IdePiNotifier(project),
        publishCount = PiSessionListener.publisherFor(project),
        publishReadouts = PiSessionStatusListener.publisherFor(project),
        statusChannels = statusChannels
    )

    init {
        // PiFileWatcher subscribes in its initializer, and project services are created lazily.
        PiFileWatcher.getInstance(project)

        // Probing runs a subprocess, so it happens in the background; a session launched
        // before the answer arrives reports Inferred Status and has no identity to reopen.
        ApplicationManager.getApplication().executeOnPooledThread { statusChannels.probe() }
    }

    private fun piLaunchOptions() = PiSettings.getInstance().state.toLaunchOptions()

    private fun piCommand(): String = piLaunchOptions().command

    val runningCount: Int get() = registry.runningCount

    /** Every running session. */
    val readouts: List<PiSessionReadout> get() = registry.readouts

    fun isRunning(): Boolean = registry.isRunning()

    fun launch() = registry.launch()

    fun newSession() = registry.newSession()

    fun submitText(text: String) = registry.submitText(text)

    fun insertText(text: String) = registry.insertText(text)

    fun focus() = registry.focus()

    fun focus(tabName: String) = registry.focus(tabName)

    override fun dispose() = registry.dispose()

    companion object {
        const val BASE_TAB_NAME = PiSessionRegistry.BASE_TAB_NAME

        fun getInstance(project: Project): PiSessionService = project.service()
    }
}
