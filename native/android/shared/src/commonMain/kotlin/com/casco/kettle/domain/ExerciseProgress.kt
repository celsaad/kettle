package com.casco.kettle.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.math.abs

/**
 * One exercise's trend over a window: `ExerciseProgress` in `src/state/selectors/exercise-progress.ts`,
 * which holds the reasoning for every rule below. [points] and [sessions] are oldest first.
 */
data class ExerciseProgress(
    val exerciseId: String,
    val kind: RecordKind,
    val points: List<Double>,
    val latest: Double,
    val delta: Double,
    /** When the most recent session in the window started. */
    val lastTrainedAt: Instant,
    val bestEver: Boolean,
    val sessions: List<ProgressSession>,
)

/**
 * [at] is when the session started. An [Instant] rather than the file's string, where RN keeps the
 * string: the selector has already parsed it, and every reader of it needs it as a time.
 */
data class ProgressSession(
    val at: Instant,
    val value: Double,
    val newBest: Boolean,
)

/** Getting stronger, split the way it renders. */
data class ProgressView(
    val movers: List<ExerciseProgress>,
    val steady: List<ExerciseProgress>,
    val fixedHoldsLeftOut: Int,
)

/**
 * How far back Getting stronger looks, and the progress screen a row opens with it: one constant, so a
 * row and the chart behind it never measure different windows. Eight weeks: four is two or three
 * sessions of most exercises, which is not a trend; eight is long enough for a number to move and short
 * enough to still be about what you're doing now.
 */
const val TREND_WEEKS = 8

/**
 * Per-exercise progress over the last [weeks] weeks before [now]: `exerciseProgress` in
 * `src/state/selectors/exercise-progress.ts`, over the window [trendWindowStart] gives.
 *
 * A `startedAt` that isn't an instant is left out, as RN's `new Date` comparisons leave out an invalid
 * date. Ties in the order break on the id by code unit, where RN's `localeCompare` collates; the two
 * differ only between ids that differ by case or punctuation, trained on the same instant.
 */
fun exerciseProgress(
    sessions: List<Session>,
    exercises: List<Exercise>,
    weeks: Int,
    now: Instant,
    zone: TimeZone,
): ProgressView {
    val windowStart = trendWindowStart(now, weeks, zone)
    val fixedHolds = exercises.filter(::isFixedTargetHold).map { it.id }.toSet()

    // The whole log, oldest first: whether a value is a best depends on everything before the window.
    val finished =
        sessions
            .mapNotNull { session -> session.startedInstant()?.let { session to it } }
            .filter { (session, startedAt) -> session.endedAt != null && startedAt <= now }
            .sortedBy { (_, startedAt) -> startedAt }
    val walk = walkLog(finished, windowStart)

    // Two sessions minimum, of the kind the latest competed on. Fixed holds are counted only past that
    // floor, so the note the screen shows is about rows that would otherwise have been there.
    val trends =
        walk.windowed.mapNotNull { (exerciseId, points) ->
            val kind = points.last().kind
            points.filter { it.kind == kind }.takeIf { it.size >= 2 }?.let { exerciseId to it }
        }
    val (fixed, measured) = trends.partition { (exerciseId) -> exerciseId in fixedHolds }
    val rows = measured.map { (exerciseId, points) -> row(exerciseId, points, walk.records, windowStart) }

    val byRecency =
        compareByDescending<ExerciseProgress> { it.lastTrainedAt }.thenBy { it.exerciseId }
    val gainsFirst =
        compareBy<ExerciseProgress> { it.delta <= 0 }
            .thenByDescending { abs(it.delta) / maxOf(it.points.first(), 1.0) }
            .then(byRecency)
    return ProgressView(
        movers = rows.filter { it.delta != 0.0 }.sortedWith(gainsFirst),
        steady = rows.filter { it.delta == 0.0 }.sortedWith(byRecency),
        fixedHoldsLeftOut = fixed.size,
    )
}

/** Each exercise's points inside the window, and every best across the whole log, by [recordKey]. */
private class Walk(
    val windowed: Map<String, List<Point>>,
    val records: Map<String, Record>,
)

/**
 * One pass over [finished], oldest first: a point per exercise and kind per session, at the better of
 * two entries for one exercise, marked when it beat everything logged before it.
 */
private fun walkLog(
    finished: List<Pair<Session, Instant>>,
    windowStart: Instant,
): Walk {
    val windowed = LinkedHashMap<String, MutableList<Point>>()
    val records = HashMap<String, Record>()
    for ((session, startedAt) in finished) {
        val inSession = LinkedHashMap<String, Point>()
        for (entry in session.entries) {
            val best = entryBest(entry) ?: continue
            val key = recordKey(entry.exercise, best.kind)
            if ((inSession[key]?.value ?: Double.NEGATIVE_INFINITY) < best.value) {
                inSession[key] = Point(entry.exercise, startedAt, best.kind, best.value, newBest = false)
            }
        }
        for ((key, point) in inSession) {
            val record = records[key]
            val newBest = record != null && point.value > record.best
            if (record == null || newBest) records[key] = Record(point.value, raisedAt = startedAt.takeIf { newBest })
            if (startedAt >=
                windowStart
            ) {
                windowed.getOrPut(point.exercise) { mutableListOf() } += point.copy(newBest = newBest)
            }
        }
    }
    return Walk(windowed, records)
}

private fun row(
    exerciseId: String,
    points: List<Point>,
    records: Map<String, Record>,
    windowStart: Instant,
): ExerciseProgress {
    val values = points.map { it.value }
    val latest = values.last()
    val record = records.getValue(recordKey(exerciseId, points.last().kind))
    return ExerciseProgress(
        exerciseId = exerciseId,
        kind = points.last().kind,
        points = values,
        latest = latest,
        delta = latest - values.first(),
        lastTrainedAt = points.last().at,
        bestEver = latest == record.best && record.raisedAt != null && record.raisedAt >= windowStart,
        sessions = points.map { ProgressSession(it.at, it.value, it.newBest) },
    )
}

/**
 * Where a window of [weeks] before [now] starts: calendar days in [zone], as RN's `setDate` counts
 * them, so a window across a DST change isn't an hour short. Public so the progress chart's axis starts
 * where the row's "since" does.
 */
fun trendWindowStart(
    now: Instant,
    weeks: Int,
    zone: TimeZone,
): Instant = now.minus(weeks * DAYS_PER_WEEK, DateTimeUnit.DAY, zone)

/** Falls back to the raw id for an exercise since deleted from the library: user data either way. */
fun exerciseName(
    exercises: List<Exercise>,
    id: String,
): String = exercises.firstOrNull { it.id == id }?.name ?: id

private data class Point(
    val exercise: String,
    val at: Instant,
    val kind: RecordKind,
    val value: Double,
    val newBest: Boolean,
)

/** The best so far for one exercise and kind, and when it was last raised by beating an earlier one. */
private data class Record(
    val best: Double,
    val raisedAt: Instant?,
)

/** A timed hold the runner ends at one fixed target, so its logged time is a copy of its config. */
private fun isFixedTargetHold(exercise: Exercise): Boolean {
    val config = exercise.config as? ExerciseConfig.TimedHold ?: return false
    val min = config.holdSecMin
    return min != null && min > 0 && (config.holdSecMax == null || config.holdSecMax == min)
}

internal const val DAYS_PER_WEEK = 7
