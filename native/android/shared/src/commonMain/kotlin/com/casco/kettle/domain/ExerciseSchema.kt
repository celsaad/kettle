package com.casco.kettle.domain

/**
 * An exercise, read straight into the domain model as
 * `SessionSchema.kt` reads a session. The writer is in `LibraryYaml.kt`.
 */
internal fun SchemaReader.exercise(
    value: Any?,
    path: Path,
): Exercise? {
    val fields = fields(value, path) ?: return null
    // zod's discriminated union: an unknown or missing type is refused at `type`, and nothing else is read.
    return when (val readConfig = configReaders[fields.raw("type")]) {
        null -> {
            refuse(path + "type", "expected one of ${configReaders.keys.joinToString()}")
        }

        else -> {
            val id = fields.required("id", SchemaReader::nonEmpty)
            val name = fields.required("name", SchemaReader::nonEmpty)
            val config = fields.required("config") { v, p -> fields(v, p)?.let { readConfig(it) } }
            val notes = fields.optional("notes", SchemaReader::string)
            // Absent is none; present and refused is null, which refuses the exercise.
            val equipment = if (fields.has("equipment")) fields.optional("equipment", equipmentList) else emptyList()
            equipment?.let {
                if (id != null && name != null && config != null) Exercise(id, name, config, notes, it) else null
            }
        }
    }
}

/**
 * The types, as one table, so the dispatch and the refusal's list can't disagree. The writer names
 * them again in `LibraryYaml.kt`, and the corpus's `every-shape` round-trips every type.
 */
private val configReaders: Map<String, SchemaReader.(Fields) -> ExerciseConfig?> =
    mapOf(
        ExerciseType.Hiit.yamlName to SchemaReader::hiit,
        ExerciseType.Emom.yamlName to SchemaReader::emom,
        ExerciseType.Amrap.yamlName to SchemaReader::amrap,
        ExerciseType.Reps.yamlName to SchemaReader::reps,
        ExerciseType.TimedHold.yamlName to SchemaReader::timedHold,
        ExerciseType.Cardio.yamlName to SchemaReader::cardio,
        ExerciseType.Rest.yamlName to SchemaReader::rest,
    )

/**
 * One type's config on its own, as merging an override validates it. [type] is the writer's name for
 * a config that already exists, so it is always in the table.
 */
internal fun SchemaReader.exerciseConfig(
    type: String,
    fields: Fields,
): ExerciseConfig? = configReaders.getValue(type)(fields)

/**
 * `equipment:`, at most [MAX_EQUIPMENT_ITEMS] items of 1 to [MAX_EQUIPMENT_LENGTH] characters each,
 * counted after trimming and in code points, as JSON Schema's `maxLength` counts, and none with a
 * line break inside it. Any spelling is accepted; two that match the same item are one, the first
 * kept, and the ceiling on items counts what was written, before they collapse. Neither ceiling
 * reports a [SchemaReader.ceilings] maximum, since repair clamps numbers and can't shorten a list.
 */
private val equipmentList: Read<List<String>> = { value, path ->
    list(value, path, maxSize = MAX_EQUIPMENT_ITEMS) { item, at ->
        string(item, at)?.let(::trimEquipment)?.let { trimmed ->
            when {
                trimmed.isEmpty() -> {
                    refuse(at, "must not be blank", fatal = false)
                }

                codePoints(trimmed).size > MAX_EQUIPMENT_LENGTH -> {
                    refuse(at, "must be at most $MAX_EQUIPMENT_LENGTH characters", fatal = false)
                }

                // One item is one line: a break inside it is two items written as one, or a pasted note.
                trimmed.any { it in LINE_BREAKS } -> {
                    refuse(at, "must not contain a line break", fatal = false)
                }

                else -> {
                    trimmed
                }
            }
        }
    }?.let(::distinctEquipment)
}

/** ECMAScript's LineTerminator: what JSON Schema's pattern for an item refuses inside one. */
private const val LINE_BREAKS = "\n\r\u2028\u2029"

/** `int().positive().max(…)`, for the counts the runner builds a step per unit of. */
internal fun countUpTo(max: Int): Read<Long> =
    { value, path -> integer(value, path, Bounds(min = 0.0, minInclusive = false, max = max.toDouble())) }

