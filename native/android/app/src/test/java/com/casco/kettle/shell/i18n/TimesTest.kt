package com.casco.kettle.shell.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** Times as every visible line writes them (#462): m:ss, to the whole second. */
class TimesTest {
    @Test
    fun `writes a time as minutes and seconds, minutes uncapped`() {
        assertEquals("0:30", timeText(30.0))
        assertEquals("1:15", timeText(74.6))
        assertEquals("60:00", timeText(3600.0))
    }

    @Test
    fun `writes a range, or one time for a fixed value`() {
        assertEquals("0:20–0:45", timeRange(20.0, 45.0))
        assertEquals("0:30", timeRange(30.0, null))
        assertEquals("0:30", timeRange(30.0, 0.0))
    }

    // Both ends rounded to the same second read "0:30–0:30", a range that isn't one.
    @Test
    fun `writes one time for a range whose ends round to the same second`() {
        assertEquals("0:30", timeRange(29.6, 30.4))
    }
}
