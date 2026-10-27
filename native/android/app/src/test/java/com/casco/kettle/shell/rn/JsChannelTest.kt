package com.casco.kettle.shell.rn

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Messages for JS sent before it listens are dropped by the bridge with no error (the M0 spike's
 * build facts), so the channel is the one place that knows whether JS is there yet.
 */
class JsChannelTest {
    private val delivered = mutableListOf<Map<String, Any?>>()
    private val channel = JsChannel { delivered += it }

    private val ready = mapOf("type" to "ready")

    @Test
    fun `a message sent before JS is ready arrives once it is`() {
        channel.send(mapOf("type" to "navigate", "url" to "kettle://history"))

        assertEquals(emptyList<Map<String, Any?>>(), delivered)

        channel.receive(ready)

        assertEquals(listOf(mapOf("type" to "navigate", "url" to "kettle://history")), delivered)
    }

    /**
     * A native screen's push goes through the channel like any other message (#196). Coverage of its
     * wire format on the way, not a regression pin: the channel queues every map in order, so this
     * would pass against any push that sends one.
     */
    @Test
    fun `a push sent before JS is ready arrives after the navigate before it, with its params`() {
        channel.send(mapOf("type" to "navigate", "url" to "/history"))
        channel.send(Stacking.Push.message("/session-editor", mapOf("id" to "2026-10-27-a")))

        channel.receive(ready)

        assertEquals(
            listOf(
                mapOf("type" to "navigate", "url" to "/history"),
                mapOf("type" to "push", "pathname" to "/session-editor", "params" to mapOf("id" to "2026-10-27-a")),
            ),
            delivered,
        )
    }

    /** A reload replaces the JS runtime, and the new one listens only once it has said so again. */
    @Test
    fun `after a reload it holds again, and delivers in the order sent`() {
        channel.receive(ready)
        channel.send(mapOf("n" to 1))
        channel.onReload()
        channel.send(mapOf("n" to 2))
        channel.send(mapOf("n" to 3))

        assertEquals(listOf(mapOf("n" to 1)), delivered)

        channel.receive(ready)

        assertEquals(listOf(mapOf("n" to 1), mapOf("n" to 2), mapOf("n" to 3)), delivered)
    }
}
