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
    val mixed: Boolean,
    val editable: Boolean,
    val entries: List<HistoryEntryView>,
)

/** Every row of History, in [sessions]' order. Rests are left out of a row's entries and of "mixed". */
fun historySessionsView(
    sessions: List<Session>,
    library: Library,
    zone: TimeZone,
): List<HistorySessionView> =
    sessions.map { session ->
        val worked = session.entries.withIndex().filter { (_, entry) -> entry !is SessionEntry.Rest }
        HistorySessionView(
            id = session.id,
            day = sessionStartDay(session, zone),
            workoutName = workoutNameFor(session, library),
            minutes = sessionDurationMinutes(session),
            sets = sessionSetCount(session),
            mixed = worked.map { (_, entry) -> entry::class }.toSet().size > 1,
            editable = session.endedAt != null,
            entries =
                worked.map { (index, entry) ->
                    HistoryEntryView(
                        exerciseName(library.exercises, entry.exercise),
                        sessionEntryResult(entry),
                        index,
                    )
                },
        )
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
