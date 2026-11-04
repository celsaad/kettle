package com.casco.kettle.domain

// What an import is about to overwrite, field by field (#244), as RN's `library-diff.ts`. Merge
// replaces a matching id whole (§6), so an id the user tweaked loses the tweak, and this is what the
// preview shows of it. Descriptors rather than words: the import screen renders them.

/** Which part of an item moved. */
sealed interface DiffLabel {
    data object Name : DiffLabel

    data object Type : DiffLabel

    data object Notes : DiffLabel

    data object Blocks : DiffLabel

    /** Exercise references a workout gained or lost, as ids: one dropped by the same import has no name left. */
    data object Exercises : DiffLabel

    data object Weeks : DiffLabel

    /** Weeks on both sides that differ in any way, by number and day. */
    data object WeeksEdited : DiffLabel

    /** A config field, named as the exercise form names it. */
    data class Field(
        val name: FieldName,
    ) : DiffLabel
}

/** One side of a change, as the screen shows it. */
sealed interface DiffValue {
    /** Absent or blank, so "60 → not set" reads as a removal rather than as nothing. */
    data object Unset : DiffValue

    /** User data or a number, shown verbatim, already cut to [MAX_VALUE_LENGTH]. */
    data class Text(
        val text: String,
    ) : DiffValue

    /** An exercise type, which the screen names as the form's type pills do. */
    data class Type(
        val type: ExerciseType,
    ) : DiffValue

    /** A number with the unit the form shows beside it: the weight already in the reader's units. */
    data class Measured(
        val number: String,
        val unit: FieldUnit,
    ) : DiffValue
}

/** [label] moved from [from] to [to]. */
data class FieldChange(
    val label: DiffLabel,
    val from: DiffValue,
    val to: DiffValue,
)

/**
 * What moved between [before] and [after], the same exercise by id, with weights in [units]. A type
 * change is reported instead of the config, not beside it: the two configs share no keys once the type
 * moves, so listing them would call every field of both changed and bury the one line that explains it.
 */
fun diffExercise(
    before: Exercise,
    after: Exercise,
    units: UnitSystem,
): List<FieldChange> {
    val retyped = before.config.type != after.config.type
    val changes =
        listOfNotNull(
            change(DiffLabel.Name, before.name, after.name),
            FieldChange(DiffLabel.Type, DiffValue.Type(before.config.type), DiffValue.Type(after.config.type))
                .takeIf { retyped },
            change(DiffLabel.Notes, before.notes, after.notes),
        )
    if (retyped) return changes

    val beforeValues = draftOf(before, units).values
    val afterValues = draftOf(after, units).values
    // In the form's order, so a diff lists fields as the editor the reader opens next shows them.
    return changes +
        configFields(after.config.type).mapNotNull { field ->
            change(DiffLabel.Field(field.name), beforeValues[field.key], afterValues[field.key]) { number ->
                field.unit?.let { DiffValue.Measured(number, it) } ?: DiffValue.Text(number)
            }
        }
}

/**
 * Coarser than the exercise diff on purpose: a faithful per-block diff of reordered circuits is a
 * diffing algorithm rather than a preview line, and the question is only whether this is the same
 * workout and still holds what the reader put in it. Block count, plus which references appeared or
 * vanished, circuit members included.
 */
fun diffWorkout(
    before: Workout,
    after: Workout,
): List<FieldChange> {
    val had = before.blocks.flatMap(::exercisesIn)
    val has = after.blocks.flatMap(::exercisesIn)
    val gone = had.filter { it !in has }
    val added = has.filter { it !in had }
    return listOfNotNull(
        change(DiffLabel.Name, before.name, after.name),
        change(DiffLabel.Blocks, "${before.blocks.size}", "${after.blocks.size}"),
        FieldChange(DiffLabel.Exercises, DiffValue.Text(gone.joinToString(", ")), NONE).takeIf { gone.isNotEmpty() },
        FieldChange(DiffLabel.Exercises, NONE, DiffValue.Text(added.joinToString(", "))).takeIf { added.isNotEmpty() },
    )
}

/**
 * The workout diff's reasoning one level up: week count, plus which weeks differ in any way. Which
 * field of week 4 moved is a question the program editor answers better than a preview line can.
 */
fun diffProgram(
    before: Program,
    after: Program,
): List<FieldChange> {
    val previous = before.weeks.associateBy(::slot)
    // Only weeks on both sides: one that's new or gone is already in the count, and counting it here
    // too would report the same edit twice.
    val edited = after.weeks.filter { week -> previous[slot(week)]?.let { it != week } == true }
    return listOfNotNull(
        change(DiffLabel.Name, before.name, after.name),
        change(DiffLabel.Weeks, "${before.weeks.size}", "${after.weeks.size}"),
        FieldChange(
            DiffLabel.WeeksEdited,
            NONE,
            DiffValue.Text(
                edited.joinToString(", ") { week ->
                    week.day?.ifEmpty { null }?.let { "${week.week} ($it)" }
                        ?: "${week.week}"
                },
            ),
        ).takeIf { edited.isNotEmpty() },
    )
}

/** A week's place in the rotation: its number and day, which no two weeks share. */
private fun slot(week: ProgramWeek) = week.week to week.day.orEmpty()

/** One entry per reference, not per id, as RN's `referencedExercises`. */
private fun exercisesIn(block: WorkoutBlock): List<String> =
    when (block) {
        is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
        is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
    }

/** RN's dash for the empty side of a list that grew or shrank: a glyph, the same in every language. */
private val NONE = DiffValue.Text("—")

/**
 * Compares the **raw** values and cuts only what's shown: comparing the cut ones would make two notes
 * that first differ past the cut look identical, and the change would vanish from the one screen that
 * exists to show it.
 */
private fun change(
    label: DiffLabel,
    from: String?,
    to: String?,
    value: (String) -> DiffValue = { DiffValue.Text(cut(it)) },
): FieldChange? =
    if (from?.trim().orEmpty() == to?.trim().orEmpty()) {
        null
    } else {
        FieldChange(label, shown(from, value), shown(to, value))
    }

private fun shown(
    raw: String?,
    value: (String) -> DiffValue,
): DiffValue {
    val text = raw?.trim()
    return if (text.isNullOrEmpty()) DiffValue.Unset else value(text)
}

/**
 * Every value but notes is a number and nowhere near this; a full pair of notes ran to three lines and
 * pushed the changed numbers, the part anyone scans a diff for, off the row. The whole text is one tap
 * away in the editor. Cut in UTF-16 units, as RN's `slice` does.
 */
private fun cut(text: String): String =
    if (text.length > MAX_VALUE_LENGTH) "${text.take(MAX_VALUE_LENGTH).trimEnd()}…" else text

private const val MAX_VALUE_LENGTH = 48
