package com.casco.kettle.domain

// The exercise editor's steppers (#441): a count stepped within its field's bounds, and the reps pair
// kept in order, "8 to 12", so the form can't hold a range Save would refuse.

/** [key]'s whole-number value, or null for a blank or a value that isn't one. */
fun ExerciseDraft.countOf(key: ConfigKey): Long? = values[key]?.trim()?.let(::jsNumberOrNull)?.toLong()

/**
 * The count [field] holds, as its stepper shows it: the value, or for a blank reps max the min, since
 * a range with no top reads as the fixed count it is; any other blank shows the field's least.
 */
fun ExerciseDraft.shownCount(field: ConfigField): Long =
    countOf(field.key)
        ?: (if (field.key == ConfigKey.TargetRepsMax) countOf(ConfigKey.TargetRepsMin) else null)
        ?: field.min.toLong()

/** Whether [field]'s stepper can move [by]: within its bounds, and a reps max never below the min. */
fun ExerciseDraft.canStep(
    field: ConfigField,
    by: Int,
): Boolean {
    val next = shownCount(field) + by
    return next >= lowest(field) && next <= (field.max?.toLong() ?: Long.MAX_VALUE)
}

/**
 * [field]'s count moved [by], held to its bounds. Raising the reps min past the max carries the max up
 * with it, so "8 to 8" stepped up is "9 to 9" rather than a range the form refuses.
 */
fun ExerciseDraft.stepping(
    field: ConfigField,
    by: Int,
): ExerciseDraft {
    if (!canStep(field, by)) return this
    val next = shownCount(field) + by
    var stepped = values + (field.key to "$next")
    val max = countOf(ConfigKey.TargetRepsMax)
    if (field.key == ConfigKey.TargetRepsMin && max != null &&
        max < next
    ) {
        stepped = stepped + (ConfigKey.TargetRepsMax to "$next")
    }
    return copy(values = stepped)
}

/**
 * The draft with every blank required count at its field's least and every blank required time at 0:
 * what its steppers and time buttons show, so a new exercise saves what it shows rather than being
 * refused for a field that read "1" on screen. Optional fields stay blank, which means something.
 */
fun ExerciseDraft.withRequiredFilled(): ExerciseDraft {
    val blanks =
        configFields(type)
            .filter { !it.optional && values[it.key].isNullOrBlank() }
            .mapNotNull { field ->
                when {
                    field.integer -> field.key to "${field.min.toLong()}"
                    field.unit == FieldUnit.Seconds -> field.key to "0"
                    else -> null
                }
            }
    return if (blanks.isEmpty()) this else copy(values = values + blanks)
}

/** A reps max goes no lower than the min; any other count no lower than its field allows. */
private fun ExerciseDraft.lowest(field: ConfigField): Long =
    if (field.key ==
        ConfigKey.TargetRepsMax
    ) {
        countOf(ConfigKey.TargetRepsMin) ?: field.min.toLong()
    } else {
        field.min.toLong()
    }
