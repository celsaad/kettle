package com.casco.kettle.conformance

import com.casco.kettle.domain.BlockConfigOverride
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MergeError
import com.casco.kettle.domain.MergeResult
import com.casco.kettle.domain.OverrideMerge
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.ResolvedWeek
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.optionals

/**
 * The library's camelCase domain model as the goldens hold it, written out by hand like
 * `sessionJson`, so the corpus is checked against its own spelling of every key rather than the
 * writer's. An absent optional is left out, never written as null, through the writer's own
 * [optionals]: a helper that only drops nulls has no spelling to get wrong.
 */
internal fun libraryJson(library: Library): Map<String, Any?> =
    mapOf(
        "version" to library.version,
        "exercises" to library.exercises.map(::exerciseJson),
        "workouts" to library.workouts.map(::workoutJson),
        "programs" to library.programs.map(::programJson),
    )

internal fun exerciseJson(exercise: Exercise): Map<String, Any?> =
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

internal fun workoutJson(workout: Workout): Map<String, Any?> =
    mapOf("id" to workout.id, "name" to workout.name, "blocks" to workout.blocks.map(::blockJson))

internal fun blockJson(block: WorkoutBlock): Map<String, Any?> =
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

/** A merge result in the goldens' shape. A refusal's `day` is left out when the week has none. */
internal fun mergeJson(result: MergeResult): Map<String, Any?> =
    when (result) {
        is MergeResult.Ok -> {
            mapOf(
                "ok" to true,
                "library" to libraryJson(result.library),
                "summary" to
                    mapOf(
                        "newExercises" to result.summary.newExercises,
                        "updatedExercises" to result.summary.updatedExercises,
                        "newWorkouts" to result.summary.newWorkouts,
                        "updatedWorkouts" to result.summary.updatedWorkouts,
                        "newPrograms" to result.summary.newPrograms,
                        "updatedPrograms" to result.summary.updatedPrograms,
                    ),
            )
        }

        is MergeResult.Refused -> {
            mapOf("ok" to false, "error" to mergeErrorJson(result.error))
        }
    }

private fun mergeErrorJson(error: MergeError): Map<String, Any?> =
    when (error) {
        is MergeError.UnknownExercise -> {
            mapOf("kind" to "unknownExercise", "workoutId" to error.workoutId, "exerciseId" to error.exerciseId)
        }

        is MergeError.UnknownWorkout -> {
            optionals(
                "kind" to "unknownWorkout",
                "programId" to error.programId,
                "week" to error.week,
                "day" to error.day,
                "workoutId" to error.workoutId,
            )
        }
    }

/** A corpus op's `config:` as the patch it stands for: every value a number or a string. */
internal fun overrideConfig(config: Map<*, *>): Map<String, OverrideValue> =
    config.entries.associate { (key, value) ->
        key as String to
            if (value is Number) OverrideValue.Numeric(value.toDouble()) else OverrideValue.Text(value as String)
    }

internal fun patchJson(patch: Map<String, OverrideValue>): Map<String, Any> =
    patch.mapValues { (_, value) -> valueJson(value) }

/**
 * TS's `asAuthored` is the target itself with the patch spread over it. Here it is only the patched
 * fields, raw, since a value like `sets: "abc"` has no typed home, so the goldens' whole object is put
 * back together from the target's own JSON with those fields laid over it.
 */
internal fun exerciseMergeJson(
    target: Exercise,
    merge: OverrideMerge<Exercise>,
): Map<String, Any?> =
    overrideMergeJson(
        merge,
        exerciseJson(merge.effective),
        exerciseJson(target) + ("config" to camelCase(merge.asAuthored)),
    )

internal fun blockMergeJson(
    target: WorkoutBlock,
    merge: OverrideMerge<WorkoutBlock>,
): Map<String, Any?> =
    overrideMergeJson(
        merge,
        blockJson(merge.effective),
        blockJson(target) + camelCase(merge.asAuthored),
    )

private fun overrideMergeJson(
    merge: OverrideMerge<*>,
    effective: Map<String, Any?>,
    asAuthored: Map<String, Any?>,
): Map<String, Any?> =
    mapOf(
        "effective" to effective,
        "asAuthored" to asAuthored,
        "report" to mapOf("refused" to merge.report.refused, "unknownKeys" to merge.report.unknownKeys),
    )

/**
 * A raw patch keyed as the domain model is. Every key but one is its snake_case name in camelCase;
 * `target_weight` is the exception, because the domain names the unit every stored weight is in.
 */
private fun camelCase(patch: Map<String, OverrideValue>): Map<String, Any?> =
    patch.entries.associate { (key, value) ->
        val name =
            if (key == "target_weight") {
                "targetWeightKg"
            } else {
                key.replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
            }
        name to valueJson(value)
    }

internal fun resolvedJson(resolved: ResolvedWeek): Map<String, Any?> =
    mapOf("workout" to workoutJson(resolved.workout), "exercises" to resolved.exercises.map(::exerciseJson))
