package com.chengyayu.pilauncher.infrastructure

import com.intellij.ide.dnd.DnDSupport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComponentContainer
import javax.swing.JComponent
import javax.swing.Timer

/**
 * Adapter over a terminal widget (`TerminalWidget`), reached by reflection so both
 * the classic and the block engine work (`docs/adr/0002`).
 */
internal class ReflectiveTerminal(private val widget: Any) : PiTerminal {

    private val logger = Logger.getInstance(ReflectiveTerminal::class.java)

    @Volatile
    private var probeLogged = false

    /** Set by the session once it exists; a drop before that goes straight to the pty. */
    @Volatile
    private var dropListener: ((List<String>) -> Unit)? = null

    init {
        installDropTarget()
    }

    override fun onFilesDropped(listener: (List<String>) -> Unit) {
        dropListener = listener
    }

    /**
     * Takes over file drops on the terminal's own component.
     *
     * The platform installs its handler on the whole tool window, and that handler
     * decides between "insert the path" and "open a terminal here" by the drop
     * point - a drop that lands on the tab strip wins the latter and opens a new
     * tab. A target on the content wins over the tool window's one, so a Pi tab
     * always gets the drop (`docs/adr/0008`).
     */
    private fun installDropTarget() {
        val component = (widget as? ComponentContainer)?.component ?: widget as? JComponent ?: run {
            logger.warn("Pi terminal exposes no component to accept file drops")
            return
        }

        DnDSupport.createBuilder(component)
            .setDropHandler { event ->
                val attached = event.attachedObject
                val paths = try {
                    DroppedFiles.pathsOf(attached)
                } catch (e: Exception) {
                    logger.warn("Failed to read the files in a drop: ${e.message}")
                    emptyList()
                }
                if (paths.isNotEmpty()) {
                    deliverDropped(paths)
                    return@setDropHandler
                }

                // Text kept the platform's "insert it" meaning, so take it over too.
                val text = DroppedFiles.textOf(attached)?.takeIf { it.isNotBlank() } ?: return@setDropHandler
                send(text, submit = false) { }
            }
            .enableAsNativeTarget()
            .disableAsSource()
            .install()
    }

    private fun deliverDropped(paths: List<String>) {
        val listener = dropListener
        if (listener != null) {
            listener(paths)
            return
        }

        // No session yet: the paths are still what the user meant, so type them.
        send(paths.joinToString(" ") + " ", submit = false) { }
    }

    /**
     * The engine's own liveness APIs are unusable (`isConnected()` answers
     * `false` for pty4j local terminals), so the shell's process tree is read
     * through the tty connector instead.
     */
    override fun isForegroundCommandRunning(): Boolean? {
        val process = realConnector()?.callOrNull(GET_PROCESS) as? Process ?: return null

        val pid = try {
            process.pid()
        } catch (_: UnsupportedOperationException) {
            return null
        }

        val handle = ProcessHandle.of(pid).orElse(null)
        logProbe(pid, handle)
        return handle?.children()?.anyMatch { it.isAlive } ?: false
    }

    override fun send(text: String, submit: Boolean, onDelivered: () -> Unit) {
        if (deliver(text, submit)) {
            onDelivered()
            return
        }
        retry(text, submit, attemptsLeft = SEND_RETRIES, onDelivered)
    }

    /**
     * Answered `false` only when no delivery mechanism existed yet, which is the
     * one failure that may be retried. A call that *threw* may have gone through,
     * so it counts as delivered and is left alone rather than submitted twice.
     */
    private fun deliver(text: String, submit: Boolean): Boolean = try {
        if (submit) submitCommand(text) else typeText(text)
    } catch (e: Exception) {
        logger.warn("Failed to send text to the Pi terminal: ${e.message}")
        true
    }

    /**
     * Gives up eventually rather than retrying forever, and always reports the
     * outcome: a caller holding input back for this send must be released even
     * when the terminal never becomes ready.
     */
    private fun retry(text: String, submit: Boolean, attemptsLeft: Int, onDelivered: () -> Unit) {
        if (attemptsLeft == 0) {
            logger.warn("Pi terminal never became ready for input: $text")
            onDelivered()
            return
        }

        Timer(SEND_RETRY_DELAY_MILLIS) {
            if (deliver(text, submit)) onDelivered() else retry(text, submit, attemptsLeft - 1, onDelivered)
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun submitCommand(text: String): Boolean {
        val method = SUBMIT_METHODS.firstNotNullOfOrNull {
            widget.javaClass.methodOrNull(it, String::class.java)
        }
        if (method != null) {
            method.invoke(widget, text)
            return true
        }

        return writeToConnector(text) && writeToConnector("\r")
    }

    private fun typeText(text: String): Boolean {
        val starter = widget.callOrNull("getTerminalStarter")
        val sendString = starter?.javaClass
            ?.methodOrNull("sendString", String::class.java, Boolean::class.javaPrimitiveType)

        if (sendString != null) {
            sendString.invoke(starter, text, false)
            return true
        }

        return writeToConnector(text)
    }

    private fun writeToConnector(text: String): Boolean {
        val connector = ttyConnector() ?: return false
        val write = connector.javaClass.methodOrNull("write", String::class.java) ?: return false
        write.invoke(connector, text)
        return true
    }

    private fun ttyConnector(): Any? =
        widget.callOrNull("getTtyConnector")
            ?: widget.callOrNull("getProcessTtyConnector")
            ?: widget.callOrNull("getTtyConnectorAccessor")?.callOrNull("getTtyConnector")

    /** The block terminal wraps the real connector in an RD proxy. */
    private fun realConnector(): Any? {
        var connector = ttyConnector()
        repeat(MAX_PROXY_DEPTH) {
            val inner = connector?.callOrNull("getConnector") ?: return connector
            if (inner === connector) return connector
            connector = inner
        }
        return connector
    }

    private fun logProbe(pid: Long, handle: ProcessHandle?) {
        if (probeLogged) return
        probeLogged = true

        val children = handle?.children()?.map { it.info().command().orElse("?") }?.toList()
        logger.debug("Pi liveness probe: pid=$pid shell=${handle != null} children=$children")
    }

    private companion object {
        const val GET_PROCESS = "getProcess"
        const val MAX_PROXY_DEPTH = 4
        const val SEND_RETRY_DELAY_MILLIS = 200
        const val SEND_RETRIES = 5
        val SUBMIT_METHODS = listOf("sendCommandToExecute", "executeCommand")
    }
}
