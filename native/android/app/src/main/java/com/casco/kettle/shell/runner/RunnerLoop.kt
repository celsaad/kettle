package com.casco.kettle.shell.runner

import android.os.Handler

/** A scheduled block that hasn't run, which [cancel] stops from running. */
fun interface Cancellable {
    fun cancel()
}

/**
 * The one thread the running session lives on (#305): every command, timer firing and catch-up is a
 * block on it, one after another, so none of them can interleave with another and see half a
 * transition. An interface so a test runs it on a virtual clock.
 */
interface RunnerLoop {
    fun post(block: () -> Unit)

    fun postDelayed(
        delayMs: Long,
        block: () -> Unit,
    ): Cancellable
}

/**
 * [RunnerLoop] on a `HandlerThread`'s looper. Its delays are on the uptime clock, which stops while
 * the CPU sleeps; the service holds a partial wake lock for the session, which is what keeps it
 * counting, and the engine reads the time from `elapsedRealtime()` anyway, so a late firing still
 * reads the right moment.
 */
class HandlerLoop(
    private val handler: Handler,
) : RunnerLoop {
    override fun post(block: () -> Unit) {
        handler.post(block)
    }

    override fun postDelayed(
        delayMs: Long,
        block: () -> Unit,
    ): Cancellable {
        val runnable = Runnable(block)
        handler.postDelayed(runnable, delayMs)
        return Cancellable { handler.removeCallbacks(runnable) }
    }
}
