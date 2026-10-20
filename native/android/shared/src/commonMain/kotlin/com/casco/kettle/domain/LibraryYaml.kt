package com.casco.kettle.domain

/**
 * The library file, as the library half of `src/domain/yaml-mapping.ts`. Its schema is split across
 * `LibrarySchema.kt`, `ExerciseSchema.kt` and `ProgramSchema.kt`, and all of it is held to
 * `conformance/library.yaml`.
 */
fun parseLibraryYaml(text: String): ParseResult<Library> = parseYaml(text) { library(it) }

/**
 * What [parseLibraryYaml] reads back unchanged, keyed as an author writes the file. An absent
 * optional is left out, and so is `rest_day` on a training week: writing `rest_day: false` on every
 * one would rewrite each program in the user's file the first time the app saved it.
 */
fun serializeLibraryYaml(library: Library): String =
    dumpYaml(
        linkedMapOf(
            "version" to library.version,
            "exercises" to library.exercises.map { it.toRaw() },
            "workouts" to library.workouts.map { it.toRaw() },
            "programs" to library.programs.map { it.toRaw() },
        ),
    )

private fun Exercise.toRaw(): Map<String, Any?> {
    val (type, raw) = config.toRaw()
    return optionals("id" to id, "name" to name, "type" to type, "config" to raw, "notes" to notes)
}

internal fun ExerciseConfig.toRaw(): Pair<String, Map<String, Any?>> =
    when (this) {
        is ExerciseConfig.Hiit -> {
            "hiit" to optionals("work_sec" to workSec, "rest_sec" to restSec, "rounds" to rounds)
        }

        is ExerciseConfig.Emom -> {
            "emom" to
                optionals("interval_sec" to intervalSec, "total_minutes" to totalMinutes, "target_reps" to targetReps)
        }

        is ExerciseConfig.Amrap -> {
            "amrap" to optionals("time_cap_sec" to timeCapSec)
        }

        is ExerciseConfig.Reps -> {
            "reps" to
                optionals(
                    "sets" to sets,
                    "target_reps_min" to targetRepsMin,
                    "target_reps_max" to targetRepsMax,
                    "target_weight" to targetWeightKg,
                    "rest_sec" to restSec,
                )
        }

        is ExerciseConfig.TimedHold -> {
            "timed_hold" to
                optionals(
                    "sets" to sets,
                    "hold_sec_min" to holdSecMin,
                    "hold_sec_max" to holdSecMax,
                    "rest_sec" to restSec,
                )
        }

        is ExerciseConfig.Cardio -> {
            "cardio" to optionals("duration_sec" to durationSec, "distance_meters" to distanceMeters)
        }

        is ExerciseConfig.Rest -> {
            "rest" to optionals("duration_sec" to durationSec)
        }
    }

private fun Workout.toRaw(): Map<String, Any?> =
    optionals(
        "id" to id,
        "name" to name,
        "blocks" to blocks.map { it.toRaw() },
    )

internal fun WorkoutBlock.toRaw(): Map<String, Any?> =
    when (this) {
        is WorkoutBlock.ExerciseBlock -> {
            optionals("type" to "exercise", "exercise" to exerciseId, "config" to configOverride?.toRaw())
        }

        is WorkoutBlock.Circuit -> {
            optionals(
                "type" to "circuit",
                "id" to id,
                "rounds" to rounds,
                "rest_between_exercises_sec" to restBetweenExercisesSec,
                "rest_between_rounds_sec" to restBetweenRoundsSec,
                "exercises" to
                    members.map { optionals("exercise" to it.exerciseId, "config" to it.configOverride?.toRaw()) },
            )
        }
    }

private fun BlockConfigOverride.toRaw(): Map<String, Any?> = optionals("duration_sec" to durationSec)

private fun Program.toRaw(): Map<String, Any?> =
    optionals(
        "id" to id,
        "name" to name,
        "weeks" to weeks.map { it.toRaw() },
    )

private fun ProgramWeek.toRaw(): Map<String, Any?> =
    when (this) {
        is ProgramWeek.Training -> {
            optionals(
                "week" to week,
                "day" to day,
                "workout" to workoutId,
                "notes" to notes,
                "overrides" to overrides?.map { it.toRaw() },
            )
        }

        is ProgramWeek.RestDay -> {
            optionals("week" to week, "day" to day, "rest_day" to true, "notes" to notes)
        }
    }

private fun ProgramOverride.toRaw(): Map<String, Any?> {
    val raw = config.mapValues { (_, value) -> value.toRaw() }
    return when (this) {
        is ProgramOverride.ExerciseOverride -> optionals("exercise" to exerciseId, "config" to raw)
        is ProgramOverride.BlockOverride -> optionals("block" to blockId, "config" to raw)
    }
}
