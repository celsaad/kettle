package com.casco.kettle.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

// `src/state/selectors/history-stats.ts`, which holds the reasoning for each rule. Days are calendar
// days in the reader's zone, as RN's `toDateString` and `setDate` count them: a [LocalDate] has no
// hours, so a week across a DST change can't come out an hour short the way millisecond steps would.
// `now`, the zone and the first weekday are parameters, where RN reads the clock and the device.

/**
 * A count of sessions and their time, as one "1h 30m" reading: [hours] and [minutes] are both whole.
 * RN's carries `sets` too, which Stats never reads; it arrives with History's port (#202).
 */
data class HistoryStats(
    val sessions: Int,
    val hours: Int,
    val minutes: Int,
)

fun historyStats(sessions: List<Session>): HistoryStats {
    val (hours, minutes) = hoursMinutes(sessions.sumOf(::sessionDurationMinutes))
    return HistoryStats(sessions.size, hours, minutes)
}

/** A length of time as one "1h 30m" reading: whole hours, and the minutes left over. */
data class HoursMinutes(
    val hours: Int,
    val minutes: Int,
)

/**
 * The one place minutes become a reading. RN once printed a fractional hour beside the remainder, so
 * 90 minutes read "1.5h 30m"; both halves come from here so that can't come back in one copy.
 */
fun hoursMinutes(totalMinutes: Int): HoursMinutes =
    HoursMinutes(totalMinutes / MINUTES_PER_HOUR, totalMinutes % MINUTES_PER_HOUR)

/**
 * [historyStats] over the week [now] is in, the week starting on [firstWeekday] as the reader's own
 * calendar does rather than on an assumed Monday. A session that isn't at an instant is left out.
 */
fun thisWeekStats(
    sessions: List<Session>,
    now: Instant,
    zone: TimeZone,
    firstWeekday: DayOfWeek,
): HistoryStats {
    val weekStart = startOfWeek(now.dayIn(zone), firstWeekday)
    return historyStats(sessions.filter { session -> session.startedDay(zone)?.let { it >= weekStart } == true })
}

/**
 * Consecutive days with a session, walking back from today. Today without one yet doesn't break it
 * (the day isn't over); a whole day without one does.
 */
fun currentStreak(
    sessions: List<Session>,
    now: Instant,
    zone: TimeZone,
): Int {
    val activeDays = sessions.mapNotNull { it.startedDay(zone) }.toSet()
    val today = now.dayIn(zone)
    var cursor = if (today in activeDays) today else today.minus(ONE_DAY)
    var streak = 0
    while (cursor in activeDays) {
        streak += 1
        cursor = cursor.minus(ONE_DAY)
    }
    return streak
}

/** The minutes at which a trained day's shade steps up, which the legend names as well. */
const val CALENDAR_MID_MINUTES = 30
const val CALENDAR_LONG_MINUTES = 45

/**
 * A day's shade: [Rest] is no session; the others step up at [CALENDAR_MID_MINUTES] and
 * [CALENDAR_LONG_MINUTES].
 */
enum class CalendarLevel { Rest, Short, Mid, Long }

/**
 * Fixed buckets rather than a scale relative to the reader's longest day, so a legend can name them.
 * A trained day is never [CalendarLevel.Rest], even at zero minutes: an unfinished session has no
 * duration, and shading it as rest would say you didn't turn up.
 */
fun calendarLevel(
    sessions: Int,
    minutes: Int,
): CalendarLevel =
    when {
        sessions == 0 -> CalendarLevel.Rest
        minutes < CALENDAR_MID_MINUTES -> CalendarLevel.Short
        minutes < CALENDAR_LONG_MINUTES -> CalendarLevel.Mid
        else -> CalendarLevel.Long
    }

data class CalendarDay(
    val date: LocalDate,
    val sessions: Int,
    val minutes: Int,
    val level: CalendarLevel,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/** One row of Stats' training calendar. */
data class CalendarWeek(
    val weekStart: LocalDate,
    val sessions: Int,
    val minutes: Int,
    val days: List<CalendarDay>,
) {
    /** [minutes] as the row's spoken reading. */
    val time: HoursMinutes get() = hoursMinutes(minutes)
}

/**
 * The last [weeks] weeks up to [now], **oldest first**, each as seven days, every one present whether
 * trained or not: a gap is the most informative cell on a consistency chart. Weeks start where
 * [thisWeekStats]'s does and sessions count as it counts them, unfinished ones included, so the THIS
 * WEEK tile and the last row never disagree about the week they both show.
 */
fun trainingCalendar(
    sessions: List<Session>,
    weeks: Int,
    now: Instant,
    zone: TimeZone,
    firstWeekday: DayOfWeek,
): List<CalendarWeek> {
    val byDay = HashMap<LocalDate, Pair<Int, Int>>()
    for (session in sessions) {
        val day = session.startedDay(zone) ?: continue
        val (count, minutes) = byDay[day] ?: (0 to 0)
        byDay[day] = count + 1 to minutes + sessionDurationMinutes(session)
    }

    val today = now.dayIn(zone)
    val currentWeekStart = startOfWeek(today, firstWeekday)
    return (weeks - 1 downTo 0).map { weeksBack ->
        val weekStart = currentWeekStart.minus(DatePeriod(days = weeksBack * DAYS_PER_WEEK))
        val days =
            (0 until DAYS_PER_WEEK).map { offset ->
                val date = weekStart.plus(DatePeriod(days = offset))
                val (count, minutes) = byDay[date] ?: (0 to 0)
                CalendarDay(date, count, minutes, calendarLevel(count, minutes), date == today, date > today)
            }
        CalendarWeek(weekStart, days.sumOf { it.sessions }, days.sumOf { it.minutes }, days)
    }
}

private fun startOfWeek(
    day: LocalDate,
    firstWeekday: DayOfWeek,
): LocalDate {
    val sinceStart = (day.dayOfWeek.ordinal - firstWeekday.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK
    return day.minus(DatePeriod(days = sinceStart))
}

private fun Instant.dayIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

private fun Session.startedDay(zone: TimeZone): LocalDate? = startedInstant()?.dayIn(zone)

private val ONE_DAY = DatePeriod(days = 1)
private const val MINUTES_PER_HOUR = 60
