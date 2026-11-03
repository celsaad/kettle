package com.casco.kettle.domain

// The exercise form's other half (#241): an exercise as the strings a form holds, back again, and
// what Save makes of them. `ExerciseForm.kt` holds the fields and their validation.

/** What the form holds: every field as the text typed, keyed so a type switch keeps what the two share. */
data class ExerciseDraft(
    val name: String,
    val type: ExerciseType,
    val values: Map<ConfigKey, String>,
    val notes: String,
) {
    companion object {
        /** A new exercise's empty form, on the first type the form offers. */
        val New = ExerciseDraft("", FORM_TYPES.first(), emptyMap(), "")
    }
}

/** [exercise] as the form shows it: the weight in [system]'s unit, every other number as stored. */
fun draftOf(
    exercise: Exercise,
    system: UnitSystem,
): ExerciseDraft {
    fun weight(kg: Double?) = kg?.let { jsNumber(toDisplayWeight(it, system)) }
    val values: Map<ConfigKey, String?> =
        when (val config = exercise.config) {
            is ExerciseConfig.Hiit -> {
                mapOf(
                    ConfigKey.WorkSec to jsNumber(config.workSec),
                    ConfigKey.RestSec to jsNumber(config.restSec),
                    ConfigKey.Rounds to config.rounds.toString(),
                )
            }

            is ExerciseConfig.Emom -> {
                mapOf(
                    ConfigKey.IntervalSec to jsNumber(config.intervalSec),
                    ConfigKey.TotalMinutes to jsNumber(config.totalMinutes),
                    ConfigKey.TargetReps to config.targetReps?.toString(),
                )
            }

            is ExerciseConfig.Amrap -> {
                mapOf(ConfigKey.TimeCapSec to jsNumber(config.timeCapSec))
            }

            is ExerciseConfig.Reps -> {
                mapOf(
                    ConfigKey.Sets to config.sets.toString(),
                    ConfigKey.TargetRepsMin to config.targetRepsMin.toString(),
                    ConfigKey.TargetRepsMax to config.targetRepsMax?.toString(),
                    ConfigKey.TargetWeightKg to weight(config.targetWeightKg),
                    ConfigKey.RestSec to jsNumber(config.restSec),
                )
            }

            is ExerciseConfig.TimedHold -> {
                mapOf(
                    ConfigKey.Sets to config.sets.toString(),
                    ConfigKey.HoldSecMin to config.holdSecMin?.let(::jsNumber),
                    ConfigKey.HoldSecMax to config.holdSecMax?.let(::jsNumber),
                    ConfigKey.RestSec to jsNumber(config.restSec),
                )
            }

            is ExerciseConfig.Cardio -> {
                mapOf(
                    ConfigKey.DurationSec to config.durationSec?.let(::jsNumber),
                    ConfigKey.DistanceMeters to config.distanceMeters?.let(::jsNumber),
                )
            }

            is ExerciseConfig.Rest -> {
                mapOf(ConfigKey.DurationSec to jsNumber(config.durationSec))
            }
        }
    return ExerciseDraft(
        exercise.name,
        exercise.config.type,
        values.filterValues { it != null }.mapValues { it.value.orEmpty() },
        exercise.notes.orEmpty(),
    )
}

/**
 * [draft] as exercise [id], its numbers as numbers: the schema would refuse `sets: "4"` on the way back
 * in. Assumes [validateConfig] passed, as RN's did: a required field that isn't a number reads as 0.
 *
 * [previousWeightKg] is the kilograms the weight field was filled from, when editing. While the field
 * still shows exactly that, it's kept rather than converted back: 100 kg shows as 220.5 lb, which
 * converts to 100.02, so an unrelated edit in pounds would otherwise move the weight on every save.
 */
fun buildExercise(
    id: String,
    draft: ExerciseDraft,
    system: UnitSystem,
    previousWeightKg: Double? = null,
): Exercise {
    val config = configOf(draft.type, draft.values, draft.values.weightKg(system, previousWeightKg))
    return Exercise(id, draft.name.trim(), config, draft.notes.trim().ifEmpty { null })
}

