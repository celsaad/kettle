package com.casco.kettle.domain

/** Whether [workout] runs [exerciseId], as a block of its own or as a circuit member. */
fun workoutRunsExercise(
    workout: Workout?,
    exerciseId: String,
): Boolean =
    workout?.blocks.orEmpty().any { block ->
        when (block) {
            is WorkoutBlock.ExerciseBlock -> block.exerciseId == exerciseId
            is WorkoutBlock.Circuit -> block.members.any { it.exerciseId == exerciseId }
        }
    }

/** What a week's override patches, as a reader would name it. */
sealed interface OverrideTarget {
    /** An exercise the week runs, by its name. */
    data class Exercise(
        val name: String,
    ) : OverrideTarget

    /** A circuit in the week's workout, by the id the override names it with. */
    data class Circuit(
        val id: String,
    ) : OverrideTarget

    /** Nothing in the week matches [id], so none of the patch applies. */
    data class Missing(
        val id: String,
    ) : OverrideTarget
}

/** One patched key. [unknown] is a key the target has no such setting for, dropped on its own. */
data class OverrideKey(
    val key: String,
    val value: OverrideValue,
    val unknown: Boolean,
)

/**
 * One override, for display, as RN's `overrideLines` renders it: ported from `program-detail.tsx`, and
 * kept in RN by `program-override-editor.tsx` until the library flip.
 * [refused] is a patch the schema refuses merged, so none of it applies and no key is marked unknown.
 * A [OverrideTarget.Missing] target is never refused and has no unknown keys either: there was nothing
 * to merge into.
 */
data class OverrideNote(
    val target: OverrideTarget,
    val keys: List<OverrideKey>,
    val refused: Boolean,
)

/**
 * What [override] does to the week that runs [workout]: the display half of [mergeExerciseOverride]'s
 * and [mergeBlockOverride]'s silent fallbacks. Without it a program states a change the week doesn't
 * make, which reads worst on a file the schema imports anyway, since an override's `config` is a free
 * record.
 *
 * Scoped to the week's own workout, not the library, for both kinds. An exercise override naming
 * something the week doesn't run merges cleanly and changes nothing, so resolved against the library
 * it showed as applied.
 */
fun overrideNote(
    override: ProgramOverride,
    library: Library,
    workout: Workout?,
): OverrideNote {
    val report =
        when (override) {
            is ProgramOverride.ExerciseOverride -> {
                library.exercises
                    .takeIf { workoutRunsExercise(workout, override.exerciseId) }
                    ?.firstOrNull { it.id == override.exerciseId }
                    ?.let { OverrideTarget.Exercise(it.name) to mergeExerciseOverride(it, override.config).report }
            }

            is ProgramOverride.BlockOverride -> {
                workout
                    ?.blocks
                    ?.firstOrNull { it is WorkoutBlock.Circuit && it.id == override.blockId }
                    ?.let { OverrideTarget.Circuit(override.blockId) to mergeBlockOverride(it, override.config).report }
            }
        }
    val (target, merged) =
        report
            ?: (OverrideTarget.Missing(override.targetId) to OverrideReport(refused = false, unknownKeys = emptyList()))
    return OverrideNote(
        target,
        // A refusal drops the whole patch, so no key is singled out: that would say the rest applied.
        override.config.map { (key, value) ->
            OverrideKey(key, value, unknown = !merged.refused && key in merged.unknownKeys)
        },
        merged.refused,
    )
}

private val ProgramOverride.targetId: String
    get() =
        when (this) {
            is ProgramOverride.ExerciseOverride -> exerciseId
            is ProgramOverride.BlockOverride -> blockId
        }
