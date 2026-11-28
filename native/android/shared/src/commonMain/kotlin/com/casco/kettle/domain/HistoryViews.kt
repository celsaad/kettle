package com.casco.kettle.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The workout behind [session], or null when it was started ad-hoc, whose stand-in label is the
 * screen's to translate. A workout deleted from the library since falls back to its id, which is user
 * data either way.
 */
fun workoutNameFor(
    session: Session,
    library: Library,
): String? {
    val id = session.workout ?: return null
    return library.workouts.firstOrNull { it.id == id }?.name ?: id
}

/** The day [session] started in [zone], or null where `started_at` isn't an instant. */
fun sessionStartDay(
    session: Session,
    zone: TimeZone,
): LocalDate? = session.startedInstant()?.toLocalDateTime(zone)?.date

/**
 * [index] is the entry's place in the session's own `entries`, so a row can find the one that set its
 * record (#414).
 */
data class HistoryEntryView(
    val exerciseName: String,
    val result: EntryResult,
    val index: Int,
)

/**
 * One History row, as RN History's `HistorySessionView` was, less the formatting, which is the
 * screen's (#192). [day] is the day it started in the reader's zone, null
 * where `started_at` isn't an instant; [workoutName] is null for an ad-hoc session.
 *
 * [editable] is false while a session is still running: the runner owns that file and writes through
 * its own copy, so an edit from History would be overwritten by the next set.
 */
data class HistorySessionView(
    val id: String,
    val day: LocalDate?,
    val workoutName: String?,
    val minutes: Int,
    val sets: Int,
    val inRounds: Boolean,
    val editable: Boolean,
    val entries: List<HistoryEntryView>,
    /** What Start again starts (#281), or null for a session with no workout to start. */
    val again: StartAgain? = null,
)

/**
 * What a logged session's Start again does (#281): a fresh session of what it ran, at its current
 * version, the old file untouched; or nothing, said why, once what it ran has gone from the library.
 */
sealed interface StartAgain {
    data class Start(
        val params: SessionParams,
    ) : StartAgain

    data object Gone : StartAgain
}

/**
 * [session]'s Start again: its program slot when it's the newest session run under a program
 * ([newestTracked]) and that slot still runs the same workout, else its workout while the library has
 * it, else [StartAgain.Gone]. Null for an ad-hoc session, which ran no workout and so has none to start.
 *
 * Only the newest: the program's place is read from its newest session, so an older slot started again
 * would set the program back to it, or switch to another program altogether, from one tap on an old
 * row. Repeating the newest moves nothing. Every other row starts its workout alone, which leaves the
 * program where it is. A slot that now runs something else, or nothing that resolves, would start a
 * different workout from the row's, or an empty runner.
 */
fun startAgain(
    session: Session,
    library: Library,
    newestTracked: Session?,
): StartAgain? {
    val workout = session.workout ?: return null
    val week = session.programWeek
    val program = library.programs.firstOrNull { it.id == session.program }?.takeIf { newestTracked?.id == session.id }
    val runsSame = { p: Program, w: Long ->
        resolveWorkoutForWeek(p, w, library, session.programDay)?.workout?.id ==
            workout
    }
    val slot =
        if (program != null && week != null && runsSame(program, week)) {
            SessionParams.ProgramSlot(program.id, week, session.programDay)
        } else {
            null
        }
    return if (library.workouts.none { it.id == workout }) {
        StartAgain.Gone
    } else {
        StartAgain.Start(slot ?: SessionParams.Workout(workout))
    }
}

/**
 * Every row of History, in [sessions]' order. Rests are left out of a row's entries. [HistorySessionView.inRounds]
 * is [sessionInRounds].
 */
fun historySessionsView(
    sessions: List<Session>,
    library: Library,
    zone: TimeZone,
): List<HistorySessionView> {
    // Newest first, as the log is read everywhere: the session Home reads the program's place from.
    // By NextUp's two rules: the active program is the newest session's with any program, week or not
    // (#569), and its place is that program's newest session with a week.
    val active = sessions.firstOrNull { it.program != null }?.program
    val newestTracked = sessions.firstOrNull { it.program == active && it.programWeek != null }
    return sessions.map { session ->
        val worked = session.entries.withIndex().filter { (_, entry) -> entry !is SessionEntry.Rest }
        HistorySessionView(
            id = session.id,
            day = sessionStartDay(session, zone),
            workoutName = workoutNameFor(session, library),
            minutes = sessionDurationMinutes(session),
            sets = sessionSetCount(session),
            inRounds = sessionInRounds(session),
            editable = session.endedAt != null,
            entries =
                worked.map { (index, entry) ->
                    HistoryEntryView(
                        exerciseName(library.exercises, entry.exercise),
                        sessionEntryResult(entry),
                        index,
                    )
                },
            again = startAgain(session, library, newestTracked),
        )
    }
}

/** One week of Sessions (#414): the day it [start]s on, null for rows with no day, and its rows. */
data class HistoryWeek(
    val start: LocalDate?,
    val rows: List<HistorySessionView>,
)

/**
 * [rows] by the week each started in, weeks starting on [firstWeekday], newest week first, each week's
 * rows in the order they come. Ordered by the week rather than by first appearance: the log is sorted
 * by its `started_at` text, so offsets that differ can bring a row out of week order. Rows whose
 * `started_at` isn't an instant have no week, and share one group with a null start, last.
 */
fun historyWeeks(
    rows: List<HistorySessionView>,
    firstWeekday: DayOfWeek,
): List<HistoryWeek> =
    rows
        .groupBy { row -> row.day?.let { startOfWeek(it, firstWeekday) } }
        .map { (start, inWeek) -> HistoryWeek(start, inWeek) }
        // Descending over nulls-first: the newest week first and the undated group last.
        .sortedWith(compareByDescending<HistoryWeek, LocalDate?>(nullsFirst()) { it.start })
