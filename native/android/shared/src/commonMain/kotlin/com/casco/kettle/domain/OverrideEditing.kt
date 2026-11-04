package com.casco.kettle.domain

// The program editor's overrides panel (#243): what a week's override can target, the form an add or
// an edit opens, and the patch its confirm emits. Ported from RN's `program-override-editor.tsx`.
//
// An override's `config` is a partial, snake_case patch of raw values, never a domain object
// (AGENTS.md), while the form holds the same strings any exercise form holds. The merge seeds the form
// and the diff turns it back into only the keys that changed; this file is the bridge between them.

/** What one override patches: an exercise the week's workout runs, or one of its id-tagged circuits. */
sealed interface OverrideEditTarget {
    data class Exercise(
        val exercise: com.casco.kettle.domain.Exercise,
    ) : OverrideEditTarget

    /** [id] is [circuit]'s own, which only a tagged circuit has, and which an override names it by. */
    data class Circuit(
        val circuit: WorkoutBlock.Circuit,
        val id: String,
    ) : OverrideEditTarget
}

/**
 * What a week running [workout] can override: its exercises, in block order and once each, circuit
 * members included, then its circuits that carry an id. An untagged circuit has nothing an override
 * could name it by.
 */
fun overrideTargets(
    library: Library,
    workout: Workout?,
): List<OverrideEditTarget> {
    val blocks = workout?.blocks.orEmpty()
    val exerciseIds =
        blocks
            .flatMap { block ->
                when (block) {
                    is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
                    is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
                }
            }.distinct()
    val exercises = exerciseIds.mapNotNull { id -> library.exercises.firstOrNull { it.id == id } }
    val circuits =
        blocks.filterIsInstance<WorkoutBlock.Circuit>().mapNotNull { c ->
            c.id?.let { OverrideEditTarget.Circuit(c, it) }
        }
    return exercises.map(OverrideEditTarget::Exercise) + circuits
}

/** The override an edit opened, at the [index] it held then. */
data class OpenedOverride(
    val index: Int,
    val override: ProgramOverride,
)

/**
 * The panel's form: [values] as typed, keyed like any exercise form's, with a circuit's three under
 * [BLOCK_CONFIG_FIELDS]' keys. [opened] is the override being edited, null for an add.
 *
 * [seedWeightKg] is the kilograms the weight field was filled with, the effective value on an edit:
 * kept so an untouched pound display isn't converted back into a slightly different number, which
 * would invent an override nobody asked for.
 */
data class OverrideForm(
    val target: OverrideEditTarget,
    val values: Map<ConfigKey, String>,
    val opened: OpenedOverride? = null,
    val seedWeightKg: Double? = null,
) {
    /** The fields the form shows, in order: the exercise type's, or the circuit's own. */
    val fields: List<ConfigField>
        get() =
            when (target) {
                is OverrideEditTarget.Exercise -> configFields(target.exercise.config.type)
                is OverrideEditTarget.Circuit -> BLOCK_CONFIG_FIELDS
            }

    companion object {
        /** A new override on [target], seeded from it as the library has it. */
        fun adding(
            target: OverrideEditTarget,
            system: UnitSystem,
        ): OverrideForm = seeded(target, emptyMap(), null, system)

        /**
         * [override], at [index] in its week, seeded from what was authored: the patch laid over its
         * target unchecked. Seeded from the validated merge, a patch the schema refuses showed the base
         * it fell back to, and a confirm then diffed base against base and erased it; this way it can
         * be read and corrected, the only way out of one for a file that imported before the rule.
         *
         * Null when the target is gone: the exercise from the library, or the circuit from the
         * week's workout.
         */
        fun editing(
            index: Int,
            override: ProgramOverride,
            library: Library,
            workout: Workout?,
            system: UnitSystem,
        ): OverrideForm? {
            val target =
                when (override) {
                    is ProgramOverride.ExerciseOverride -> {
                        library.exercises.firstOrNull { it.id == override.exerciseId }?.let(
                            OverrideEditTarget::Exercise,
                        )
                    }

                    is ProgramOverride.BlockOverride -> {
                        workout
                            ?.blocks
                            ?.filterIsInstance<WorkoutBlock.Circuit>()
                            ?.firstOrNull { it.id == override.blockId }
                            ?.let { OverrideEditTarget.Circuit(it, override.blockId) }
                    }
                }
            return target?.let { seeded(it, override.config, OpenedOverride(index, override), system) }
        }

        private fun seeded(
            target: OverrideEditTarget,
            config: Map<String, OverrideValue>,
            opened: OpenedOverride?,
            system: UnitSystem,
        ): OverrideForm =
            when (target) {
                is OverrideEditTarget.Exercise -> {
                    val authored = mergeExerciseOverride(target.exercise, config).asAuthored
                    val values =
                        configFields(target.exercise.config.type)
                            .mapNotNull { field ->
                                authored[field.key.rawKey]?.let {
                                    field.key to
                                        it.shown(field, system)
                                }
                            }.toMap()
                    val weightKg = (authored[ConfigKey.TargetWeightKg.rawKey] as? OverrideValue.Numeric)?.value
                    OverrideForm(target, values, opened, weightKg)
                }

                // A rest the circuit has none of shows 0, so the field isn't blank; [editedRest] knows.
                is OverrideEditTarget.Circuit -> {
                    val authored = mergeBlockOverride(target.circuit, config).asAuthored
                    val values =
                        BLOCK_CONFIG_FIELDS.associate {
                            it.key to
                                (authored[it.key.rawKey]?.shown(it, system) ?: "0")
                        }
                    OverrideForm(target, values, opened)
                }
            }
    }
}

