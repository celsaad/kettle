package com.casco.kettle.domain

/**
 * `rawLibrarySchema` and `rawWorkoutSchema` from `src/domain/schema.ts`. Exercises are read in
 * `ExerciseSchema.kt` and programs in `ProgramSchema.kt`; the writer for all of it is `LibraryYaml.kt`.
 */
internal fun SchemaReader.library(value: Any?): Library? {
    val fields = fields(value, ROOT) ?: return null
    val version = fields.required("version", positiveInteger)
    val exercises = fields.required("exercises") { v, p -> list(v, p) { item, at -> exercise(item, at) } }
    val workouts = fields.required("workouts") { v, p -> list(v, p) { item, at -> workout(item, at) } }
    val programs = fields.required("programs") { v, p -> list(v, p) { item, at -> program(item, at) } }
    return when {
        version == null || exercises == null -> null
        workouts == null || programs == null -> null
        else -> Library(version, exercises, workouts, programs)
    }
}

private fun SchemaReader.workout(
    value: Any?,
    path: Path,
): Workout? {
    val fields = fields(value, path) ?: return null
    val id = fields.required("id", SchemaReader::nonEmpty)
    val name = fields.required("name", SchemaReader::nonEmpty)
    val blocks = fields.required("blocks") { v, p -> list(v, p) { item, at -> block(item, at) } }
    return if (id != null && name != null && blocks != null) Workout(id, name, blocks) else null
}

/** A discriminated union on `type`, refused at `type` like an exercise's. */
private fun SchemaReader.block(
    value: Any?,
    path: Path,
): WorkoutBlock? {
    val fields = fields(value, path) ?: return null
    return when (fields.raw("type")) {
        "exercise" -> exerciseBlock(fields)
        "circuit" -> circuit(fields)
        else -> refuse(path + "type", "expected one of exercise, circuit")
    }
}

private fun SchemaReader.exerciseBlock(fields: Fields): WorkoutBlock? =
    member(fields)?.let { WorkoutBlock.ExerciseBlock(it.exerciseId, it.configOverride) }

/** An exercise and its optional `config:`, the same two keys in a plain block and a circuit member. */
private fun SchemaReader.member(fields: Fields): CircuitMember? {
    val exerciseId = fields.required("exercise", SchemaReader::nonEmpty)
    val config = fields.optional("config", blockConfig)
    return exerciseId?.let { CircuitMember(it, config) }
}

/**
 * A circuit needs two members, a round-robin of one being a plain block. Its members run one visit
 * per round whatever their own `sets`, so [MAX_ROUNDS] bounds it the way it bounds a HIIT.
 */
private fun SchemaReader.circuit(fields: Fields): WorkoutBlock? {
    val id = fields.optional("id", SchemaReader::nonEmpty)
    val rounds = fields.required("rounds", countUpTo(MAX_ROUNDS))
    val restBetweenExercisesSec = fields.optional("rest_between_exercises_sec", nonNegative)
    val restBetweenRoundsSec = fields.optional("rest_between_rounds_sec", nonNegative)
    val members =
        fields.required("exercises") { v, p ->
            list(v, p, minSize = MIN_CIRCUIT_MEMBERS) { item, at ->
                fields(item, at)?.let { member(it) }
            }
        }
    return if (rounds != null && members != null) {
        WorkoutBlock.Circuit(id, rounds, restBetweenExercisesSec, restBetweenRoundsSec, members)
    } else {
        null
    }
}

private const val MIN_CIRCUIT_MEMBERS = 2

/** `workoutBlockConfigOverrideSchema`: every key optional, so `config: {}` is an empty override, not none. */
private val blockConfig: Read<BlockConfigOverride> = { value, path ->
    fields(value, path)?.let { BlockConfigOverride(it.optional("duration_sec", nonNegative)) }
}
