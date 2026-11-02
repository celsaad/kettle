package com.casco.kettle.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * `REST_DAY_REMINDER_DAYS` in `src/domain/preferences.ts`, which the settings note quotes. The two
 * share no code, so change both.
 */
const val REST_DAY_REMINDER_DAYS = 2

/** The local hour the nudge lands at, `REST_DAY_REMINDER_HOUR` in the same file. */
private const val REST_DAY_REMINDER_HOUR = 18

/**
 * When the opt-in rest-day nudge should fire: [REST_DAY_REMINDER_DAYS] calendar days after the newest
 * session started, at a fixed local hour in [zone]. Null when there is nothing to schedule, which the
 * caller turns into a cancel.
 *
 * Steps by [LocalDate][kotlinx.datetime.LocalDate] rather than by 48 hours, the DST fix RN's `setDate()`
 * carried: a fixed offset across a 23- or 25-hour day can land on the wrong date.
 *
 * A moment already past schedules nothing too. The user is opening the app now, which is what the
 * nudge exists to prompt, and firing it immediately would notify them about what they're doing.
 */
fun restDayReminderAt(
    enabled: Boolean,
    newestStartedAt: String?,
    now: Instant,
    zone: TimeZone,
): Instant? {
    val startedAt = newestStartedAt?.takeIf { enabled }?.let(::instantOrNull) ?: return null
    val day = startedAt.toLocalDateTime(zone).date.plus(DatePeriod(days = REST_DAY_REMINDER_DAYS))
    return day.atTime(LocalTime(REST_DAY_REMINDER_HOUR, 0)).toInstant(zone).takeIf { it > now }
}
