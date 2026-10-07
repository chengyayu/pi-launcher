package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.lang.reflect.Method
import javax.swing.JComponent

/**
 * [PiTerminalProvider] backed by the IDE's Terminal tool window.
 *
 * The terminal API is reached through reflection because it has changed shape
 * across platform versions; keeping all of that here means the rest of the plugin
 * never sees a `Class.forName`.
 */
class IdeTerminalProvider(private val project: Project) : PiTerminalProvider {

    @Volatile
    private var created: PiTerminal? = null

    override fun currentTerminal(): PiTerminal? = created

    override fun createTerminalTab(workingDirectory: String, tabName: String): PiTerminal {
        val manager = terminalToolWindowManager()
        val widget = manager.javaClass
            .getMethod("createLocalShellWidget", String::class.java, String::class.java)
            .invoke(manager, workingDirectory, tabName)
            ?: error("createLocalShellWidget returned null")

        val terminal = ReflectiveTerminal(widget)
        created = terminal
        focusTerminalTab(tabName)
        return terminal
    }

    override fun focusTerminalTab(tabName: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW)
            ?: return

        toolWindow.show {
            val contentManager = toolWindow.contentManager
            contentManager.contents
                .firstOrNull { it.displayName == tabName }
                ?.let { contentManager.setSelectedContent(it, true) }

            created?.component?.requestFocusInWindow()
        }
    }

    private fun terminalToolWindowManager(): Any {
        val managerClass = Class.forName(TERMINAL_MANAGER_CLASS)
        return managerClass.getMethod("getInstance", Project::class.java).invoke(null, project)
            ?: error("TerminalToolWindowManager.getInstance returned null")
    }

    /**
     * Thin adapter over `ShellTerminalWidget`.
     *
     * Members are resolved by name so that a platform rename degrades to a logged
     * warning instead of a crash at the call site.
     */
    private class ReflectiveTerminal(private val widget: Any) : PiTerminal {

        private val logger = Logger.getInstance(ReflectiveTerminal::class.java)

        @Volatile
        private var diagnosticsLogged = false

        override val component: JComponent by lazy {
            widget.javaClass.methodOrNull("getComponent")!!.invoke(widget) as JComponent
        }

        override fun isAttached(): Boolean = component.parent != null

        /**
         * Asks whether the shell behind this tab currently has a child process.
         *
         * The obvious APIs cannot answer this:
         *  - `TtyConnector.isConnected()` is `process.isAlive()`, and for pty4j
         *    local terminals that delegates to a short-lived spawn helper, so it
         *    is always false;
         *  - `ShellTerminalWidget.hasRunningCommands()` returns early with false
         *    whenever `isConnected()` is false.
         *
         * `pid()` however is the real child pid, so the process tree is inspected
         * directly instead.
         */
        override fun isForegroundCommandRunning(): Boolean? {
            val connector = processConnector()
            val process = connector?.let { call(it, "getProcess") } as? Process

            if (process == null) {
                reportOnce("connector=${connector?.javaClass?.simpleName} process=null")
                return null
            }

            val pid = try {
                process.pid()
            } catch (_: UnsupportedOperationException) {
                reportOnce("pid unsupported on ${process.javaClass.simpleName}")
                return null
            }

            val handle = ProcessHandle.of(pid).orElse(null)

            reportOnce(
                buildString {
                    append("connector=${connector.javaClass.simpleName}")
                    append(" process=${process.javaClass.simpleName}")
                    append(" pid=$pid")
                    append(" processAlive=${process.isAlive}")
                    append(" shellPresent=${handle != null}")
                    append(" children=${handle?.children()?.map { it.info().command().orElse("?") }?.toList()}")
                }
            )

            // No handle means the shell itself is gone.
            if (handle == null) return false

            return handle.children().anyMatch { it.isAlive }
        }

        private fun reportOnce(details: String) {
            if (diagnosticsLogged) return
            diagnosticsLogged = true
            logger.debug("Pi terminal liveness probe: $details")
        }

        override fun send(text: String, submit: Boolean) {
            try {
                if (submit) {
                    widget.javaClass.getMethod("executeCommand", String::class.java).invoke(widget, text)
                } else {
                    val starter = call(widget, "getTerminalStarter") ?: return
                    starter.javaClass
                        .getMethod("sendString", String::class.java, Boolean::class.javaPrimitiveType)
                        .invoke(starter, text, false)
                }
            } catch (e: Exception) {
                logger.warn("Failed to send text to the Pi terminal: ${e.message}")
            }
        }

        private fun processConnector(): Any? =
            call(widget, "getProcessTtyConnector") ?: call(widget, "getTtyConnector")

        private fun call(target: Any, name: String, vararg params: Class<*>): Any? = try {
            target.javaClass.methodOrNull(name, *params)?.invoke(target)
        } catch (e: Exception) {
            logger.debug("$name unavailable", e)
            null
        }

        private fun Class<*>.methodOrNull(name: String, vararg params: Class<*>): Method? = try {
            getMethod(name, *params)
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private companion object {
        const val TERMINAL_MANAGER_CLASS = "org.jetbrains.plugins.terminal.TerminalToolWindowManager"
        const val TERMINAL_TOOL_WINDOW = "Terminal"
    }
}
