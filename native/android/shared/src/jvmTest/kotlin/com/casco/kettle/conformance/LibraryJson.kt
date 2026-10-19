package com.casco.kettle.conformance

import com.casco.kettle.domain.BlockConfigOverride
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock

/**
 * The library's camelCase domain model as the goldens hold it, written out by hand like
 * `sessionJson`, so the corpus is checked against its own spelling of every key rather than the
 * writer's. An absent optional is left out, never written as null.
 */
internal fun libraryJson(library: Library): Map<String, Any?> =
    mapOf(
        "version" to library.version,
        "exercises" to library.exercises.map(::exerciseJson),
        "workouts" to library.workouts.map(::workoutJson),
        "programs" to library.programs.map(::programJson),
    )

private fun exerciseJson(exercise: Exercise): Map<String, Any?> =
    buildMap {
        put("id", exercise.id)
        put("name", exercise.name)
        exercise.notes?.let { put("notes", it) }
        val (type, config) = configJson(exercise.config)
        put("type", type)
        put("config", config)
    }

private fun configJson(config: ExerciseConfig): Pair<String, Map<String, Any?>> =
    when (config) {
        is ExerciseConfig.Hiit -> {
            "hiit" to mapOf("workSec" to config.workSec, "restSec" to config.restSec, "rounds" to config.rounds)
        }

        is ExerciseConfig.Emom -> {
            "emom" to
                optionals(
                    "intervalSec" to config.intervalSec,
                    "totalMinutes" to config.totalMinutes,
                    "targetReps" to config.targetReps,
                )
        }

        is ExerciseConfig.Amrap -> {
            "amrap" to mapOf("timeCapSec" to config.timeCapSec)
        }

        is ExerciseConfig.Reps -> {
            "reps" to
                optionals(
                    "sets" to config.sets,
                    "targetRepsMin" to config.targetRepsMin,
                    "targetRepsMax" to config.targetRepsMax,
                    "targetWeightKg" to config.targetWeightKg,
                    "restSec" to config.restSec,
                )
        }

        is ExerciseConfig.TimedHold -> {
            "timed_hold" to
                optionals(
                    "sets" to config.sets,
                    "holdSecMin" to config.holdSecMin,
                    "holdSecMax" to config.holdSecMax,
                    "restSec" to config.restSec,
                )
        }

        is ExerciseConfig.Cardio -> {
            "cardio" to optionals("durationSec" to config.durationSec, "distanceMeters" to config.distanceMeters)
        }

        is ExerciseConfig.Rest -> {
            "rest" to mapOf("durationSec" to config.durationSec)
        }
    }

private fun workoutJson(workout: Workout): Map<String, Any?> =
    mapOf("id" to workout.id, "name" to workout.name, "blocks" to workout.blocks.map(::blockJson))

private fun blockJson(block: WorkoutBlock): Map<String, Any?> =
    when (block) {
        is WorkoutBlock.ExerciseBlock -> {
            optionals(
                "kind" to "exercise",
                "exerciseId" to block.exerciseId,
                "configOverride" to block.configOverride?.let(::overrideJson),
            )
        }

        is WorkoutBlock.Circuit -> {
            optionals(
                "kind" to "circuit",
                "id" to block.id,
                "rounds" to block.rounds,
                "restBetweenExercisesSec" to block.restBetweenExercisesSec,
                "restBetweenRoundsSec" to block.restBetweenRoundsSec,
                "members" to block.members.map(::memberJson),
            )
        }
    }

private fun memberJson(member: CircuitMember): Map<String, Any?> =
    optionals("exerciseId" to member.exerciseId, "configOverride" to member.configOverride?.let(::overrideJson))

private fun overrideJson(override: BlockConfigOverride): Map<String, Any?> =
    optionals("durationSec" to override.durationSec)

private fun programJson(program: Program): Map<String, Any?> =
    mapOf("id" to program.id, "name" to program.name, "weeks" to program.weeks.map(::weekJson))

private fun weekJson(week: ProgramWeek): Map<String, Any?> =
    when (week) {
        is ProgramWeek.Training -> {
            optionals(
                "week" to week.week,
                "day" to week.day,
                "workoutId" to week.workoutId,
                "notes" to week.notes,
                "overrides" to week.overrides?.map(::programOverrideJson),
            )
        }

        is ProgramWeek.RestDay -> {
            optionals("week" to week.week, "day" to week.day, "restDay" to true, "notes" to week.notes)
        }
    }

private fun programOverrideJson(override: ProgramOverride): Map<String, Any?> {
    val config = override.config.mapValues { (_, value) -> valueJson(value) }
    return when (override) {
        is ProgramOverride.ExerciseOverride -> {
            mapOf("kind" to "exercise", "exerciseId" to override.exerciseId, "config" to config)
        }

        is ProgramOverride.BlockOverride -> {
            mapOf("kind" to "block", "blockId" to override.blockId, "config" to config)
        }
    }
}

private fun valueJson(value: OverrideValue): Any =
    when (value) {
        is OverrideValue.Numeric -> value.value
        is OverrideValue.Text -> value.value
    }

/** A map with its null entries left out: every nullable here is an optional, absent rather than null. */
private fun optionals(vararg entries: Pair<String, Any?>): Map<String, Any?> =
    entries.filter { it.second != null }.toMap()
