package com.casco.kettle.domain

/**
 * The library file's domain model: exercises, workouts and programs, as `Library` in
 * `src/domain/types.ts`. Optional fields follow `Session.kt`'s rule: a nullable property whose null
 * means *absent*.
 *
 * Integers are `Long` and other numbers `Double`, for the reason `SchemaReader.integer` gives.
 */
data class Library(
    val version: Long,
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val programs: List<Program>,
)

/**
 * One exercise. TS spells its type as a `type` discriminant beside a matching config; here the
 * config's class is the type, so the two can't disagree.
 */
data class Exercise(
    val id: String,
    val name: String,
    val config: ExerciseConfig,
    val notes: String? = null,
)

/** What one exercise type is configured with. [Reps.targetWeightKg] is kilograms, as every stored weight. */
sealed interface ExerciseConfig {
    data class Hiit(
        val workSec: Double,
        val restSec: Double,
        val rounds: Long,
    ) : ExerciseConfig

    data class Emom(
        val intervalSec: Double,
        val totalMinutes: Double,
        val targetReps: Long? = null,
    ) : ExerciseConfig

    data class Amrap(
        val timeCapSec: Double,
    ) : ExerciseConfig

    /** [targetRepsMax] is absent for a fixed target, and set for a double-progression range. */
    data class Reps(
        val sets: Long,
        val targetRepsMin: Long,
        val targetRepsMax: Long? = null,
        val targetWeightKg: Double? = null,
        val restSec: Double,
    ) : ExerciseConfig

    /**
     * Neither target is a max-effort hold, [holdSecMin] alone a fixed target, and both a range. The
     * schema refuses [holdSecMax] without [holdSecMin].
     */
    data class TimedHold(
        val sets: Long,
        val holdSecMin: Double? = null,
        val holdSecMax: Double? = null,
        val restSec: Double,
    ) : ExerciseConfig

    data class Cardio(
        val durationSec: Double? = null,
        val distanceMeters: Double? = null,
    ) : ExerciseConfig

    data class Rest(
        val durationSec: Double,
    ) : ExerciseConfig
}

/** A block's or member's own `config:`. Present but empty is not the same value as absent. */
data class BlockConfigOverride(
    val durationSec: Double? = null,
)

data class CircuitMember(
    val exerciseId: String,
    val configOverride: BlockConfigOverride? = null,
)

/** One exercise, or a circuit run round-robin for [Circuit.rounds] rounds. */
sealed interface WorkoutBlock {
    data class ExerciseBlock(
        val exerciseId: String,
        val configOverride: BlockConfigOverride? = null,
    ) : WorkoutBlock

    /** [id] is only there so a program week's overrides can target this circuit's own params. */
    data class Circuit(
        val id: String? = null,
        val rounds: Long,
        val restBetweenExercisesSec: Double? = null,
        val restBetweenRoundsSec: Double? = null,
        val members: List<CircuitMember>,
    ) : WorkoutBlock
}

data class Workout(
    val id: String,
    val name: String,
    val blocks: List<WorkoutBlock>,
)

data class Program(
    val id: String,
    val name: String,
    val weeks: List<ProgramWeek>,
)

/**
 * One slot in a program's rotation: a workout to run, or a scheduled day off. Two classes rather than
 * a flag for the reason `ProgramWeek` in `types.ts` gives: a rest week has no workout to look up.
 */
sealed interface ProgramWeek {
    val week: Long
    val day: String?
    val notes: String?

    data class Training(
        override val week: Long,
        override val day: String? = null,
        val workoutId: String,
        override val notes: String? = null,
        val overrides: List<ProgramOverride>? = null,
    ) : ProgramWeek

    data class RestDay(
        override val week: Long,
        override val day: String? = null,
        override val notes: String? = null,
    ) : ProgramWeek
}

/**
 * A week's patch to one exercise or one circuit block. [config] stays the *raw* snake_case patch it
 * was written as, keyed like the YAML it patches (`sets`, `rest_sec`), and isn't checked until it is
 * applied. That asymmetry with the camelCase rest of the model is deliberate (`AGENTS.md`).
 */
sealed interface ProgramOverride {
    val config: Map<String, OverrideValue>

    data class ExerciseOverride(
        val exerciseId: String,
        override val config: Map<String, OverrideValue>,
    ) : ProgramOverride

    data class BlockOverride(
        val blockId: String,
        override val config: Map<String, OverrideValue>,
    ) : ProgramOverride
}

/** One value of an override's patch: `number | string` in TS. */
sealed interface OverrideValue {
    data class Numeric(
        val value: Double,
    ) : OverrideValue

    data class Text(
        val value: String,
    ) : OverrideValue
}