/** The weight field back in kilograms: the stored value itself while the field still shows it. */
private fun Map<ConfigKey, String>.weightKg(
    system: UnitSystem,
    previousWeightKg: Double?,
): Double? {
    val entered = number(ConfigKey.TargetWeightKg)
    return when {
        entered == null -> null
        previousWeightKg != null && entered == toDisplayWeight(previousWeightKg, system) -> previousWeightKg
        else -> fromDisplayWeight(entered, system)
    }
}

/** RN's `Number(raw) || 0`: validation has already refused a required field that isn't one. */
private fun Map<ConfigKey, String>.required(key: ConfigKey) = number(key) ?: 0.0

private fun Map<ConfigKey, String>.count(key: ConfigKey) = required(key).toLong()

/** [values] as [type]'s config, with [weightKg] already converted, since only reps has one. */
private fun configOf(
    type: ExerciseType,
    values: Map<ConfigKey, String>,
    weightKg: Double?,
): ExerciseConfig =
    when (type) {
        ExerciseType.Hiit -> {
            ExerciseConfig.Hiit(
                values.required(ConfigKey.WorkSec),
                values.required(ConfigKey.RestSec),
                values.count(ConfigKey.Rounds),
            )
        }

        ExerciseType.Emom -> {
            ExerciseConfig.Emom(
                values.required(ConfigKey.IntervalSec),
                values.required(ConfigKey.TotalMinutes),
                values.number(ConfigKey.TargetReps)?.toLong(),
            )
        }

        ExerciseType.Amrap -> {
            ExerciseConfig.Amrap(values.required(ConfigKey.TimeCapSec))
        }

        ExerciseType.Reps -> {
            ExerciseConfig.Reps(
                sets = values.count(ConfigKey.Sets),
                targetRepsMin = values.count(ConfigKey.TargetRepsMin),
                targetRepsMax = values.number(ConfigKey.TargetRepsMax)?.toLong(),
                targetWeightKg = weightKg,
                restSec = values.required(ConfigKey.RestSec),
            )
        }

        ExerciseType.TimedHold -> {
            ExerciseConfig.TimedHold(
                sets = values.count(ConfigKey.Sets),
                holdSecMin = values.number(ConfigKey.HoldSecMin),
                holdSecMax = values.number(ConfigKey.HoldSecMax),
                restSec = values.required(ConfigKey.RestSec),
            )
        }

        ExerciseType.Cardio -> {
            ExerciseConfig.Cardio(values.number(ConfigKey.DurationSec), values.number(ConfigKey.DistanceMeters))
        }

        ExerciseType.Rest -> {
            ExerciseConfig.Rest(values.required(ConfigKey.DurationSec))
        }
    }

/** Why the form can't be saved: the name's two problems, or the config's. */
sealed interface ExerciseProblem {
    data object NameRequired : ExerciseProblem

    /** A name with no letter or digit in it, which leaves nothing to make an id from. */
    data object NoId : ExerciseProblem

    data class Config(
        val problem: ConfigProblem,
    ) : ExerciseProblem
}

sealed interface ExerciseSubmit {
    data class Built(
        val exercise: Exercise,
    ) : ExerciseSubmit

    data class Refused(
        val problem: ExerciseProblem,
    ) : ExerciseSubmit
}

/**
 * What Save makes of [draft]: the editor's and the quick-add's one path, so the checks can't drift
 * apart between them, as RN's two copies did.
 *
 * [existing] is the exercise being edited, null for a new one. It keeps its id, so a rename can't
 * orphan the workouts that use it; its type, since each type keys a different config; and its stored
 * weight against drift. A new exercise's id is its name's [slugify].
 */
fun submitExercise(
    draft: ExerciseDraft,
    existing: Exercise?,
    system: UnitSystem,
): ExerciseSubmit {
    val id = existing?.id ?: slugify(draft.name)
    val locked = existing?.let { draft.copy(type = it.config.type) } ?: draft
    // In this order, as RN checked: the name first, so a blank form says what's missing first.
    val problem =
        when {
            draft.name.isBlank() -> ExerciseProblem.NameRequired
            id.isEmpty() -> ExerciseProblem.NoId
            else -> validateConfig(locked.type, locked.values)?.let(ExerciseProblem::Config)
        }
    val previousWeightKg = (existing?.config as? ExerciseConfig.Reps)?.targetWeightKg
    return problem?.let(ExerciseSubmit::Refused)
        ?: ExerciseSubmit.Built(buildExercise(id, locked, system, previousWeightKg))
}
