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

/**
 * [field]'s count as its stepper writes it: the whole number typed, kept as typed past a Long's range
 * (#569), where the count itself is clamped; else the count.
 */
fun ExerciseDraft.shownText(field: ConfigField): String =
    values[field.key]?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.toLongOrNull() == null }
        ?: "${shownCount(field)}"

/** Whether [field]'s stepper can move [by]: within its bounds, and a reps max never below the min. */
fun ExerciseDraft.canStep(
    field: ConfigField,
    by: Int,
): Boolean {
    val next = steppedCount(field, by)
    return next >= lowest(field) && next <= (field.max?.toLong() ?: Long.MAX_VALUE)
}

/**
 * [field]'s count after a step of [by]. A count typed past its ceiling (#569) comes down to the ceiling
 * in one step of −, which a step at a time from 501 couldn't, its next value still past the max.
 */
private fun ExerciseDraft.steppedCount(
    field: ConfigField,
    by: Int,
): Long {
    val shown = shownCount(field)
    val top = field.max?.toLong() ?: Long.MAX_VALUE
    return if (by < 0 && shown > top) top else shown + by
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
    val next = steppedCount(field, by)
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
 * [field]'s count as typed into it (#463): a whole number, of any
 * size, so Save's `validateConfig` refuses one past its ceiling in its own words; anything else, a blank
 * included, leaves the count as it was, since a "–" or "8.5" closed into a valid-looking count would
 * read as accepted. A reps min typed past the max carries the max up with it, and a max typed below the
 * min is held at it, as the steppers keep the two, rather than leave a range the form refuses.
 */
fun ExerciseDraft.typing(
    field: ConfigField,
    text: String,
): ExerciseDraft {
    val digits = text.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    // Past a Long, it's still a whole number, and Save's ceiling says so; dropped, it read as accepted.
    val count = digits?.toLongOrNull()
    val held = count?.let { if (field.key == ConfigKey.TargetRepsMax) maxOf(it, lowest(field)) else it }
    val carried = held?.takeIf { field.key == ConfigKey.TargetRepsMin && (countOf(ConfigKey.TargetRepsMax) ?: it) < it }
    val written = held?.toString() ?: digits
    val typed = if (written == null) values else values + (field.key to written)
    return copy(values = if (carried == null) typed else typed + (ConfigKey.TargetRepsMax to "$carried"))
}

/**
 * A new exercise's starting target, per type (#456): 3 × 8–12 with a 1:30 rest, a 0:30 hold, a HIIT
 * of 0:30 on and off for 8 rounds. A name-only Save writes something a person would train, not the
 * least each field takes. No weight: there's no load that suits everyone.
 */
private val DEFAULTS: Map<ExerciseType, Map<ConfigKey, String>> =
    mapOf(
        ExerciseType.Reps to
            mapOf(
                ConfigKey.Sets to "3",
                ConfigKey.TargetRepsMin to "8",
                ConfigKey.TargetRepsMax to "12",
                ConfigKey.RestSec to "90",
            ),
        ExerciseType.TimedHold to mapOf(ConfigKey.Sets to "3", ConfigKey.HoldSecMin to "30", ConfigKey.RestSec to "60"),
        ExerciseType.Hiit to mapOf(ConfigKey.WorkSec to "30", ConfigKey.RestSec to "30", ConfigKey.Rounds to "8"),
        ExerciseType.Emom to mapOf(ConfigKey.IntervalSec to "60", ConfigKey.TotalMinutes to "10"),
        ExerciseType.Amrap to mapOf(ConfigKey.TimeCapSec to "600"),
        ExerciseType.Rest to mapOf(ConfigKey.DurationSec to "60"),
    )

/**
 * The draft with its blanks at the type's starting target, and any other blank required count or time
 * at its field's least: what its steppers and time buttons show, so a new exercise saves what it shows
 * rather than being refused for a field that read "3" on screen. An optional field with no default
 * stays blank, which means something: no weight, a cardio that counts up.
 */
fun ExerciseDraft.withDefaults(): ExerciseDraft {
    val defaults = DEFAULTS[type].orEmpty()
    val blanks =
        configFields(type)
            .filter { values[it.key].isNullOrBlank() }
            .mapNotNull { field ->
                val least =
                    when {
                        field.optional -> null
                        field.integer -> "${field.min.toLong()}"
                        field.unit == FieldUnit.Seconds -> jsNumber(field.min)
                        else -> null
                    }
                (defaults[field.key] ?: least)?.let { field.key to it }
            }
    return if (blanks.isEmpty()) this else copy(values = values + blanks)
}

/**
 * A new exercise switched to [type]: what it shares with the old one carries over, Sets from reps to a
 * hold say, but not a value the new type refuses, such as Rest's filled 0:00 landing in Cardio's
 * duration, whose least is a second, or typed text that is not a number. Then its own blanks take
 * the type's defaults, as a new exercise's do.
 */
fun ExerciseDraft.switchingTo(type: ExerciseType): ExerciseDraft {
    val fields = configFields(type).associateBy { it.key }
    val untouched = DEFAULTS[this.type].orEmpty()
    val kept =
        values.filter { (key, text) ->
            val field = fields[key]
            val number = jsNumberOrNull(text.trim())
            // The old type's own default, never changed, isn't the user's: the new type's goes in its
            // place, so a new reps exercise switched to HIIT rests 0:30, not the reps 1:30.
            val stillDefault = untouched[key] == text
            // Not a number goes too: a time button shows it as 0:00, which Save then refuses unseen.
            !stillDefault && (field == null || (number != null && number >= field.min))
        }
    return copy(type = type, values = kept).withDefaults()
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
