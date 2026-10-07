package com.chengyayu.pilauncher.util

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The debouncer used to silently lose keys: it copied the pending set and then
 * cleared it outside any atomic swap, so a submit landing in that window saw a
 * non-empty set, skipped scheduling, and was wiped by the following clear.
 *
 * These tests pin the invariant that a submitted key is always delivered.
 */
class PiChangeDebouncerTest {

    /** Runs the flush callback inline so assertions are deterministic. */
    private fun inlineDispatch(): (() -> Unit) -> Unit = { it() }

    @Test
    fun `coalesces a burst into a single batch`() {
        val batches = mutableListOf<List<String>>()
        newDebouncer(delayMs = 20) { paths ->
            batches += paths
            emptyList()
        }.use { debouncer ->
            repeat(10) { debouncer.submit("a.go") }
            awaitUntil { batches.isNotEmpty() }
        }

        assertEquals(1, batches.size, "a burst must produce exactly one flush")
        assertEquals(listOf("a.go"), batches.single())
    }

    @Test
    fun `delivers every distinct key of a burst`() {
        val seen = ConcurrentLinkedQueue<String>()
        newDebouncer(delayMs = 20) { paths ->
            seen += paths
            emptyList()
        }.use { debouncer ->
            val keys = (1..40).map { "f$it.go" }
            keys.forEach { debouncer.submit(it) }
            awaitUntil { seen.size >= keys.size }
        }

        assertEquals(40, seen.size, "no key may be dropped")
        assertEquals(40, seen.distinct().size)
    }

    @Test
    fun `keys submitted while a flush is draining are still delivered`() {
        // Reproduces the old race: the first flush is mid-flight when a new key
        // arrives. Before the fix that key was cleared and never re-scheduled.
        val seen = ConcurrentLinkedQueue<String>()
        val firstFlushStarted = CountDownLatch(1)
        val releaseFirstFlush = CountDownLatch(1)

        val debouncer = newDebouncer(delayMs = 20) { paths ->
            if (paths.contains("first.go")) {
                firstFlushStarted.countDown()
                releaseFirstFlush.await(2, TimeUnit.SECONDS)
            }
            seen += paths
            emptyList()
        }

        debouncer.use {
            it.submit("first.go")
            assertTrue(firstFlushStarted.await(2, TimeUnit.SECONDS), "first flush should start")

            // Submit while the first flush is blocked mid-drain.
            it.submit("during-flush.go")
            releaseFirstFlush.countDown()

            awaitUntil { seen.contains("during-flush.go") }
        }

        assertTrue(
            seen.contains("during-flush.go"),
            "a key submitted during a flush must not be lost, saw=$seen"
        )
    }

    @Test
    fun `re-queues keys the callback could not handle`() {
        var attempts = 0
        val seen = mutableListOf<String>()

        newDebouncer(delayMs = 10, retryDelayMs = 20) { paths ->
            attempts++
            seen += paths
            if (attempts == 1) paths else emptyList() // first attempt "cannot handle"
        }.use { debouncer ->
            debouncer.submit("busy.go")
            awaitUntil(timeoutMs = 3_000) { attempts >= 2 }
        }

        assertTrue(attempts >= 2, "unhandled keys must be retried, attempts=$attempts")
        assertEquals(listOf("busy.go", "busy.go"), seen)
    }

    @Test
    fun `a throwing callback does not wedge the debouncer`() {
        var calls = 0
        newDebouncer(delayMs = 10) {
            calls++
            throw IllegalStateException("boom")
        }.use { debouncer ->
            debouncer.submit("a.go")
            awaitUntil { calls >= 1 }
            debouncer.submit("b.go")
            awaitUntil { calls >= 2 }
        }

        assertEquals(2, calls, "a failing flush must not stop later flushes")
    }

    @Test
    fun `stops delivering after close`() {
        val batches = mutableListOf<List<String>>()
        val debouncer = newDebouncer(delayMs = 30) { paths ->
            batches += paths
            emptyList()
        }

        debouncer.submit("a.go")
        debouncer.close()
        Thread.sleep(150)

        assertTrue(batches.isEmpty(), "a closed debouncer must not flush")
    }

    @Test
    fun `clear drops pending keys`() {
        val batches = mutableListOf<List<String>>()
        newDebouncer(delayMs = 40) { paths ->
            batches += paths
            emptyList()
        }.use { debouncer ->
            debouncer.submit("a.go")
            debouncer.clear()
            Thread.sleep(150)
        }

        assertTrue(batches.isEmpty(), "cleared keys must not be flushed")
    }

    private fun newDebouncer(
        delayMs: Int,
        retryDelayMs: Long = delayMs * 5L,
        onFlush: (List<String>) -> List<String>
    ) = PiChangeDebouncer(
        delayMs = delayMs,
        retryDelayMs = retryDelayMs,
        dispatch = inlineDispatch(),
        onFlush = onFlush
    )

    private fun awaitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), "condition not met within ${timeoutMs}ms")
    }

    private inline fun PiChangeDebouncer.use(block: (PiChangeDebouncer) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }
}

/**
 * Stress variant: many threads submit while flushes are constantly draining.
 * The pre-fix implementation copied the pending set and cleared it outside the
 * atomic swap, so keys landing in that window were cleared without a follow-up
 * flush ever being scheduled - they were lost silently.
 */
class PiChangeDebouncerConcurrencyTest {

    @Test
    fun `no key is lost when submits race with flushes`() {
        val submitted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val delivered = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        PiChangeDebouncer(
            delayMs = 1,
            dispatch = { it() },
            onFlush = { keys ->
                delivered += keys
                emptyList()
            }
        ).use { debouncer ->
            val threads = 8
            val perThread = 400
            val start = CountDownLatch(1)
            val workers = (0 until threads).map { t ->
                Thread {
                    start.await()
                    repeat(perThread) { i ->
                        val key = "t$t-k$i"
                        submitted += key
                        debouncer.submit(key)
                    }
                }.apply { isDaemon = true; start() }
            }

            start.countDown()
            workers.forEach { it.join(30_000) }

            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && !delivered.containsAll(submitted)) {
                Thread.sleep(20)
            }
        }

        val lost = submitted - delivered
        assertTrue(
            lost.isEmpty(),
            "lost ${lost.size} of ${submitted.size} keys, e.g. ${lost.take(5)}"
        )
    }
}
