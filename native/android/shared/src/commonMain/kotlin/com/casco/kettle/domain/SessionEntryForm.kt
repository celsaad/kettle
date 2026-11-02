package com.casco.kettle.domain

import kotlin.math.floor

// `src/domain/session-entry-form.ts`, which holds the reasoning: the session editor's data model, which
// fields each logged entry type exposes, how an entry round-trips to the strings a form holds, and the
// validation that path must run itself, since the editor never passes through the schema.

/** What a field's number is measured in, for the suffix beside its label. */
enum class FieldUnit {
    /** Kilograms or pounds as the reader's setting says, and the one value that converts both ways. */
    Weight,
    Seconds,
    Metres,
}

/**
 * One number the editor lets the user correct. [min] is 0, not the exercise form's 1: a zero isn't
 * refused, it means "not done", and [buildEntry] drops it on save, as the runner never writes one.
 * [integer] marks a count rather than a measurement: 8.5 reps is not something the log should hold.
 */
enum class EntryField(
    val optional: Boolean = false,
    val min: Double = 0.0,
    val max: Double? = null,
    val integer: Boolean = false,
    val unit: FieldUnit? = null,
) {
    Reps(integer = true),
    Load(optional = true, unit = FieldUnit.Weight),

    // Blank means it wasn't recorded, which is different from 1. The scale itself starts at 1.
    Rpe(optional = true, min = 1.0, max = 10.0),
    Hold(unit = FieldUnit.Seconds),
    Rounds(integer = true),
    ExtraReps(optional = true, integer = true),
    Duration(optional = true, unit = FieldUnit.Seconds),
    Distance(optional = true, unit = FieldUnit.Metres),
}

/**
 * The fields [entry] is edited through, in the order they show, or null for an entry the editor can't
 * rewrite. EMOM is out because its log is a per-minute rep list, a list editor rather than a row of
 * fields; rest because it has no number a user would recognise as theirs. Anything else new lands
 * here read-only by default, rather than silently dropped.
 */
fun entryFields(entry: SessionEntry): List<EntryField>? =
    when (entry) {
        is SessionEntry.Reps -> listOf(EntryField.Reps, EntryField.Load, EntryField.Rpe)
        is SessionEntry.TimedHold -> listOf(EntryField.Hold)
        is SessionEntry.Hiit -> listOf(EntryField.Rounds)
        is SessionEntry.Amrap -> listOf(EntryField.Rounds, EntryField.ExtraReps)
        is SessionEntry.Cardio -> listOf(EntryField.Duration, EntryField.Distance)
        is SessionEntry.Emom, is SessionEntry.Rest -> null
    }

/**
 * One row of the form: what the user can type, plus the parts of the logged set that have no field and
 * still have to survive the round trip. Carried here rather than read back off the original entry,
 * because removing a set shifts every later row, and reading `original.sets[index]` at save time would
 * staple set 3's rest onto set 2.
 *
 * [weightKg] is the stored kilograms behind what the load field shows: the drift guard, kept per row so
 * no caller can forget it. Without it, opening a 100 kg set in pounds and editing something else
 * rewrites it as 100.02.
 */
data class SetForm(
    val values: Map<EntryField, String>,
    val restTakenSec: Double,
    val weightKg: Double? = null,
)

/**
 * A logged entry as form rows, the load converted to [system]; nothing else is. The one-result types
 * get a single row, so the screen, the validation and removal all handle one shape.
 */
fun entryToSetForms(
    entry: SessionEntry,
    system: UnitSystem,
): List<SetForm> =
    when (entry) {
        is SessionEntry.Reps -> {
            entry.sets.map { set ->
                SetForm(
                    mapOf(
                        EntryField.Reps to set.reps.toString(),
                        EntryField.Load to set.weightKg?.let { jsNumber(toDisplayWeight(it, system)) }.orEmpty(),
                        EntryField.Rpe to set.rpe?.let(::jsNumber).orEmpty(),
                    ),
                    set.restTakenSec,
                    set.weightKg,
                )
            }
        }

        is SessionEntry.TimedHold -> {
            entry.sets.map { SetForm(mapOf(EntryField.Hold to jsNumber(it.holdSec)), it.restTakenSec) }
        }

        is SessionEntry.Hiit -> {
            listOf(SetForm(mapOf(EntryField.Rounds to entry.roundsCompleted.toString()), 0.0))
        }

        is SessionEntry.Amrap -> {
            val values =
                mapOf(
                    EntryField.Rounds to entry.roundsCompleted.toString(),
                    EntryField.ExtraReps to entry.extraReps?.toString().orEmpty(),
                )
            listOf(SetForm(values, 0.0))
        }

        is SessionEntry.Cardio -> {
            val values =
                mapOf(
                    EntryField.Duration to entry.durationSec?.let(::jsNumber).orEmpty(),
                    EntryField.Distance to entry.distanceMeters?.let(::jsNumber).orEmpty(),
                )
            listOf(SetForm(values, 0.0))
        }

        is SessionEntry.Emom, is SessionEntry.Rest -> {
            emptyList()
        }
    }

/** Why a form can't be saved, for the screen to put into words. The first one found, as RN reports it. */
sealed interface EntryProblem {
    data class Required(
        val field: EntryField,
    ) : EntryProblem

