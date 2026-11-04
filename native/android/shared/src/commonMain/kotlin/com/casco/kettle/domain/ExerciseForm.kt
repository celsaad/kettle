package com.casco.kettle.domain

import kotlin.math.floor

// The exercise form's data model (#241): which fields each exercise type exposes, and the validation
// the form must run itself, since it writes the library directly and never passes through the schema.
// How an exercise round-trips to the strings the form holds is `ExerciseDraft.kt`'s. Ported from RN's
// `exercise-form.ts`, with `validateBlockConfig`, the override editor's (#243), at the end.

/**
 * A config value a form holds, named for the domain property it becomes. The last two are a circuit's
 * own rests, which only the override editor's circuit branch edits, beside [Rounds]: one key space for
 * both kinds of target, as RN's string keys were, so that form holds one map whichever it edits.
 */
enum class ConfigKey {
    WorkSec,
    RestSec,
    Rounds,
    IntervalSec,
    TotalMinutes,
    TargetReps,
    TimeCapSec,
    Sets,
    TargetRepsMin,
    TargetRepsMax,
    TargetWeightKg,
    HoldSecMin,
    HoldSecMax,
    DurationSec,
    DistanceMeters,
    RestBetweenExercisesSec,
    RestBetweenRoundsSec,
}

/** What a field is called on screen: the screen's to put into words, and the name a problem names. */
enum class FieldName {
    Work,
    Rest,
    Rounds,
    Interval,
    Total,
    TargetReps,
    TargetRepsMax,
    TimeCap,
    Sets,
    Weight,
    Hold,
    HoldMax,
    Duration,
    Distance,
    RestPerExercise,
    RestPerRound,
}

/**
 * One field of a type's config. [min] is the smallest accepted value, 1 by default as the schema's
 * `positive()`; [max] the largest, only on the counts the runner expands one step per unit of; and
 * [integer] the schema's `int()`. [unit] is the suffix beside the label, [FieldUnit.Weight] the one
 * that converts.
 */
data class ConfigField(
    val key: ConfigKey,
    val name: FieldName,
    val unit: FieldUnit? = null,
    val optional: Boolean = false,
    val min: Double = 1.0,
    val max: Double? = null,
    val integer: Boolean = false,
)

/** The types the form offers, in the order its pills show them. */
val FORM_TYPES: List<ExerciseType> =
    listOf(
        ExerciseType.Reps,
        ExerciseType.TimedHold,
        ExerciseType.Hiit,
        ExerciseType.Emom,
        ExerciseType.Amrap,
        ExerciseType.Cardio,
        ExerciseType.Rest,
    )

private val Rest = ConfigField(ConfigKey.RestSec, FieldName.Rest, FieldUnit.Seconds, min = 0.0)
private val Sets = ConfigField(ConfigKey.Sets, FieldName.Sets, max = MAX_SETS.toDouble(), integer = true)

/** [type]'s fields, in the order the form shows them. */
fun configFields(type: ExerciseType): List<ConfigField> =
    when (type) {
        ExerciseType.Hiit -> {
            listOf(
                ConfigField(ConfigKey.WorkSec, FieldName.Work, FieldUnit.Seconds),
                Rest,
                ConfigField(ConfigKey.Rounds, FieldName.Rounds, max = MAX_ROUNDS.toDouble(), integer = true),
            )
        }

        ExerciseType.Emom -> {
            listOf(
                ConfigField(ConfigKey.IntervalSec, FieldName.Interval, FieldUnit.Seconds),
                ConfigField(
                    ConfigKey.TotalMinutes,
                    FieldName.Total,
                    FieldUnit.Minutes,
                    max = MAX_TOTAL_MINUTES.toDouble(),
                ),
                ConfigField(ConfigKey.TargetReps, FieldName.TargetReps, optional = true, integer = true),
            )
        }

        ExerciseType.Amrap -> {
            listOf(ConfigField(ConfigKey.TimeCapSec, FieldName.TimeCap, FieldUnit.Seconds))
        }

        ExerciseType.Reps -> {
            listOf(
                Sets,
                ConfigField(ConfigKey.TargetRepsMin, FieldName.TargetReps, integer = true),
                ConfigField(ConfigKey.TargetRepsMax, FieldName.TargetRepsMax, optional = true, integer = true),
                // 0 is legal, as the schema's `nonnegative()`: "bodyweight, no added load" spelled out.
                ConfigField(ConfigKey.TargetWeightKg, FieldName.Weight, FieldUnit.Weight, optional = true, min = 0.0),
                Rest,
            )
        }

        ExerciseType.TimedHold -> {
            listOf(
                Sets,
                // Blank is a max-effort hold that counts up until it's ended, the only way to write one.
                ConfigField(ConfigKey.HoldSecMin, FieldName.Hold, FieldUnit.Seconds, optional = true),
                ConfigField(ConfigKey.HoldSecMax, FieldName.HoldMax, FieldUnit.Seconds, optional = true),
                Rest,
            )
        }

        ExerciseType.Cardio -> {
            listOf(
                ConfigField(ConfigKey.DurationSec, FieldName.Duration, FieldUnit.Seconds, optional = true),
                ConfigField(ConfigKey.DistanceMeters, FieldName.Distance, FieldUnit.Metres, optional = true),
            )
        }

        ExerciseType.Rest -> {
            listOf(ConfigField(ConfigKey.DurationSec, FieldName.Duration, FieldUnit.Seconds, min = 0.0))
        }
    }

