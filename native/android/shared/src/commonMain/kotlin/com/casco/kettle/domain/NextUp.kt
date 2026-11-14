package com.casco.kettle.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What a "start this" on Home's card starts: a bare workout, or one program slot with its overrides. */
sealed interface SessionParams {
    data class Workout(
        val workoutId: String,
    ) : SessionParams

    data class ProgramSlot(
        val programId: String,
        val week: Long,
        val day: String?,
    ) : SessionParams
}

/**
 * What Home queues up, as `NextUpView` in RN's `state/selectors/next-up.ts`, which went with RN Home
 * (#235). Two classes because a rest day has no workout and nothing to start, and the card is the one
 * place that must not get that wrong. The week's fields are structured, so the card composes the
 * label; [Queued.weekDay] and the notes are user data and render verbatim.
 */
sealed interface NextUp {
    val weekDay: String?
    val weekNotes: String?

    /** Where the slot shown sits in its program, which Today's program row and strip read (#412). */
    val position: ProgramPosition?

    /**
     * [exercises] is the whole library's, with the week's overrides applied. [position] is null for a
     * workout of the day, which no program put there.
     */
    data class Queued(
        val workout: Workout,
        val exercises: List<Exercise>,
        val weekNumber: Long?,
        override val weekDay: String?,
        override val weekNotes: String?,
        val sessionParams: SessionParams,
        override val position: ProgramPosition? = null,
    ) : NextUp

    /**
     * [skipTo] is the next slot that runs something, the card's "train anyway", and [skipToWorkout] its
     * workout's name, null when that workout is gone. [position] is the rest slot shown, not [skipTo]'s.
     */
    data class Rest(
        val weekNumber: Long,
        override val weekDay: String?,
        override val weekNotes: String?,
        val skipTo: SessionParams.ProgramSlot?,
        override val position: ProgramPosition,
        val skipToWorkout: String?,
    ) : NextUp
}

/**
 * The slot after the one the newest session tracked against [program] ran, wrapping at the end; the
 * first slot when none did or that slot is gone. Not a count of sessions, which drifts the moment a
 * week is redone or started out of turn from Program detail. [sessions] is newest first.
 */
internal fun nextWeekAfter(
    program: Program,
    sessions: List<Session>,
): ProgramWeek {
    val weeks = rotation(program)
    return weeks[nextIndexAfter(weeks, program, sessions)]
}

/**
 * Home's card: the next slot of the active program, or a library workout when there is no program to
 * follow or its slot names a workout that is gone. Null only for a library with no workouts.
 *
 * A rest day clears itself by calendar days in [zone], since nothing about one is logged: the day you
 * train, the rest slot after it is next; the next day it is the rest day itself; the day after it is
 * spent. With `R` rest slots in a row, `restsServed = max(0, daysSince(lastSession) - 1)`, and the card
 * shows rest slot `restsServed + 1` until that reaches `R`. A persisted "rested" flag was RN's rejected
 * alternative; the card's [NextUp.Rest.skipTo] is the way out when this disagrees with the user's week.
 */
fun nextUpView(
    library: Library,
    sessions: List<Session>,
    now: Instant,
    zone: TimeZone,
): NextUp? {
    val program =
        activeProgram(library, sessions)?.takeIf { it.weeks.isNotEmpty() } ?: return workoutOfTheDay(library, now)
    val weeks = rotation(program)
    val start = nextIndexAfter(weeks, program, sessions)
    val restRun = restRunLength(weeks, start)
    return restOwed(library, program, weeks, start, restRun, lastTracked(program, sessions), now, zone)
        ?: queued(program, (start + restRun) % weeks.size, library)
        ?: workoutOfTheDay(library, now)
}

/**
 * The rest slot still owed of the [restRun] from [start], or null once they are spent. No [last]
 * session means no anchor, so a leading rest day shows until something is logged. An unreadable
 * `startedAt` serves no rests either, as RN's NaN arithmetic fell through to the workout.
 */