    data class MustBeNumber(
        val field: EntryField,
    ) : EntryProblem

    data class MustBeAtLeast(
        val field: EntryField,
        val min: Double,
    ) : EntryProblem

    data class MustBeAtMost(
        val field: EntryField,
        val max: Double,
    ) : EntryProblem

    data class MustBeWhole(
        val field: EntryField,
    ) : EntryProblem

    data object CardioNeedsOne : EntryProblem
}

/**
 * The first problem in any row of [forms], [entry]'s, or null. The only thing between a typo and a log
 * claiming an RPE of 88, since the editor writes the file directly and never runs the schema.
 */
fun validateEntryForm(
    entry: SessionEntry,
    forms: List<SetForm>,
): EntryProblem? {
    val fields = entryFields(entry).orEmpty()
    return forms.firstNotNullOfOrNull { form ->
        fields.firstNotNullOfOrNull { fieldProblem(it, form.values[it]?.trim().orEmpty()) }
            // Each cardio field is optional alone, as the format allows, but an entry with neither
            // records nothing, and the per-field check can't see that.
            ?: EntryProblem.CardioNeedsOne.takeIf {
                entry is SessionEntry.Cardio && form.isBlank(EntryField.Duration) && form.isBlank(EntryField.Distance)
            }
    }
}

private fun fieldProblem(
    field: EntryField,
    raw: String,
): EntryProblem? {
    val value = jsNumberOrNull(raw)
    val max = field.max
    return when {
        raw.isEmpty() -> EntryProblem.Required(field).takeUnless { field.optional }
        value == null -> EntryProblem.MustBeNumber(field)
        value < field.min -> EntryProblem.MustBeAtLeast(field, field.min)
        max != null && value > max -> EntryProblem.MustBeAtMost(field, max)
        field.integer && value != floor(value) -> EntryProblem.MustBeWhole(field)
        else -> null
    }
}

/**
 * [forms] back into a logged entry, keeping [original]'s exercise and type. Null when nothing is left,
 * no rows or only rows at zero: the caller's signal to drop the entry rather than write an exercise
 * recorded as having done nothing. Zero rows inside a longer entry go the same way.
 */
fun buildEntry(
    original: SessionEntry,
    forms: List<SetForm>,
    system: UnitSystem,
): SessionEntry? = if (forms.isEmpty()) null else assembleEntry(original, forms, system)?.let(::entryWithoutZeroWork)

/** Whether [form] alone, saved, would be dropped as zero work: what the screen's hint keys on. */
fun isZeroForm(
    original: SessionEntry,
    form: SetForm,
    system: UnitSystem,
): Boolean = buildEntry(original, listOf(form), system) == null

private fun assembleEntry(
    original: SessionEntry,
    forms: List<SetForm>,
    system: UnitSystem,
): SessionEntry? {
    val exercise = original.exercise

    // `?: 0.0` on a required field, which validation has already checked, is RN's `Number(raw) || 0`.
    return when (original) {
        is SessionEntry.Reps -> {
            val sets =
                forms.map {
                    RepsSetLog(
                        reps = (it.optionalNumber(EntryField.Reps) ?: 0.0).toLong(),
                        weightKg = it.loadKg(system),
                        rpe = it.optionalNumber(EntryField.Rpe),
                        restTakenSec = it.restTakenSec,
                    )
                }
            SessionEntry.Reps(exercise, sets)
        }

        is SessionEntry.TimedHold -> {
            SessionEntry.TimedHold(
                exercise,
                forms.map {
                    TimedHoldSetLog(it.optionalNumber(EntryField.Hold) ?: 0.0, it.restTakenSec)
                },
            )
        }

        is SessionEntry.Hiit -> {
            SessionEntry.Hiit(exercise, (forms[0].optionalNumber(EntryField.Rounds) ?: 0.0).toLong())
        }

        is SessionEntry.Amrap -> {
            val form = forms[0]
            SessionEntry.Amrap(
                exercise,
                (form.optionalNumber(EntryField.Rounds) ?: 0.0).toLong(),
                form.optionalNumber(EntryField.ExtraReps)?.toLong(),
            )
        }

        is SessionEntry.Cardio -> {
            val form = forms[0]
            SessionEntry.Cardio(
                exercise,
                form.optionalNumber(EntryField.Duration),
                form.optionalNumber(EntryField.Distance),
            )
        }

        is SessionEntry.Emom, is SessionEntry.Rest -> {
            null
        }
    }
}

/** [field]'s number, or null for blank, where an optional field means "not recorded". */
private fun SetForm.optionalNumber(field: EntryField): Double? = values[field]?.trim()?.let(::jsNumberOrNull)

private fun SetForm.isBlank(field: EntryField) = values[field]?.trim().isNullOrEmpty()

/** The load, back in kilograms: the stored value itself while the field still shows it, against drift. */
private fun SetForm.loadKg(system: UnitSystem): Double? {
    val entered = optionalNumber(EntryField.Load) ?: return null
    val stored = weightKg
    return if (stored != null &&
        entered == toDisplayWeight(stored, system)
    ) {
        stored
    } else {
        fromDisplayWeight(entered, system)
    }
}
