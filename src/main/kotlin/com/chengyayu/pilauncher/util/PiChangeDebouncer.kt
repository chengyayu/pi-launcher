package com.chengyayu.pilauncher.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Coalesces a burst of events into one [onFlush] call, dispatched through [dispatch] (the UI
 * thread): acting on every event of a `gofmt -w` opened dozens of tabs at once.
 *
 * Keys [onFlush] could not handle yet (the IDE is indexing) are re-queued with a longer delay
 * instead of dropped. [submit] may be called from any thread and the pending set is swapped
 * atomically, so a key is never lost.
 *
 * Free of IDE types on purpose, so it can be unit tested with a direct [dispatch].
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

            // Add before scheduling: a flush draining right now must not swallow these keys.
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
