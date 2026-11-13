package com.casco.kettle.shell.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The moment Stats counts from, and the zone its days are in. */
internal data class StatsTime(
    val now: Instant,
    val zone: TimeZone,
)

/**
 * When Stats counts from (#411), kept current while it shows: a tab's section can stay up for days,
 * which a sheet never did, and this week, the streak and today's cell all turn over with the date.
 *
 * Read again each time the screen comes back (ON_START), and at each local midnight while it stays up.
 * The timer alone isn't enough: `delay` counts uptime, which stops while the phone sleeps, so a night
 * with the screen off would leave yesterday showing until an hour or so of waking time had passed. The
 * zone is read again with it, for a reader who has travelled.
 */
@Composable
internal fun rememberStatsTime(
    clock: Clock = Clock.System,
    zoneOf: () -> TimeZone = TimeZone::currentSystemDefault,
): StatsTime {
    var time by remember { mutableStateOf(StatsTime(clock.now(), zoneOf())) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { time = StatsTime(clock.now(), zoneOf()) }
    LaunchedEffect(time) {
        delay(untilNextDay(time.now, time.zone))
        time = StatsTime(clock.now(), zoneOf())
    }
    return time
}

/** How long from [now] until the next local midnight in [zone], when the date Stats counts from turns. */
internal fun untilNextDay(
    now: Instant,
    zone: TimeZone,
): Duration {
    val tomorrow = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
    return tomorrow.atStartOfDayIn(zone) - now
}
