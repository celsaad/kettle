package com.casco.kettle.domain

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

data class HistoryEntryView(
    val exerciseName: String,
    val result: EntryResult,
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
        val worked = session.entries.filter { it !is SessionEntry.Rest }
        HistorySessionView(
            id = session.id,
            day = session.startedInstant()?.toLocalDateTime(zone)?.date,
            workoutName = workoutNameFor(session, library),
            minutes = sessionDurationMinutes(session),
            sets = sessionSetCount(session),
            mixed = worked.map { it::class }.toSet().size > 1,
            editable = session.endedAt != null,
            entries =
                worked.map {
                    HistoryEntryView(
                        exerciseName(library.exercises, it.exercise),
                        sessionEntryResult(it),
                    )
                },
        )
    }