@Suppress("LongParameterList") // The rule's own inputs; bundling them would only rename them.
private fun restOwed(
    library: Library,
    program: Program,
    weeks: List<ProgramWeek>,
    start: Int,
    restRun: Int,
    last: Session?,
    now: Instant,
    zone: TimeZone,
): NextUp.Rest? {
    if (restRun == 0) return null
    val training = weeks[(start + restRun) % weeks.size].takeIf { restRun < weeks.size }
    // Whole calendar days in [zone], so a DST day of 23 or 25 hours still counts as one.
    val today = now.toLocalDateTime(zone).date
    val daysSince =
        last?.let {
            it
                .startedInstant()
                ?.toLocalDateTime(zone)
                ?.date
                ?.daysUntil(today)
        }
    val restsServed = if (last == null) 0 else daysSince?.let { maxOf(0, it - 1) }
    val owed = training == null || (restsServed != null && restsServed < restRun)
    val index = (start + minOf(restsServed ?: 0, restRun - 1)) % weeks.size
    val rest = weeks[index]
    return if (!owed) {
        null
    } else {
        NextUp.Rest(
            rest.week,
            rest.day,
            rest.notes,
            training?.let { SessionParams.ProgramSlot(program.id, it.week, it.day) },
            ProgramPosition(program, index),
            // Named by the same resolver that would start it.
            training?.let { resolveWorkoutForWeek(program, it.week, library, it.day)?.workout?.name },
        )
    }
}

/** The slot at [index] queued with its overrides, or null when its workout is gone. */
private fun queued(
    program: Program,
    index: Int,
    library: Library,
): NextUp.Queued? {
    val target = rotation(program)[index]
    return resolveWorkoutForWeek(program, target.week, library, target.day)?.let {
        NextUp.Queued(
            it.workout,
            it.exercises,
            target.week,
            target.day,
            target.notes,
            SessionParams.ProgramSlot(program.id, target.week, target.day),
            ProgramPosition(program, index),
        )
    }
}

/**
 * Picked by the UTC day rather than at random, so it holds still across recompositions within a day
 * instead of flickering. The seam for a smarter pick one day.
 */
private fun workoutOfTheDay(
    library: Library,
    now: Instant,
): NextUp? {
    if (library.workouts.isEmpty()) return null
    val workout = library.workouts[now.toEpochMilliseconds().floorDiv(MILLIS_PER_DAY).mod(library.workouts.size)]
    return NextUp.Queued(workout, library.exercises, null, null, null, SessionParams.Workout(workout.id))
}

/** The program of the newest session run under one, else the library's first. */
private fun activeProgram(
    library: Library,
    sessions: List<Session>,
): Program? {
    val last = sessions.firstOrNull { it.program != null }?.program
    return library.programs.firstOrNull { it.id == last } ?: library.programs.firstOrNull()
}

/**
 * By week number, and within one the order the weeks were written: `day` is free text, so there is no
 * order to read out of it. Relies on `sortedBy` being stable, which is why it has no tiebreak; RN's
 * `localeCompare` one ran a calendar week alphabetically.
 */
internal fun rotation(program: Program): List<ProgramWeek> = program.weeks.sortedBy { it.week }

private fun lastTracked(
    program: Program,
    sessions: List<Session>,
): Session? = sessions.firstOrNull { it.program == program.id && it.programWeek != null }

private fun nextIndexAfter(
    weeks: List<ProgramWeek>,
    program: Program,
    sessions: List<Session>,
): Int {
    val last = lastTracked(program, sessions) ?: return 0
    val index = weeks.indexOfFirst { it.week == last.programWeek && it.day == last.programDay }
    return if (index == -1) 0 else (index + 1) % weeks.size
}

/** Rest slots in a row from [start], wrapping, capped at the length so an all-rest program can't spin. */
private fun restRunLength(
    weeks: List<ProgramWeek>,
    start: Int,
): Int {
    var run = 0
    while (run < weeks.size && weeks[(start + run) % weeks.size] is ProgramWeek.RestDay) run += 1
    return run
}

private const val MILLIS_PER_DAY = 86_400_000L
