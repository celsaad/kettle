package com.casco.kettle.domain

/**
 * `rawSessionSchema` from `src/domain/schema.ts`, read straight into the camelCase domain model, so
 * the snake_case names live here and in the writer in `SessionYaml.kt`, and nowhere else.
 *
 * Every field is read before any is checked, so one pass reports all of a document's refusals.
 */
internal fun SchemaReader.session(value: Any?): Session? {
    val fields = fields(value, ROOT) ?: return null
    val version = fields.required("version", positiveInteger)
    val id = fields.required("id", SchemaReader::id)
    val workout = fields.nullable("workout", SchemaReader::id)
    // Absent reads as null so files written before program tracking existed still parse, and
    // program_week the same for files written before it was added.
    val program = fields.nullableOrAbsent("program", SchemaReader::id)
    val programWeek = fields.nullableOrAbsent("program_week", positiveInteger)
    val programDay = fields.nullableOrAbsent("program_day", SchemaReader::string)
    val startedAt = fields.required("started_at", SchemaReader::string)
    val endedAt = fields.nullable("ended_at", SchemaReader::string)
    val entries = fields.required("entries") { v, p -> list(v, p) { item, at -> entry(item, at) } }
    return when {
        version == null || id == null -> null
        startedAt == null || entries == null -> null
        else -> Session(version, id, workout, program, programWeek, programDay, startedAt, endedAt, entries)
    }
}

/** zod's `discriminatedUnion('type', …)`: an unknown or missing type is refused at `type` itself. */
private fun SchemaReader.entry(
    value: Any?,
    path: Path,
): SessionEntry? {
    val fields = fields(value, path) ?: return null
    return when (fields.raw("type")) {
        "timed_hold" -> timedHold(fields)
        "reps" -> reps(fields)
        "rest" -> rest(fields)
        "hiit" -> hiit(fields)
        "emom" -> emom(fields)
        "amrap" -> amrap(fields)
        "cardio" -> cardio(fields)
        else -> refuse(path + "type", "expected one of ${ENTRY_TYPES.joinToString()}")
    }
}

private val ENTRY_TYPES = listOf("timed_hold", "reps", "rest", "hiit", "emom", "amrap", "cardio")

private val RPE = Bounds(min = 1.0, max = 10.0)

private val nonNegative: Read<Double> = { value, path -> number(value, path, Bounds.NONNEGATIVE) }

private val count: Read<Long> = { value, path -> integer(value, path, Bounds.NONNEGATIVE) }

private val positiveInteger: Read<Long> = { value, path -> integer(value, path, Bounds.POSITIVE) }

private fun SchemaReader.timedHold(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val sets =
        fields.required("sets") { v, p ->
            list(v, p) { item, at ->
                fields(item, at)?.let { set ->
                    val holdSec = set.required("hold_sec", nonNegative)
                    val restTakenSec = set.required("rest_taken_sec", nonNegative)
                    if (holdSec != null && restTakenSec != null) TimedHoldSetLog(holdSec, restTakenSec) else null
                }
            }
        }
    return if (exercise != null && sets != null) SessionEntry.TimedHold(exercise, sets) else null
}

private fun SchemaReader.reps(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val sets =
        fields.required("sets") { v, p ->
            list(v, p) { item, at ->
                fields(item, at)?.let { set ->
                    val reps = set.required("reps", count)
                    val weight = set.optional("weight", nonNegative)
                    val rpe = set.optional("rpe") { n, q -> number(n, q, RPE) }
                    val restTakenSec = set.required("rest_taken_sec", nonNegative)
                    if (reps != null && restTakenSec != null) RepsSetLog(reps, weight, rpe, restTakenSec) else null
                }
            }
        }
    return if (exercise != null && sets != null) SessionEntry.Reps(exercise, sets) else null
}

private fun SchemaReader.rest(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val restTakenSec = fields.required("rest_taken_sec", nonNegative)
    return if (exercise != null && restTakenSec != null) SessionEntry.Rest(exercise, restTakenSec) else null
}

private fun SchemaReader.hiit(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val roundsCompleted = fields.required("rounds_completed", count)
    return if (exercise != null && roundsCompleted != null) SessionEntry.Hiit(exercise, roundsCompleted) else null
}

private fun SchemaReader.emom(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val minutes =
        fields.required("minutes") { v, p ->
            list(v, p) { item, at ->
                fields(item, at)?.let { minute -> EmomMinuteLog(minute.optional("reps", count)) }
            }
        }
    return if (exercise != null && minutes != null) SessionEntry.Emom(exercise, minutes) else null
}

private fun SchemaReader.amrap(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val roundsCompleted = fields.required("rounds_completed", count)
    val extraReps = fields.optional("extra_reps", count)
    return if (exercise != null && roundsCompleted != null) {
        SessionEntry.Amrap(exercise, roundsCompleted, extraReps)
    } else {
        null
    }
}

private fun SchemaReader.cardio(fields: Fields): SessionEntry? {
    val exercise = fields.required("exercise", SchemaReader::id)
    val durationSec = fields.optional("duration_sec", nonNegative)
    val distanceMeters = fields.optional("distance_meters", nonNegative)
    return exercise?.let { SessionEntry.Cardio(it, durationSec, distanceMeters) }
}
