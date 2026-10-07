package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection
import com.chengyayu.pilauncher.infrastructure.PiVfsFilter
import com.chengyayu.pilauncher.settings.PiSettings
import com.chengyayu.pilauncher.util.PiChangeDebouncer

/**
 * Watches for file changes made by Pi while a session is running.
 *
 * The plugin deliberately opens **no** diff and shows **no** notification for
 * those changes: a single Pi session can rewrite dozens of files, and surfacing
 * them as editor tabs is unusable. Review the work with `git diff`.
 *
 * What remains is the optional "auto-open files" convenience, which is off by
 * default and, when enabled, is debounced, filtered, capped and run on the EDT.
 *
 * Watching follows the session: it starts and stops with [PiSessionListener],
 * so no other component has to remember to wire it up.
 */
@Service(Service.Level.PROJECT)
class PiFileWatcher(private val project: Project) : Disposable, PiSessionListener {

    private val logger = Logger.getInstance(PiFileWatcher::class.java)

    @Volatile
    private var isWatching = false
    private var connection: MessageBusConnection? = null

    private val debouncer = PiChangeDebouncer(
        delayMs = AUTO_OPEN_DEBOUNCE_MS,
        dispatch = { ApplicationManager.getApplication().invokeLater(it) },
        onFlush = ::onFlush
    )

    init {
        project.messageBus.connect(this).subscribe(PiSessionListener.TOPIC, this)
    }

    override fun sessionStatusChanged(status: PiSessionStatus) {
        when (status) {
            PiSessionStatus.RUNNING -> startWatching()
            PiSessionStatus.IDLE -> stopWatching()
        }
    }

    /**
     * Runs on the EDT. During indexing the editor model must not be touched, so
     * the paths are handed back for a later retry instead of being dropped.
     */
    private fun onFlush(paths: List<String>): List<String> {
        if (DumbService.getInstance(project).isDumb) return paths
        openChangedFiles(paths)
        return emptyList()
    }

    private fun startWatching() {
        if (isWatching) return
        if (!PiSettings.getInstance().state.autoOpenFiles) {
            logger.info("Pi file watching skipped: auto-open is disabled")
            return
        }

        isWatching = true
        debouncer.clear()

        connection?.disconnect()
        connection = project.messageBus.connect(this).also { conn ->
            conn.subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        if (!isWatching) return
                        events.forEach { event ->
                            if (event !is VFileContentChangeEvent) return@forEach
                            val file = event.file
                            if (!PiVfsFilter.isRelevantChange(project, file)) return@forEach
                            debouncer.submit(file.path)
                        }
                    }
                }
            )
        }

        logger.info("Pi file watcher started")
    }

    private fun stopWatching() {
        if (!isWatching) return
        isWatching = false
        connection?.disconnect()
        connection = null
        debouncer.clear()
    }

    /**
     * Opens at most [MAX_AUTO_OPEN_FILES] files per batch so a project-wide
     * rewrite cannot flood the editor with hundreds of tabs.
     */
    private fun openChangedFiles(paths: List<String>) {
        if (!isWatching) return

        val fileEditorManager = FileEditorManager.getInstance(project)
        var opened = 0
        for (path in paths) {
            if (opened >= MAX_AUTO_OPEN_FILES) break

            val file: VirtualFile = LocalFileSystem.getInstance().findFileByPath(path) ?: continue
            if (!file.isValid) continue
            if (fileEditorManager.isFileOpen(file)) continue

            fileEditorManager.openFile(file, false)
            opened++
        }
    }

    override fun dispose() {
        isWatching = false
        connection?.disconnect()
        connection = null
        debouncer.close()
    }

    companion object {
        /** Wait for pi to stop writing before touching the editor. */
        private const val AUTO_OPEN_DEBOUNCE_MS = 600

        /** Hard cap on auto-opened editors per write burst. */
        private const val MAX_AUTO_OPEN_FILES = 10

        fun getInstance(project: Project): PiFileWatcher = project.service()
    }
}
