package com.casco.kettle.domain

/**
 * `rawSessionSchema` from `src/domain/schema.ts`, read straight into the camelCase domain model, so
 * the snake_case names live here and in the writer in `SessionYaml.kt` (the corpus runner spells
 * them out again on purpose, as its own oracle).
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

/**
 * zod's `discriminatedUnion('type', …)`. An unknown or missing type is refused at `type` itself, and
 * nothing else in the entry is read, so it reports no other path.
 */
private fun SchemaReader.entry(
    value: Any?,
    path: Path,
): SessionEntry? {
    val fields = fields(value, path) ?: return null
    val read = entryReaders[fields.raw("type")]
    return if (read == null) {
        refuse(path + "type", "expected one of ${entryReaders.keys.joinToString()}")
    } else {
        read(fields, fields.required("exercise", SchemaReader::id))
    }
}

/**
 * The entry types, as one table, so the dispatch and the refusal's list can't disagree. The writer
 * in `SessionYaml.kt` names them again in an exhaustive `when`. The corpus's `every-entry-type` case
 * round-trips all seven, so a name that differs between the two fails there.
 */
private val entryReaders: Map<String, SchemaReader.(Fields, String?) -> SessionEntry?> =
    mapOf(
        "timed_hold" to SchemaReader::timedHold,
        "reps" to SchemaReader::reps,
        "rest" to SchemaReader::rest,
        "hiit" to SchemaReader::hiit,
        "emom" to SchemaReader::emom,
        "amrap" to SchemaReader::amrap,
        "cardio" to SchemaReader::cardio,
    )

private val RPE = Bounds(min = 1.0, max = 10.0)

private val nonNegativeInteger: Read<Long> = { value, path -> integer(value, path, Bounds.NONNEGATIVE) }

private fun SchemaReader.timedHold(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
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

private fun SchemaReader.reps(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
    val sets =
        fields.required("sets") { v, p ->
            list(v, p) { item, at ->
                fields(item, at)?.let { set ->
                    val reps = set.required("reps", nonNegativeInteger)
                    val weight = set.optional("weight", nonNegative)
                    val rpe = set.optional("rpe") { n, q -> number(n, q, RPE) }
                    val restTakenSec = set.required("rest_taken_sec", nonNegative)
                    if (reps != null && restTakenSec != null) RepsSetLog(reps, weight, rpe, restTakenSec) else null
                }
            }
        }
    return if (exercise != null && sets != null) SessionEntry.Reps(exercise, sets) else null
}

private fun SchemaReader.rest(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
    val restTakenSec = fields.required("rest_taken_sec", nonNegative)
    return if (exercise != null && restTakenSec != null) SessionEntry.Rest(exercise, restTakenSec) else null
}

private fun SchemaReader.hiit(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
    val roundsCompleted = fields.required("rounds_completed", nonNegativeInteger)
    return if (exercise != null && roundsCompleted != null) SessionEntry.Hiit(exercise, roundsCompleted) else null
}

private fun SchemaReader.emom(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
    val minutes =
        fields.required("minutes") { v, p ->
            list(v, p) { item, at ->
                fields(item, at)?.let { minute -> EmomMinuteLog(minute.optional("reps", nonNegativeInteger)) }
            }
        }
    return if (exercise != null && minutes != null) SessionEntry.Emom(exercise, minutes) else null
}

private fun SchemaReader.amrap(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
    val roundsCompleted = fields.required("rounds_completed", nonNegativeInteger)
    val extraReps = fields.optional("extra_reps", nonNegativeInteger)
    return if (exercise != null && roundsCompleted != null) {
        SessionEntry.Amrap(exercise, roundsCompleted, extraReps)
    } else {
        null
    }
}

private fun SchemaReader.cardio(
    fields: Fields,
    exercise: String?,
): SessionEntry? {
    val durationSec = fields.optional("duration_sec", nonNegative)
    val distanceMeters = fields.optional("distance_meters", nonNegative)
    return exercise?.let { SessionEntry.Cardio(it, durationSec, distanceMeters) }
}