/** Why a config can't be saved, for the screen to put into words. The first one found, as RN reports it. */
sealed interface ConfigProblem {
    data class Required(
        val field: FieldName,
    ) : ConfigProblem

    data class MustBeNumber(
        val field: FieldName,
    ) : ConfigProblem

    data class MustBeAtLeast(
        val field: FieldName,
        val min: Double,
    ) : ConfigProblem

    data class MustBeAtMost(
        val field: FieldName,
        val max: Double,
    ) : ConfigProblem

    data class MustBeWhole(
        val field: FieldName,
    ) : ConfigProblem

    data class TooManyIntervals(
        val max: Int,
    ) : ConfigProblem

    data object RepsMaxBelowMin : ConfigProblem

    data object HoldMaxNeedsMin : ConfigProblem

    data object HoldMaxBelowMin : ConfigProblem
}

/**
 * The first problem in [values] as [type]'s config, or null: the schema's config rules, mirrored for
 * a form that writes the library file directly. A config the schema refuses would be written and then
 * fail the next launch's parse, which sends the user's whole library through the reseed.
 *
 * The per-field loop can't see the schema's cross-field rules, so the three there are come after it:
 * EMOM's interval count, and the rep and hold ranges' order.
 */
fun validateConfig(
    type: ExerciseType,
    values: Map<ConfigKey, String>,
): ConfigProblem? {
    configFields(type).firstNotNullOfOrNull { fieldProblem(it, values.text(it.key)) }?.let { return it }
    return when (type) {
        ExerciseType.Emom -> {
            val interval = values.number(ConfigKey.IntervalSec)
            val total = values.number(ConfigKey.TotalMinutes)
            ConfigProblem.TooManyIntervals(MAX_ROUNDS).takeIf {
                interval != null && total != null && interval > 0 && emomIntervalCount(interval, total) > MAX_ROUNDS
            }
        }

        // A bare maximum is legal here, since the minimum is required, so the only rule is the order.
        ExerciseType.Reps -> {
            ConfigProblem.RepsMaxBelowMin.takeIf { values.below(ConfigKey.TargetRepsMax, ConfigKey.TargetRepsMin) }
        }

        ExerciseType.TimedHold -> {
            when {
                values.text(ConfigKey.HoldSecMax).isNotEmpty() && values.text(ConfigKey.HoldSecMin).isEmpty() -> {
                    ConfigProblem.HoldMaxNeedsMin
                }

                values.below(ConfigKey.HoldSecMax, ConfigKey.HoldSecMin) -> {
                    ConfigProblem.HoldMaxBelowMin
                }

                else -> {
                    null
                }
            }
        }

        else -> {
            null
        }
    }
}

private fun fieldProblem(
    field: ConfigField,
    raw: String,
): ConfigProblem? {
    val value = jsNumberOrNull(raw)
    val max = field.max
    return when {
        raw.isEmpty() -> ConfigProblem.Required(field.name).takeUnless { field.optional }
        value == null -> ConfigProblem.MustBeNumber(field.name)
        value < field.min -> ConfigProblem.MustBeAtLeast(field.name, field.min)
        max != null && value > max -> ConfigProblem.MustBeAtMost(field.name, max)
        field.integer && value != floor(value) -> ConfigProblem.MustBeWhole(field.name)
        else -> null
    }
}

internal fun Map<ConfigKey, String>.text(key: ConfigKey) = this[key]?.trim().orEmpty()

/** [key]'s number, or null for blank or not a number, where an optional field means "not set". */
internal fun Map<ConfigKey, String>.number(key: ConfigKey) = jsNumberOrNull(text(key))

/** Whether both ends of a range are filled in and [high] is below [low]. */
private fun Map<ConfigKey, String>.below(
    high: ConfigKey,
    low: ConfigKey,
): Boolean {
    val top = number(high)
    val bottom = number(low)
    return top != null && bottom != null && top < bottom
}

/**
 * A circuit's own params, as the override editor's circuit branch edits them: its fields for
 * [validateBlockConfig]. Both rests are optional, as the schema has them, and since the panel seeds
 * them from the circuit, clearing one is how a user says "no rest here"; required, that was a blocked
 * save where it had always read as 0.
 */
val BLOCK_CONFIG_FIELDS: List<ConfigField> =
    listOf(
        ConfigField(ConfigKey.Rounds, FieldName.Rounds, max = MAX_ROUNDS.toDouble(), integer = true),
        ConfigField(ConfigKey.RestBetweenExercisesSec, FieldName.RestPerExercise, optional = true, min = 0.0),
        ConfigField(ConfigKey.RestBetweenRoundsSec, FieldName.RestPerRound, optional = true, min = 0.0),
    )

/**
 * The first problem in [values] as a circuit's params, or null. RN's circuit branch validated nothing
 * until this existed, so a negative rest was written to the program file and then dropped, silently,
 * by the merge's re-parse.
 */
fun validateBlockConfig(values: Map<ConfigKey, String>): ConfigProblem? =
    BLOCK_CONFIG_FIELDS.firstNotNullOfOrNull { fieldProblem(it, values.text(it.key)) }
