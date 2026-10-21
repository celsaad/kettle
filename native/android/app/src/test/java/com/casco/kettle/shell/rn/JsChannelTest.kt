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
