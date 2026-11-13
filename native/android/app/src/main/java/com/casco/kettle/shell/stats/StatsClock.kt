package com.casco.kettle.shell.stats

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * How long from [now] until the next local midnight in [zone], when the date Stats counts this week, the
 * streak and today's cell from turns over (#411): a tab's section can stay up across one.
 */
internal fun untilNextDay(
    now: Instant,
    zone: TimeZone,
): Duration {
    val tomorrow = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
    return tomorrow.atStartOfDayIn(zone) - now
}
