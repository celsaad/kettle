package com.casco.kettle.domain

/**
 * What a workout is made of, as `WorkoutShape` in TS was: structured rather than a sentence, so the
 * view composes and translates it. [types] are the distinct non-rest types, in first-seen order.
 *
 * No duration (#412): the estimate counted a reps set as its rest alone, the one part of a set that
 * isn't lifting, so a strength workout read as a few minutes. Nothing else knows how long a set takes.
 */
data class WorkoutShape(
    val blockCount: Int,
    val types: List<ExerciseType>,
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
    for (block in workout.blocks) {
        blockConfigs(block, byId).filter { it !is ExerciseConfig.Rest }.forEach { types += it.type }
    }
    return WorkoutShape(workout.blocks.size, types.toList())
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
