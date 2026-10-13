package com.chengyayu.pilauncher.infrastructure

import com.jediterm.terminal.TerminalCustomCommandListener
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins what the terminal really does with OSC 7501.
 *
 * This test exists because ADR 0004 was built on the opposite assumption: reading
 * `JediEmulator.doProcessOsc` bytecode, the `processCustomCommand` call looked like
 * the `default` branch. It is not. The switch routes only **OSC 104 and OSC 1341**
 * there; every other code - 7501 included - falls through to `default`, which
 * returns "not handled" and drops the sequence. Verified on IC-243.21565.193 and on
 * the 2026.2 build the plugin also runs on, which are identical here.
 *
 * So no plugin-side listener can observe Pi's Session Status reports, and the status
 * has to come from a channel the plugin owns. Keep this test: it is the evidence for
 * that constraint, and the reason a future "just listen for the OSC" attempt must
 * come back here first.
 */
class ProgramStatusOscDispatchTest {

    @Test
    fun `drops OSC 7501 instead of forwarding it`() {
        val (terminal, received) = terminalWithListener()

        feed(terminal, "\u001B]7501;state=working\u001B\\")

        assertEquals(0, received.size, "OSC 7501 reached a plugin listener; ADR 0004 needs revisiting")
    }

    @Test
    fun `drops a full report payload too`() {
        val (terminal, received) = terminalWithListener()

        // base64("Approve bash?")
        feed(terminal, "\u001B]7501;state=blocked:app=pi:kind=permission:msg=QXBwcm92ZSBiYXNoPw==\u001B\\")

        assertEquals(0, received.size)
    }

    /** The two codes the fork does forward, and the payload shape they arrive in. */
    @Test
    fun `forwards OSC 1341 with the code stripped`() {
        val (terminal, received) = terminalWithListener()

        feed(terminal, "\u001B]1341;key=value\u001B\\")

        assertEquals(listOf("key=value"), received.single())
    }

    /**
     * OSC 7501 is dropped, not drawn, which is why nothing looks broken in the
     * terminal while the status stays invisible to the plugin.
     */
    @Test
    fun `does not draw OSC 7501 either`() {
        val buffer = TerminalTextBuffer(80, 24, StyleState())
        val (terminal, _) = terminalWithListener(buffer)

        feed(terminal, "\u001B]7501;state=done:app=pi\u001B\\")

        val drawn = (0 until buffer.screenLinesCount).joinToString("\n") { buffer.getLine(it).text }
        assertFalse(drawn.contains("7501"), "the report leaked onto the screen: $drawn")
        assertFalse(drawn.contains("state="), "the report leaked onto the screen: $drawn")
    }

    /**
     * The terminal writes into [buffer], which is why the caller passes it in: a test
     * that inspects a buffer of its own would assert nothing at all.
     */
    private fun terminalWithListener(
        buffer: TerminalTextBuffer = TerminalTextBuffer(80, 24, StyleState())
    ): Pair<JediTerminal, MutableList<List<String>>> {
        val terminal = JediTerminal(dummyDisplay(), buffer, StyleState())

        val received = mutableListOf<List<String>>()
        terminal.addCustomCommandListener(TerminalCustomCommandListener { received += it })

        return terminal to received
    }

    /** Runs [sequence] through a real emulator reading a real `TerminalDataStream`. */
    private fun feed(terminal: JediTerminal, sequence: String) {
        val emulator = JediEmulator(StaticDataStream(sequence), terminal)
        while (emulator.hasNext()) emulator.next()
    }

    private fun dummyDisplay(): TerminalDisplay = Proxy.newProxyInstance(
        TerminalDisplay::class.java.classLoader,
        arrayOf(TerminalDisplay::class.java)
    ) { _, method, _ ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0f
            else -> null
        }
    } as TerminalDisplay

    /**
     * A fixed byte source. `AppendableTerminalDataStream` looks like the natural
     * choice but blocks on an empty queue - it feeds a live terminal - so an
     * exhausted stream has to raise `EOF`, which is how the emulator is told to stop.
     */
    private class StaticDataStream(private val text: String) : TerminalDataStream {
        private var index = 0
        private val pushed = ArrayDeque<Char>()

        override fun getChar(): Char {
            if (pushed.isNotEmpty()) return pushed.removeFirst()
            if (index >= text.length) throw TerminalDataStream.EOF()
            return text[index++]
        }

        override fun pushChar(ch: Char) {
            pushed.addFirst(ch)
        }

        override fun readNonControlCharacters(maxChars: Int): String {
            val result = StringBuilder()
            while (result.length < maxChars) {
                val ch = try {
                    getChar()
                } catch (_: TerminalDataStream.EOF) {
                    break
                }
                if (ch.code < CONTROL_CHAR_LIMIT) {
                    pushChar(ch)
                    break
                }
                result.append(ch)
            }
            return result.toString()
        }

        override fun pushBackBuffer(chars: CharArray, start: Int) {
            for (i in chars.indices.reversed()) {
                if (i >= start) pushed.addFirst(chars[i])
            }
        }

        override fun isEmpty(): Boolean = pushed.isEmpty() && index >= text.length
    }

    private companion object {
        const val CONTROL_CHAR_LIMIT = 32
    }
}