/**
 * A circuit's rounds stepped [by], held to 1..the schema's ceiling, as RN's stepper. Rounds the form
 * can't read count as 1, as RN's `Number(raw) || 1`.
 */
fun OverrideForm.steppingRounds(by: Int): OverrideForm {
    val rounds = values.number(ConfigKey.Rounds)?.takeIf { it != 0.0 } ?: 1.0
    val stepped = (rounds + by).coerceIn(1.0, MAX_ROUNDS.toDouble())
    return copy(values = values + (ConfigKey.Rounds to jsNumber(stepped)))
}

sealed interface OverrideConfirm {
    /** The week's overrides with the confirm applied. */
    data class Done(
        val overrides: List<ProgramOverride>,
    ) : OverrideConfirm

    data class Refused(
        val problem: ConfigProblem,
    ) : OverrideConfirm
}

/**
 * What [form]'s confirm does to [overrides], the week's list: the edit in place of the override it
 * opened, or an add at the end, through the same validation an exercise form runs.
 *
 * The patch is only what changed, plus whatever the opened override held that the form has nowhere
 * to show (a misspelt key, a circuit's structural one), carried rather than dropped: the editor must
 * not destroy what it can't display, and the row's remove is how one goes deliberately. A patch that
 * ends up empty is no override at all, so an edit down to nothing removes it and an add adds nothing.
 *
 * The edit goes where the opened override still is, and nowhere else. RN replaced by index alone, so
 * a list that changed under the open panel took the edit onto its neighbour, or crashed past the end.
 */
fun confirmOverride(
    form: OverrideForm,
    overrides: List<ProgramOverride>,
    system: UnitSystem,
): OverrideConfirm {
    val patch =
        when (val target = form.target) {
            is OverrideEditTarget.Exercise -> exercisePatch(target.exercise, form, system)
            is OverrideEditTarget.Circuit -> circuitPatch(target, form)
        }
    val override =
        when (patch) {
            is Patch.Refused -> return OverrideConfirm.Refused(patch.problem)
            is Patch.Of -> patch.override.takeIf { it.config.isNotEmpty() }
        }
    val opened = form.opened
    return OverrideConfirm.Done(
        when {
            opened == null -> {
                overrides + listOfNotNull(override)
            }

            overrides.getOrNull(opened.index) != opened.override -> {
                overrides
            }

            else -> {
                overrides.flatMapIndexed { i, existing ->
                    if (i ==
                        opened.index
                    ) {
                        listOfNotNull(override)
                    } else {
                        listOf(existing)
                    }
                }
            }
        },
    )
}

private sealed interface Patch {
    data class Of(
        val override: ProgramOverride,
    ) : Patch

    data class Refused(
        val problem: ConfigProblem,
    ) : Patch
}

