package com.casco.kettle.domain

/**
 * One exercise on the completion screen's "Against last time" (#416): what it put up today, and how
 * that compares with the last finished session that had it.
 *
 * [result] is today's entry, the best one when the exercise was logged twice. [change] is today's best
 * less last time's, in stored units (kilograms for a weight), and null where there is nothing to compare
 * on: interval work, a last time on a different measure, or [firstTime].
 */
data class ExerciseAgainstLast(
    val exerciseId: String,
    val exerciseName: String,
    val result: EntryResult,
    val kind: RecordKind?,
    val change: Double?,
    val firstTime: Boolean,
)

/**
 * Each exercise [session] logged, in the order it first appears, against the newest other finished
 * session in [sessions] that logged it. Rests are left out. "First time" means no finished session
 * before this one logged the exercise at all, by any measure.
 */
fun againstLastTime(
    session: Session,
    sessions: List<Session>,
    exercises: List<Exercise>,
): List<ExerciseAgainstLast> {
    val started = session.startedInstant()
    val before =
        sessions
            .filter { it.id != session.id && it.endedAt != null }
            .mapNotNull { other -> other.startedInstant()?.let { other to it } }
            .filter { (_, at) -> started == null || at < started }
            .sortedByDescending { (_, at) -> at }
            .map { (other) -> other }
    return session.entries
        .filter { it !is SessionEntry.Rest }
        .groupBy { it.exercise }
        .map { (exerciseId, entries) ->
            val best =
                entries
                    .mapNotNull { entry ->
                        entryBest(entry)?.let { entry to it }
                    }.maxByOrNull { it.second.value }
            val last = before.firstOrNull { other -> other.entries.any { it.exercise == exerciseId } }
            val lastBest =
                best?.let { (_, today) ->
                    last
                        ?.entries
                        ?.filter { it.exercise == exerciseId }
                        ?.mapNotNull(::entryBest)
                        ?.filter { it.kind == today.kind }
                        ?.maxOfOrNull { it.value }
                }
            ExerciseAgainstLast(
                exerciseId = exerciseId,
                exerciseName = exerciseName(exercises, exerciseId),
                result = sessionEntryResult(best?.first ?: entries.first()),
                kind = best?.second?.kind,
                change = lastBest?.let { best.second.value - it },
                firstTime = last == null,
            )
        }
}
