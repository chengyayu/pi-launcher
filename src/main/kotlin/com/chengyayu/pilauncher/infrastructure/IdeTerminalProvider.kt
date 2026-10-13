package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.beans.PropertyChangeListener

/**
 * [PiTerminalProvider] backed by the IDE's Terminal tool window.
 *
 * Tabs are created through the engine-agnostic `createShellWidget` and remembered
 * by name, so several Pi tabs can coexist and be focused independently; the
 * reflective access lives in [ReflectiveTerminal] (`docs/adr/0002`).
 */
class IdeTerminalProvider(private val project: Project) : PiTerminalProvider {

    private val tabs = mutableMapOf<String, Content>()

    private var selectionListener: ((String) -> Unit)? = null
    private var closeListener: ((String) -> Unit)? = null
    private var renameListener: ((String, String) -> Unit)? = null
    private var listenersInstalled = false

    override fun onTabSelected(listener: (String) -> Unit) {
        selectionListener = listener
    }

    override fun onTabClosed(listener: (String) -> Unit) {
        closeListener = listener
    }

    override fun onTabRenamed(listener: (String, String) -> Unit) {
        renameListener = listener
    }

    override fun createTerminalTab(workingDirectory: String, tabName: String): PiTerminal {
        val widget = createShellWidget(workingDirectory, tabName)
        if (widget == null) error("the Terminal tool window created no widget for $tabName")

        installTabListeners()
        contentManager()?.contents
            ?.lastOrNull { it.displayName == tabName }
            ?.let { content ->
                tabs[tabName] = content
                content.addPropertyChangeListener(renameReporter)
            }

        focusTerminalTab(tabName)
        return ReflectiveTerminal(widget)
    }

    override fun focusTerminalTab(tabName: String) {
        installTabListeners()
        val toolWindow = toolWindow() ?: return
        val content = tabs[tabName] ?: return

        toolWindow.show {
            toolWindow.contentManager.setSelectedContent(content, true)
            content.component.requestFocusInWindow()
        }
    }

    override fun existingTabNames(): Set<String> =
        contentManager()?.contents?.map { it.displayName }?.toSet() ?: emptySet()

    /**
     * A renamed tab keeps its `Content` and changes only its display name, so both
     * the lookup table and the registry have to follow it (`Content.PROP_DISPLAY_NAME`
     * is what the platform's own rename actions fire).
     */
    private val renameReporter = PropertyChangeListener { event ->
        if (event.propertyName != Content.PROP_DISPLAY_NAME) return@PropertyChangeListener

        val oldName = event.oldValue as? String ?: return@PropertyChangeListener
        val newName = event.newValue as? String ?: return@PropertyChangeListener
        if (oldName == newName) return@PropertyChangeListener

        tabs.remove(oldName)?.let { tabs[newName] = it }
        renameListener?.invoke(oldName, newName)
    }

    /**
     * Tracks which Pi tab the user is on, and drops tabs the user closed. Only
     * tabs this provider created are reported, so foreign terminal tabs are
     * ignored.
     */
    private fun installTabListeners() {
        if (listenersInstalled) return
        val contentManager = contentManager() ?: return

        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                selectionListener?.invoke(event.content.displayName)
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                // Found by identity: after a rename the display name is the new one,
                // but the lookup table is what decides whether this tab was ours.
                val tabName = tabs.entries.firstOrNull { it.value === event.content }?.key ?: return
                tabs.remove(tabName)
                closeListener?.invoke(tabName)
            }
        })
        listenersInstalled = true
    }

    /** `createShellWidget` is the engine-agnostic API; older platforms have only the latter. */
    private fun createShellWidget(workingDirectory: String, tabName: String): Any? {
        val manager = terminalToolWindowManager()
        manager.javaClass.methodOrNull(
            "createShellWidget",
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )?.let {
            return it.invoke(manager, workingDirectory, tabName, false, false)
        }

        return manager.javaClass
            .methodOrNull("createLocalShellWidget", String::class.java, String::class.java)
            ?.invoke(manager, workingDirectory, tabName)
    }

    private fun contentManager() = toolWindow()?.contentManager

    private fun toolWindow(): ToolWindow? =
        ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW)

    private fun terminalToolWindowManager(): Any {
        val managerClass = Class.forName(TERMINAL_MANAGER_CLASS)
        return managerClass.getMethod("getInstance", Project::class.java).invoke(null, project)
            ?: error("TerminalToolWindowManager.getInstance returned null")
    }

    private companion object {
        const val TERMINAL_MANAGER_CLASS = "org.jetbrains.plugins.terminal.TerminalToolWindowManager"
        const val TERMINAL_TOOL_WINDOW = "Terminal"
    }
}
