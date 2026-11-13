package com.casco.kettle.shell.stats

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** When Stats reads the time again (#411): the next local midnight, as a tab section can outlast one. */
class UntilNextDayTest {
    @Test
    fun `waits until the next local midnight, in the reader's zone`() {
        val evening = Instant.parse("2026-11-13T22:30:00Z")
        assertEquals(90.minutes, untilNextDay(evening, TimeZone.UTC))
        // 19:30 in São Paulo (UTC-3), four and a half hours before its midnight.
        assertEquals(4.5.hours, untilNextDay(evening, TimeZone.of("America/Sao_Paulo")))
    }

    @Test
    fun `at midnight itself, waits a whole day rather than none`() {
        assertEquals(24.hours, untilNextDay(Instant.parse("2026-11-14T00:00:00Z"), TimeZone.UTC))
    }
}
