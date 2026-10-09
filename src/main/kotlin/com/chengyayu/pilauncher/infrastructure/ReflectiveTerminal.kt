package com.chengyayu.pilauncher.infrastructure

import com.intellij.openapi.diagnostic.Logger
import javax.swing.Timer

/**
 * Adapter over a terminal widget (`TerminalWidget`), reached by reflection so both
 * the classic and the block engine work (`docs/adr/0002`).
 */
internal class ReflectiveTerminal(private val widget: Any) : PiTerminal {

    private val logger = Logger.getInstance(ReflectiveTerminal::class.java)

    @Volatile
    private var probeLogged = false

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

    override fun send(text: String, submit: Boolean) {
        if (deliver(text, submit) != Delivery.NO_PATH) return
        retry(text, submit, attemptsLeft = SEND_RETRIES)
    }

    /**
     * [Delivery.NO_PATH] is the only failure that may be retried: it means no
     * delivery mechanism existed yet. A call that *threw* may have gone through,
     * so it is reported and left alone rather than submitted twice.
     */
    private fun deliver(text: String, submit: Boolean): Delivery = try {
        val sent = if (submit) submitCommand(text) else typeText(text)
        if (sent) Delivery.DELIVERED else Delivery.NO_PATH
    } catch (e: Exception) {
        logger.warn("Failed to send text to the Pi terminal: ${e.message}")
        Delivery.DELIVERED
    }

    private fun retry(text: String, submit: Boolean, attemptsLeft: Int) {
        if (attemptsLeft == 0) {
            logger.warn("Pi terminal never became ready for input: $text")
            return
        }

        Timer(SEND_RETRY_DELAY_MILLIS) {
            if (deliver(text, submit) == Delivery.NO_PATH) retry(text, submit, attemptsLeft - 1)
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

    private enum class Delivery { DELIVERED, NO_PATH }

    private companion object {
        const val GET_PROCESS = "getProcess"
        const val MAX_PROXY_DEPTH = 4
        const val SEND_RETRY_DELAY_MILLIS = 200
        const val SEND_RETRIES = 5
        val SUBMIT_METHODS = listOf("sendCommandToExecute", "executeCommand")
    }
}
