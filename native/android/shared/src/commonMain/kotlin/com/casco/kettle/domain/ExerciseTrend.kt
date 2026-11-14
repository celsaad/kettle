package com.casco.kettle.domain

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * What Exercise detail's Progress says of one exercise (#413): Stats' own row when Getting stronger
 * has one, or which of Getting stronger's floors left it out, so a page about a trained exercise never
 * says it wasn't trained.
 */
sealed interface ExerciseTrend {
    data class Charted(
        val progress: ExerciseProgress,
    ) : ExerciseTrend

    /**
     * Trained, but its log is its config: a fixed-length hold, which Stats leaves out for that reason,
     * or interval work, which puts up no single number at all.
     */
    data object Fixed : ExerciseTrend

    /** Trained, but not twice on one measure: one point is not a line. */
    data object TooFew : ExerciseTrend

    data object Untrained : ExerciseTrend
}

/** Getting stronger's reading of [exerciseId] over its [TREND_WEEKS] window, or why there is none. */
fun exerciseTrend(
    sessions: List<Session>,
    exercises: List<Exercise>,
    exerciseId: String,
    now: Instant,
    zone: TimeZone,
): ExerciseTrend {
    val view = exerciseProgress(sessions, exercises, TREND_WEEKS, now, zone)
    (view.movers + view.steady).firstOrNull { it.exerciseId == exerciseId }?.let { return ExerciseTrend.Charted(it) }

    // The selector's own filter: finished, started inside the window and not in the future.
    val windowStart = trendWindowStart(now, TREND_WEEKS, zone)
    val entries =
        sessions
            .filter { session ->
                val at = session.startedInstant()
                session.endedAt != null && at != null && at >= windowStart && at <= now
            }.flatMap { session -> session.entries.filter { it.exercise == exerciseId } }
    val exercise = exercises.firstOrNull { it.id == exerciseId }
    return when {
        entries.isEmpty() -> ExerciseTrend.Untrained
        exercise != null && isFixedTargetHold(exercise) -> ExerciseTrend.Fixed
        entries.all { entryBest(it) == null } -> ExerciseTrend.Fixed
        else -> ExerciseTrend.TooFew
    }
}
