package com.casco.kettle.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `src/hooks/__tests__/use-rest-day-reminder.test.ts`'s arithmetic and its reasons to schedule
 * nothing, which go with RN's reminder in the preferences flip: this suite is the spec then.
 *
 * Where jest fakes the clock in whatever zone the machine has, these pass both, in New York, so the
 * DST case crosses a real boundary on every machine.
 */
class RestDayReminderTest {
    private val zone = TimeZone.of("America/New_York")

    /** [month]/[day] 2026 at [hour]:[minute] in [zone], as jest's `new Date('2026-…T…')`. */
    private fun local(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Instant = LocalDateTime(2026, month, day, hour, minute).toInstant(zone)

    private val now = local(7, 24, 9)

    @Test
    fun `lands two calendar days after the session, at the fixed hour`() {
        val at = restDayReminderAt(enabled = true, newestStartedAt = local(7, 24, 7, 30).toString(), now, zone)

        assertEquals(local(7, 26, 18), at)
    }

    /**
     * Steps by calendar day rather than by 48 hours. New York's clocks went forward on 8 March 2026, so
     * from late on the 7th a fixed 48 hours ends past midnight on the 10th, and the nudge lands a day
     * late. Jest's copy of this case couldn't cross a boundary in July; this one does.
     */
    @Test
    fun `steps whole days across a DST boundary rather than a fixed 48 hours`() {
        val at =
            restDayReminderAt(
                enabled = true,
                newestStartedAt = local(3, 7, 23, 30).toString(),
                local(3, 7, 23, 45),
                zone,
            )

        assertEquals(local(3, 9, 18), at)
    }

    @Test
    fun `schedules nothing while the preference is off`() {
        assertNull(restDayReminderAt(enabled = false, newestStartedAt = local(7, 24, 7, 30).toString(), now, zone))
    }

    /**
     * The user has been away long enough that the nudge's moment has passed, and they are opening the
     * app right now, which is the thing it exists to prompt.
     */
    @Test
    fun `schedules nothing when the reminder would already be due`() {
        assertNull(restDayReminderAt(enabled = true, newestStartedAt = local(7, 10, 7, 30).toString(), now, zone))
    }

    @Test
    fun `schedules nothing on an install with no sessions at all`() {
        assertNull(restDayReminderAt(enabled = true, newestStartedAt = null, now, zone))
    }
}
