package com.chengyayu.pilauncher.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Coalesces a burst of events into a single callback.
 *
 * Pi (and the tools it runs, e.g. `gofmt`) writes files many times in quick
 * succession, and a single session may touch many files. Acting on every event
 * opened dozens of editor tabs and eventually froze the IDE. This debouncer:
 *
 *  - accumulates distinct keys for [delayMs],
 *  - then delivers them to [onFlush] through [dispatch],
 *  - and never lets more than one flush be scheduled at a time.
 *
 * [onFlush] may return keys it could not handle yet (for example because the IDE
 * is indexing). Those are re-queued with a longer delay instead of being
 * dropped, because a dropped key stays invisible for the rest of the session.
 *
 * Thread-safety: [submit] may be called from any thread; [onFlush] runs through
 * [dispatch]. The pending set is swapped out atomically so a key submitted while
 * a flush is draining can never be lost.
 *
 * Deliberately free of IDE types so it can be unit tested with a direct
 * [dispatch].
 *
 * @param dispatch runs a task on the UI thread; injectable for tests.
 */
class PiChangeDebouncer(
    private val delayMs: Int,
    private val retryDelayMs: Long = delayMs * 5L,
    private val dispatch: (() -> Unit) -> Unit,
    private val onFlush: (List<String>) -> List<String>
) : AutoCloseable {

    private val pending = AtomicReference<Set<String>>(emptySet())
    private val disposed = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "pi-change-debouncer").apply { isDaemon = true }
    }

    @Volatile
    private var scheduled: ScheduledFuture<*>? = null

    @Volatile
    private var retrying = false

    fun submit(key: String) = submitAll(listOf(key), retry = false)

    private fun submitAll(keys: Collection<String>, retry: Boolean) {
        if (disposed.get() || keys.isEmpty()) return

        synchronized(this) {
            if (disposed.get()) return

            // Add before deciding whether to schedule: a flush draining right now
            // must not be able to swallow these keys without a follow-up flush.
            pending.updateAndGet { it + keys }

            if (retry) retrying = true

            val current = scheduled
            if (current == null || current.isDone) {
                val delay = if (retrying) retryDelayMs else delayMs.toLong()
                scheduled = scheduler.schedule({ flush() }, delay, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun flush() {
        if (disposed.get()) return

        val batch: List<String>
        synchronized(this) {
            scheduled = null
            retrying = false
            batch = pending.getAndSet(emptySet()).toList()
        }

        if (batch.isEmpty()) return

        dispatch {
            if (disposed.get()) return@dispatch
            val unhandled = runCatching { onFlush(batch) }.getOrDefault(emptyList())
            if (unhandled.isNotEmpty()) submitAll(unhandled, retry = true)
        }
    }

    /** Forget everything pending, without disposing. */
    fun clear() {
        synchronized(this) {
            pending.set(emptySet())
        }
    }

    override fun close() {
        disposed.set(true)
        synchronized(this) {
            scheduled?.cancel(false)
            scheduled = null
            pending.set(emptySet())
        }
        scheduler.shutdownNow()
    }
}
