package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile

/**
 * Which [PiSessionHost] a project uses, while the editor-tab Host is being proven
 * (`docs/adr/0007`).
 *
 * Deliberately **not** a setting: it exists so one person can flip between two Hosts on
 * a real IDE, and it is deleted together with the Host that loses. A user-facing choice
 * would be a promise that both are equally good, which is exactly what has not been
 * established yet.
 */
object PiHostChoice {

    /** `-Dpilauncher.session.host=editor`, as a VM option. */
    const val PROPERTY = "pilauncher.session.host"

    /** The same switch, as an environment variable, which `runIde` passes through. */
    const val ENVIRONMENT = "PI_LAUNCHER_HOST"

    /** The same switch, for Search Everywhere → Registry. */
    const val REGISTRY_KEY = "pilauncher.session.host.editor"

    private const val EDITOR = "editor"

    /**
     * A missing or malformed value means the Terminal tool window, which is the Host
     * that has been shipping. Reading the registry is guarded: an undeclared key must
     * never be able to stop a launch.
     */
    fun editorRequested(
        property: String? = System.getProperty(PROPERTY) ?: System.getenv(ENVIRONMENT),
        registry: () -> Boolean = { Registry.`is`(REGISTRY_KEY, false) }
    ): Boolean = property?.equals(EDITOR, ignoreCase = true) == true ||
        runCatching(registry).getOrDefault(false)
}

/**
 * The editor-tab [PiSessionHost]: a Pi Session occupies a full editor tab instead of a
 * narrow tab in the Terminal tool window (`docs/adr/0006`).
 *
 * The widget is created without any Terminal-tool-window `Content`, which is what the
 * platform itself does when it restores a terminal session into an editor
 * (`org.jetbrains.plugins.terminal.vfs.TerminalSessionEditor`): the runner hands back a
 * `TerminalWidget`, the editor shows `widget.getComponent()`, and the widget's lifetime
 * is the editor's disposable rather than a tab's.
 *
 * Reached reflectively, for the reason in `docs/adr/0002`: the plugin declares no upper
 * bound on supported IDE builds, so a renamed terminal internal has to cost a log line
 * rather than a `NoClassDefFoundError`.
 */
class EditorTabSessionHost(private val project: Project) : PiSessionHost {

    private val logger = Logger.getInstance(EditorTabSessionHost::class.java)

    /** The files this Host opened, by their name. */
    private val opened = mutableMapOf<String, PiTerminalVirtualFile>()

    private var selectionListener: ((String) -> Unit)? = null
    private var closeListener: ((String) -> Unit)? = null
    private var listenersInstalled = false

    override fun onTabSelected(listener: (String) -> Unit) {
        selectionListener = listener
    }

    override fun onTabClosed(listener: (String) -> Unit) {
        closeListener = listener
    }

    /**
     * An editor tab is named by its file, and the platform offers no inline rename for
     * one, so this Host never reports a rename. The registry keeps the name the session
     * was opened under, and renaming a parked session stays the dashboard's job.
     */
    override fun onTabRenamed(listener: (oldName: String, newName: String) -> Unit) = Unit

    override fun open(workingDirectory: String, name: String): PiTerminal {
        val lifetime = Disposer.newDisposable("Pi session $name")
        val widget = try {
            createWidget(workingDirectory, lifetime)
        } catch (e: Exception) {
            Disposer.dispose(lifetime)
            throw e
        }

        val file = PiTerminalVirtualFile(name, ReflectiveTerminal(widget), widget, lifetime)
        opened[name] = file
        installListeners()

        openFileInEditor(project, file, focus = true)
        logger.info("Pi session $name opened in an editor tab")
        return file.terminal
    }

    override fun focus(name: String) {
        val file = opened[name] ?: return
        if (!file.isValid) {
            opened.remove(name)
            return
        }
        openFileInEditor(project, file, focus = true)
    }

    override fun shownNames(): Set<String> = opened.filterValues { it.isValid }.keys

    /**
     * A terminal widget with no `Content` behind it, the way the platform restores one
     * into an editor: `startShellTerminalWidget` is the Content-free half of the
     * `createShellWidget` the Terminal Host uses.
     */
    private fun createWidget(workingDirectory: String, lifetime: Disposable): Any {
        val runner = Class.forName(RUNNER_CLASS)
            .getConstructor(Project::class.java)
            .newInstance(project)

        val optionsClass = Class.forName(STARTUP_OPTIONS_CLASS)
        val options = Class.forName(STARTUP_OPTIONS_FACTORY)
            .methodOrNull("shellStartupOptions", String::class.java)
            ?.invoke(null, workingDirectory)
            ?: error("$STARTUP_OPTIONS_FACTORY no longer offers shellStartupOptions(String)")

        return runner.javaClass.methodOrNull(
            "startShellTerminalWidget",
            Disposable::class.java,
            optionsClass,
            Boolean::class.javaPrimitiveType
        )?.invoke(runner, lifetime, options, false)
            ?: error("$RUNNER_CLASS no longer offers startShellTerminalWidget")
    }

    /**
     * Closes and selections come from the editor manager. A closed editor has already
     * disposed the widget's lifetime by then, so the run is over before this hears it.
     */
    private fun installListeners() {
        if (listenersInstalled) return

        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    if (file !is PiTerminalVirtualFile) return
                    opened.remove(file.name)
                    closeListener?.invoke(file.name)
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile ?: return
                    if (file !is PiTerminalVirtualFile) return
                    selectionListener?.invoke(file.name)
                }
            }
        )
        listenersInstalled = true
    }

    private companion object {
        const val RUNNER_CLASS = "org.jetbrains.plugins.terminal.LocalBlockTerminalRunner"
        const val STARTUP_OPTIONS_CLASS = "org.jetbrains.plugins.terminal.ShellStartupOptions"
        const val STARTUP_OPTIONS_FACTORY = "org.jetbrains.plugins.terminal.ShellStartupOptionsKt"
    }
}
