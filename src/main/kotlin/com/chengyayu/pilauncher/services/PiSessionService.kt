package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.chengyayu.pilauncher.infrastructure.IdePiNotifier
import com.chengyayu.pilauncher.infrastructure.IdeTerminalProvider
import com.chengyayu.pilauncher.settings.PiSettings
import com.chengyayu.pilauncher.settings.toLaunchOptions

/**
 * Project-level entry point to the Pi session.
 *
 * IntelliJ only supports a `(Project)` (or `(Project, CoroutineScope)`)
 * constructor for services, so this stays a thin adapter and the actual logic
 * lives in [PiSession].
 */
@Service(Service.Level.PROJECT)
class PiSessionService(private val project: Project) : Disposable {

    init {
        // Project services are created lazily. PiFileWatcher subscribes to the
        // session topic in its initializer, so it must be touched here or it is
        // never instantiated and file watching silently never starts.
        PiFileWatcher.getInstance(project)
    }

    private val session = PiSession(
        workingDirectory = { project.basePath ?: System.getProperty("user.home") },
        publishStatus = PiSessionState.publisherFor(project),
        terminals = IdeTerminalProvider(project),
        notifier = IdePiNotifier(project),
        launchOptions = { PiSettings.getInstance().state.toLaunchOptions() }
    )

    val status: PiSessionStatus get() = session.status

    fun isRunning(): Boolean = session.isRunning()

    fun launch() = session.launch()

    fun submitText(text: String) = session.submitText(text)

    fun insertText(text: String) = session.insertText(text)

    fun focus() = session.focus()

    fun reset() = session.reset()

    override fun dispose() = session.dispose()

    companion object {
        const val TAB_NAME = PiSession.TAB_NAME

        fun getInstance(project: Project): PiSessionService = project.service()
    }
}
