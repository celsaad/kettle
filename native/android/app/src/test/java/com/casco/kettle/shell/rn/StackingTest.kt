package com.casco.kettle.shell.rn

import org.junit.Assert.assertEquals
import org.junit.Test

/** What src/hooks/safe-brownfield.ts reads. The two share no code, so this holds the shape. */
class StackingTest {
    @Test
    fun `a push carries the route and its params, apart, for RN's router to join`() {
        assertEquals(
            mapOf("type" to "push", "pathname" to "/session-editor", "params" to mapOf("id" to "2026-10-27-a")),
            Stacking.Push.message("/session-editor", mapOf("id" to "2026-10-27-a")),
        )
    }

    @Test
    fun `a replace is the same message under its own type`() {
        assertEquals(
            mapOf("type" to "replace", "pathname" to "/import", "params" to emptyMap<String, String>()),
            Stacking.Replace.message("/import"),
        )
    }
}
