package com.casco.kettle.domain

import kotlin.math.roundToLong

/**
 * What a workout is made of, as `WorkoutShape` in TS was: structured rather than a sentence, so the
 * view composes and translates it. [types] are the distinct non-rest types, in first-seen order.
 */
data class WorkoutShape(
    val blockCount: Int,
    val types: List<ExerciseType>,
    val estimatedMinutes: Long,
)

/**
 * An exercise's type, the YAML's `type:`, as its own value so a `when` over it is checked for every
 * one: a type added to [ExerciseConfig] fails to compile here and in each label, rather than reading
 * as some other type.
 */
enum class ExerciseType { Hiit, Emom, Amrap, Reps, TimedHold, Cardio, Rest }

/**
 * The `type:` value a file spells for each type, in the library and in a session alike: the one
 * spelling the readers, the writers and the assistant brief all use, so a rename is one line.
 */
val ExerciseType.yamlName: String
    get() =
        when (this) {
            ExerciseType.Hiit -> "hiit"
            ExerciseType.Emom -> "emom"
            ExerciseType.Amrap -> "amrap"
            ExerciseType.Reps -> "reps"
            ExerciseType.TimedHold -> "timed_hold"
            ExerciseType.Cardio -> "cardio"
            ExerciseType.Rest -> "rest"
        }

val ExerciseConfig.type: ExerciseType
    get() =
        when (this) {
            is ExerciseConfig.Hiit -> ExerciseType.Hiit
            is ExerciseConfig.Emom -> ExerciseType.Emom
            is ExerciseConfig.Amrap -> ExerciseType.Amrap
            is ExerciseConfig.Reps -> ExerciseType.Reps
            is ExerciseConfig.TimedHold -> ExerciseType.TimedHold
            is ExerciseConfig.Cardio -> ExerciseType.Cardio
            is ExerciseConfig.Rest -> ExerciseType.Rest
        }

/** As `workoutShape` in RN's `state/selectors/workout-shape.ts`, which went with RN Home (#235). */
fun workoutShape(
    workout: Workout,
    exercises: List<Exercise>,
): WorkoutShape {
    val byId = exercises.associateBy { it.id }
    val types = linkedSetOf<ExerciseType>()
    var totalSec = 0.0
    for (block in workout.blocks) {
        blockConfigs(block, byId).filter { it !is ExerciseConfig.Rest }.forEach { types += it.type }
        totalSec += blockSeconds(block, byId)
    }
    return WorkoutShape(workout.blocks.size, types.toList(), maxOf(1, (totalSec / SECONDS_PER_MINUTE).roundToLong()))
}

/** A block's configs, leaving out a member whose exercise is gone, so it adds no type. */
private fun blockConfigs(
    block: WorkoutBlock,
    byId: Map<String, Exercise>,
): List<ExerciseConfig> =
    when (block) {
        is WorkoutBlock.ExerciseBlock -> listOfNotNull(byId[block.exerciseId]?.config)
        is WorkoutBlock.Circuit -> block.members.mapNotNull { byId[it.exerciseId]?.config }
    }

private fun blockSeconds(
    block: WorkoutBlock,
    byId: Map<String, Exercise>,
): Double =
    when (block) {
        is WorkoutBlock.ExerciseBlock -> {
            byId[block.exerciseId]?.config?.let { exerciseSeconds(it, block.configOverride?.durationSec) } ?: 0.0
        }

        is WorkoutBlock.Circuit -> {
            val members = blockConfigs(block, byId)
            val round =
                members.sumOf(::memberVisitSeconds) +
                    maxOf(0, members.size - 1) * (block.restBetweenExercisesSec ?: 0.0)
            block.rounds * round + maxOf(0L, block.rounds - 1) * (block.restBetweenRoundsSec ?: 0.0)
        }
    }

/**
 * A hold is estimated from the top of its range, since that is where it now ends itself. A max-effort
 * hold has no end to estimate and adds only its rest, as cardio with no duration adds nothing.
 */
private fun exerciseSeconds(
    config: ExerciseConfig,
    overrideDurationSec: Double?,
): Double =
    when (config) {
        is ExerciseConfig.Hiit -> config.rounds * (config.workSec + config.restSec)
        is ExerciseConfig.TimedHold -> config.sets * config.holdTop() + (config.sets - 1) * config.restSec
        is ExerciseConfig.Reps -> config.sets * config.restSec
        is ExerciseConfig.Emom -> config.totalMinutes * SECONDS_PER_MINUTE
        is ExerciseConfig.Amrap -> config.timeCapSec
        is ExerciseConfig.Cardio -> config.durationSec ?: 0.0
        is ExerciseConfig.Rest -> overrideDurationSec ?: config.durationSec
    }

/** A member's cost per visit to it in a circuit round: one hold or rep pass, not the exercise's own sets. */
private fun memberVisitSeconds(config: ExerciseConfig): Double =
    when (config) {
        is ExerciseConfig.TimedHold -> config.holdTop()
        is ExerciseConfig.Reps -> config.restSec
        else -> exerciseSeconds(config, null)
    }

private fun ExerciseConfig.TimedHold.holdTop() = holdSecMax ?: holdSecMin ?: 0.0