private val totalMinutes: Read<Double> =
    { value, path -> number(value, path, Bounds(min = 0.0, minInclusive = false, max = MAX_TOTAL_MINUTES.toDouble())) }

private fun SchemaReader.hiit(fields: Fields): ExerciseConfig? {
    val workSec = fields.required("work_sec", positive)
    val restSec = fields.required("rest_sec", nonNegative)
    val rounds = fields.required("rounds", countUpTo(MAX_ROUNDS))
    return when {
        workSec == null || restSec == null || rounds == null -> null
        else -> ExerciseConfig.Hiit(workSec, restSec, rounds)
    }
}

/**
 * The refinement bounds the *floored* interval count, never the raw quotient: see
 * [emomIntervalCount]. It runs even when `interval_sec` was refused for its range, where a zero
 * interval is infinitely many intervals (or, over zero minutes, NaN), and refuses at the same path.
 * Written as "not at most", as TS is, so a NaN is refused.
 */
private fun SchemaReader.emom(fields: Fields): ExerciseConfig? =
    refined(fields, {
        val intervalSec = fields.required("interval_sec", positive)
        val totalMinutes = fields.required("total_minutes", totalMinutes)
        val targetReps = fields.optional("target_reps", positiveInteger)
        when {
            intervalSec == null || totalMinutes == null -> null
            else -> ExerciseConfig.Emom(intervalSec, totalMinutes, targetReps)
        }
    }) {
        val count = emomIntervalCount(it.rawNumber("interval_sec")!!, it.rawNumber("total_minutes")!!)
        if (!(count <= MAX_ROUNDS)) {
            refuse(it.path + "interval_sec", "must come to at most $MAX_ROUNDS intervals", fatal = false)
        }
    }

private fun SchemaReader.amrap(fields: Fields): ExerciseConfig? =
    fields.required("time_cap_sec", positive)?.let { ExerciseConfig.Amrap(it) }

private fun SchemaReader.reps(fields: Fields): ExerciseConfig? =
    refined(fields, {
        val sets = fields.required("sets", countUpTo(MAX_SETS))
        val targetRepsMin = fields.required("target_reps_min", positiveInteger)
        val targetRepsMax = fields.optional("target_reps_max", positiveInteger)
        val targetWeightKg = fields.optional("target_weight", nonNegative)
        val restSec = fields.required("rest_sec", nonNegative)
        if (sets != null && targetRepsMin != null && restSec != null) {
            ExerciseConfig.Reps(sets, targetRepsMin, targetRepsMax, targetWeightKg, restSec)
        } else {
            null
        }
    }) {
        val max = it.rawNumber("target_reps_max")
        if (max != null && max < it.rawNumber("target_reps_min")!!) {
            refuse(it.path + "target_reps_max", "must be >= target_reps_min", fatal = false)
        }
    }

/** A bare `hold_sec_max` is refused rather than read as a fixed target: one meaning, one spelling. */
private fun SchemaReader.timedHold(fields: Fields): ExerciseConfig? =
    refined(fields, {
        val sets = fields.required("sets", countUpTo(MAX_SETS))
        val holdSecMin = fields.optional("hold_sec_min", positive)
        val holdSecMax = fields.optional("hold_sec_max", positive)
        val restSec = fields.required("rest_sec", nonNegative)
        if (sets != null && restSec != null) ExerciseConfig.TimedHold(sets, holdSecMin, holdSecMax, restSec) else null
    }) {
        val min = it.rawNumber("hold_sec_min")
        val max = it.rawNumber("hold_sec_max")
        when {
            max != null && min == null -> {
                refuse(it.path + "hold_sec_max", "needs hold_sec_min", fatal = false)
            }

            max != null && min != null && max < min -> {
                refuse(it.path + "hold_sec_max", "must be >= hold_sec_min", fatal = false)
            }
        }
    }

private fun SchemaReader.cardio(fields: Fields): ExerciseConfig =
    ExerciseConfig.Cardio(fields.optional("duration_sec", positive), fields.optional("distance_meters", positive))

private fun SchemaReader.rest(fields: Fields): ExerciseConfig? =
    fields.required("duration_sec", nonNegative)?.let { ExerciseConfig.Rest(it) }
