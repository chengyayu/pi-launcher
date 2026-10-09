package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.chengyayu.pilauncher.domain.PiCommandLine
import com.chengyayu.pilauncher.infrastructure.IdePiNotifier
import com.chengyayu.pilauncher.infrastructure.IdeTerminalProvider
import com.chengyayu.pilauncher.settings.PiSettings
import com.chengyayu.pilauncher.settings.toLaunchOptions

/**
 * Project-facing entry point; the logic lives in [PiSessionRegistry], because a
 * platform service may only be constructed from a `Project`.
 */
@Service(Service.Level.PROJECT)
class PiSessionService(private val project: Project) : Disposable {

    init {
        // PiFileWatcher subscribes to the count topic in its initializer, and
        // project services are created lazily, so it must be touched here.
        PiFileWatcher.getInstance(project)
    }

    private val registry = PiSessionRegistry(
        workingDirectory = { project.basePath ?: System.getProperty("user.home") },
        // Settings are a snapshot taken when a session starts.
        startCommand = { PiCommandLine.render(PiSettings.getInstance().state.toLaunchOptions()) },
        terminals = IdeTerminalProvider(project),
        notifier = IdePiNotifier(project),
        publishCount = PiSessionListener.publisherFor(project)
    )

    val runningCount: Int get() = registry.runningCount

    fun isRunning(): Boolean = registry.isRunning()

    fun launch() = registry.launch()

    fun newSession() = registry.newSession()

    fun submitText(text: String) = registry.submitText(text)

    fun insertText(text: String) = registry.insertText(text)

    fun focus() = registry.focus()

    override fun dispose() = registry.dispose()

    companion object {
        const val BASE_TAB_NAME = PiSessionRegistry.BASE_TAB_NAME

        fun getInstance(project: Project): PiSessionService = project.service()
    }
}
