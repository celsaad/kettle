package com.casco.kettle.domain

// A count as it's typed and shown (#463, #569): digits however long, which the count itself, a Long read
// through a double, can't hold exactly.

/** ASCII digits and nothing else: what a typed count is (#463), however long. */
internal fun String.isCount() = isNotEmpty() && all { it in '0'..'9' }

/** [key]'s typed digits, leading zeros gone, or null when it holds no whole number of digits. */
private fun ExerciseDraft.digitsOf(key: ConfigKey): String? =
    values[key]
        ?.trim()
        ?.takeIf { it.isCount() }
        ?.trimStart('0')
        ?.ifEmpty { "0" }

/**
 * [field]'s count as every place shows it (#569): the digits typed, exactly, however long, where the
 * count itself rounds past 2^53 and clamps past a Long; a blank reps max mirrors the min's; else the
 * count.
 */
fun ExerciseDraft.shownText(field: ConfigField): String =
    digitsOf(field.key)
        ?: (if (field.key == ConfigKey.TargetRepsMax) digitsOf(ConfigKey.TargetRepsMin) else null)
        ?: "${shownCount(field)}"

/** Whether [field] holds digits too many for a Long: no step reads it right, so neither steps it. */
internal fun ExerciseDraft.overflows(field: ConfigField): Boolean = shownText(field).toLongOrNull() == null