private fun exercisePatch(
    exercise: Exercise,
    form: OverrideForm,
    system: UnitSystem,
): Patch {
    val type = exercise.config.type
    validateConfig(type, form.values)?.let { return Patch.Refused(it) }
    val edited =
        buildExercise(exercise.id, ExerciseDraft(exercise.name, type, form.values, ""), system, form.seedWeightKg)
    val kept = form.unshown { mergeExerciseOverride(exercise, it).report }
    return Patch.Of(ProgramOverride.ExerciseOverride(exercise.id, kept + diffExerciseOverride(exercise, edited)))
}

private fun circuitPatch(
    target: OverrideEditTarget.Circuit,
    form: OverrideForm,
): Patch {
    validateBlockConfig(form.values)?.let { return Patch.Refused(it) }
    val circuit = target.circuit
    val edited =
        circuit.copy(
            // RN's `Number(raw) || rounds`: validation has refused anything else but a blank.
            rounds =
                form.values
                    .number(ConfigKey.Rounds)
                    ?.toLong()
                    ?.takeIf { it != 0L } ?: circuit.rounds,
            restBetweenExercisesSec =
                editedRest(
                    form.values,
                    ConfigKey.RestBetweenExercisesSec,
                    circuit.restBetweenExercisesSec,
                ),
            restBetweenRoundsSec =
                editedRest(
                    form.values,
                    ConfigKey.RestBetweenRoundsSec,
                    circuit.restBetweenRoundsSec,
                ),
        )
    val kept = form.unshown { mergeBlockOverride(circuit, it).report }
    return Patch.Of(ProgramOverride.BlockOverride(target.id, kept + diffBlockOverride(circuit, edited)))
}

/** The opened override's keys that [report] finds no setting for, which the form therefore can't show. */
private fun OverrideForm.unshown(report: (Map<String, OverrideValue>) -> OverrideReport): Map<String, OverrideValue> {
    val config = opened?.override?.config ?: return emptyMap()
    val unknown = report(config).unknownKeys
    return config.filterKeys { it in unknown }
}

/**
 * A circuit rest as edited: null where the circuit declares none and the seeded 0 was left alone, so
 * an edit to the rounds alone doesn't invent two rest patches. A cleared field is 0, "no rest here",
 * which on a circuit that declares a rest is a real edit; read as unchanged, it emitted nothing.
 */
private fun editedRest(
    values: Map<ConfigKey, String>,
    key: ConfigKey,
    base: Double?,
): Double? {
    val value = if (values.text(key).isEmpty()) 0.0 else values.number(key) ?: return base
    return value.takeUnless { it == 0.0 && base == null }
}

/** A seeded value as its field shows it: the weight in [system]'s unit, anything unchecked verbatim. */
private fun OverrideValue.shown(
    field: ConfigField,
    system: UnitSystem,
): String =
    when (this) {
        is OverrideValue.Numeric -> {
            jsNumber(
                if (field.unit ==
                    FieldUnit.Weight
                ) {
                    toDisplayWeight(value, system)
                } else {
                    value
                },
            )
        }

        is OverrideValue.Text -> {
            value
        }
    }

/**
 * The key a form value is written under in the file, which an override's patch is keyed by too. The
 * writer keys them per config type rather than by form field, so this is a second spelling of them;
 * `OverrideEditingTest` holds it to the writer's, type by type.
 */
internal val ConfigKey.rawKey: String
    get() =
        when (this) {
            ConfigKey.WorkSec -> "work_sec"
            ConfigKey.RestSec -> "rest_sec"
            ConfigKey.Rounds -> "rounds"
            ConfigKey.IntervalSec -> "interval_sec"
            ConfigKey.TotalMinutes -> "total_minutes"
            ConfigKey.TargetReps -> "target_reps"
            ConfigKey.TimeCapSec -> "time_cap_sec"
            ConfigKey.Sets -> "sets"
            ConfigKey.TargetRepsMin -> "target_reps_min"
            ConfigKey.TargetRepsMax -> "target_reps_max"
            ConfigKey.TargetWeightKg -> "target_weight"
            ConfigKey.HoldSecMin -> "hold_sec_min"
            ConfigKey.HoldSecMax -> "hold_sec_max"
            ConfigKey.DurationSec -> "duration_sec"
            ConfigKey.DistanceMeters -> "distance_meters"
            ConfigKey.RestBetweenExercisesSec -> "rest_between_exercises_sec"
            ConfigKey.RestBetweenRoundsSec -> "rest_between_rounds_sec"
        }
