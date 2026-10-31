package com.chengyayu.pilauncher.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.infrastructure.openFileInEditor
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
 * Watches for files Pi rewrites. Only the optional auto-open uses them (off by default,
 * debounced, filtered, capped, on the EDT): a session rewrites dozens of files, so diffs and
 * per-file notifications would be unusable. Review the work with `git diff`.
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

    override fun sessionCountChanged(runningCount: Int) {
        if (runningCount > 0) {
            startWatching()
        } else {
            stopWatching()
        }
    }

    /** On the EDT. While indexing the editor must not be touched, so the paths are handed back for a retry. */
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

    /** At most [MAX_AUTO_OPEN_FILES] per batch: a project-wide rewrite must not flood the editor. */
    private fun openChangedFiles(paths: List<String>) {
        if (!isWatching) return

        val fileEditorManager = FileEditorManager.getInstance(project)
        var opened = 0
        for (path in paths) {
            if (opened >= MAX_AUTO_OPEN_FILES) break

            val file: VirtualFile = LocalFileSystem.getInstance().findFileByPath(path) ?: continue
            if (!file.isValid) continue
            if (fileEditorManager.isFileOpen(file)) continue

            openFileInEditor(project, file, focus = false)
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
        /** Wait for Pi to stop writing before touching the editor. */
        private const val AUTO_OPEN_DEBOUNCE_MS = 600

        private const val MAX_AUTO_OPEN_FILES = 10

        fun getInstance(project: Project): PiFileWatcher = project.service()
    }
}
