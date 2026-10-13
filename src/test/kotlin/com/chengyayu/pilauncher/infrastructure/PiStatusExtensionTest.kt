package com.chengyayu.pilauncher.infrastructure

import com.chengyayu.pilauncher.domain.PiStatus
import com.chengyayu.pilauncher.domain.PiStatusReport
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plugin's half of `docs/adr/0005`: put a reporter inside Pi, then read what it
 * writes. These tests never start Pi - they drive the file channel the extension and
 * the plugin share.
 */
class PiStatusExtensionTest {

    private val base: Path = Files.createTempDirectory("pi-launcher-test")

    @AfterTest
    fun cleanUp() {
        Files.walk(base).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun extension(supported: Boolean = true) = PiStatusExtension(
        piCommand = { "pi" },
        baseDirectory = base,
        detectSupport = { supported }
    ).also { it.probe() }

    @Test
    fun `ships the extension Pi is told to load`() {
        val channel = assertNotNull(extension().open("Pi 1-abcd"))

        val extensionText = Path.of(channel.extensionPath).readText()

        assertTrue(extensionText.contains("ui_prompt_start"), "the reporter must report blocking prompts")
        assertTrue(extensionText.contains("agent_settled"), "the reporter must report the settled boundary")
    }

    @Test
    fun `places the report next to the extension, where __dirname finds it`() {
        val channel = assertNotNull(extension().open("Pi 1-abcd"))

        assertEquals(
            PiStatusReport.FILE_NAME,
            Path.of(channel.extensionPath).parent.resolve(PiStatusReport.FILE_NAME).fileName.toString()
        )
    }

    @Test
    fun `reads nothing until Pi has reported`() {
        val channel = assertNotNull(extension().open("Pi 1-abcd"))

        assertNull(channel.read())
    }

    @Test
    fun `reads the report Pi wrote`() {
        val channel = assertNotNull(extension().open("Pi 1-abcd"))
        report(channel, "state=blocked\nkind=confirm\nmessage=${base64("Allow tool call?")}\n")

        val status = channel.read()

        assertEquals(PiStatus.State.BLOCKED, status?.state)
        assertEquals(PiStatus.Kind.CONFIRM, status?.kind)
        assertEquals("Allow tool call?", status?.message)
    }

    @Test
    fun `gives every session its own directory`() {
        val provider = extension()
        val first = assertNotNull(provider.open("Pi 1-abcd"))
        val second = assertNotNull(provider.open("Pi 2-efgh"))

        assertTrue(first.extensionPath != second.extensionPath)
    }

    @Test
    fun `closing a session removes its channel`() {
        val provider = extension()
        val channel = assertNotNull(provider.open("Pi 1-abcd"))

        provider.close("Pi 1-abcd")

        assertFalse(Path.of(channel.extensionPath).exists())
    }

    /** Pi builds without `--extension` must not be handed a flag they would reject. */
    @Test
    fun `reports nothing when Pi cannot load extensions`() {
        assertNull(extension(supported = false).open("Pi 1-abcd"))
    }

    @Test
    fun `degrades instead of throwing when the directory cannot be written`() {
        val provider = PiStatusExtension(
            piCommand = { "pi" },
            baseDirectory = Path.of("/dev/null/pi-launcher"),
            detectSupport = { true }
        ).also { it.probe() }

        assertNull(provider.open("Pi 1-abcd"))
    }

    @Test
    fun `an unreadable report degrades to no status`() {
        val channel = assertNotNull(extension().open("Pi 1-abcd"))
        report(channel, "state=working\n")

        assertNotNull(channel.read())
    }

    @Test
    fun `a corrupt report reads as no status rather than a wrong one`() {
        val channel = assertNotNull(extension().open("Pi 1-abcd"))
        report(channel, "\u0000\u0001 garbage")

        assertNull(channel.read())
    }

    @Test
    fun `a new provider discards the previous run's sessions`() {
        val provider = extension()
        val channel = assertNotNull(provider.open("Pi 1-abcd"))
        assertTrue(Path.of(channel.extensionPath).exists())

        extension()

        assertFalse(Path.of(channel.extensionPath).exists())
    }

    private fun report(channel: PiStatusChannel, text: String) {
        Path.of(channel.extensionPath).parent.createDirectories()
            .resolve(PiStatusReport.FILE_NAME)
            .writeText(text)
    }

    private fun base64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
