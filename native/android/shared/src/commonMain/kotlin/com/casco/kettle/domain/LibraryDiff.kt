package com.casco.kettle.domain

// What an import is about to overwrite, field by field (#244), as RN's library diff. Merge
// replaces a matching id whole (§6), so an id the user tweaked loses the tweak, and this is what the
// preview shows of it. Descriptors rather than words: the import screen renders them.

/** Which part of an item moved. */
sealed interface DiffLabel {
    data object Name : DiffLabel

    data object Type : DiffLabel

    data object Notes : DiffLabel

    /** Equipment items (#393) the update takes away, as the workout's exercises are listed. */
    data object EquipmentRemoved : DiffLabel

    /** Equipment items the update adds. */
    data object EquipmentAdded : DiffLabel

    data object Blocks : DiffLabel

    /**
     * Blocks on both sides, at the same place, holding the same exercises, whose settings differ: a
     * rest length, a circuit's rounds or rests, an override. Without it, an update undoing the
     * workout editor's rest (#440) read as identical (#443).
     */
    data object BlocksEdited : DiffLabel

    /** Exercise references a workout gained or lost, as ids: one dropped by the same import has no name left. */
    data object Exercises : DiffLabel

    data object Weeks : DiffLabel

    /** Weeks on both sides that differ in any way, by number and day. */
    data object WeeksEdited : DiffLabel

    /** Slots the update adds, by number and day: a renamed day is one removed and one added. */
    data object WeeksAdded : DiffLabel

    /** Slots the update takes away, by number and day. */
    data object WeeksRemoved : DiffLabel

    /** Weeks on both sides that lose a note, a change (override) or their workout to a rest day. */
    data object WeeksLosing : DiffLabel

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

    /**
     * Equipment items as the file spells them, never empty (#393). A list rather than [Text], because
     * a known item reads in the reader's language and only the screen can name it; the user's own
     * words stay verbatim. Uncut, since a cut taken here, before the names are known, would cut a
     * different string from the one shown. What keeps it short is that it holds only the items that
     * moved, one row for those that go and one for those that come; a long hand-written list wraps.
     */
    data class Items(
        val items: List<String>,
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

/**
 * [label] moved from [from] to [to]. [cleared] is how much of what it lists the update takes away: a
 * field left unset is one, a workout's lost exercises one each. Import's counts and warning marks are
 * this number and nothing else (#456), so the tile, an item's count and its open rows always agree.
 */
data class FieldChange(
    val label: DiffLabel,
    val from: DiffValue,
    val to: DiffValue,
    val cleared: Int = if (to == DiffValue.Unset && from != DiffValue.Unset) 1 else 0,
) {
    /** Whether the update takes anything away here: what Import marks with its warning. */
    val clears: Boolean get() = cleared > 0
}

/**
 * What moved between [before] and [after], the same exercise by id, with weights in [units] and
 * [knownEquipment] the vocabulary's keys, which decide how two items are compared. A type
 * change is reported instead of the config, not beside it: listing both configs would call every field
 * changed and bury the one line that explains it. Only what the new type has no place for is listed,
 * as cleared, since that is what the import takes away (#456).
 */
fun diffExercise(
    before: Exercise,
    after: Exercise,
    units: UnitSystem,
    knownEquipment: Set<String>,
): List<FieldChange> {
    val retyped = before.config.type != after.config.type
    val changes =
        listOfNotNull(
            change(DiffLabel.Name, before.name, after.name),
            FieldChange(DiffLabel.Type, DiffValue.Type(before.config.type), DiffValue.Type(after.config.type))
                .takeIf { retyped },
            change(DiffLabel.Notes, before.notes, after.notes),
        ) + equipmentChanges(before.equipment, after.equipment, knownEquipment)
    val beforeValues = draftOf(before, units).values
    val afterValues = draftOf(after, units).values
    if (retyped) {
        return changes +
            configFields(before.config.type).mapNotNull { field ->
                beforeValues[field.key]
                    ?.takeIf { it.isNotBlank() && afterValues[field.key].isNullOrBlank() }
                    ?.let { FieldChange(DiffLabel.Field(field.name), measured(field, it), DiffValue.Unset) }
            }
    }
    // In the form's order, so a diff lists fields as the editor the reader opens next shows them.
    return changes +
        configFields(after.config.type).mapNotNull { field ->
            change(DiffLabel.Field(field.name), beforeValues[field.key], afterValues[field.key]) {
                measured(field, it)
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
    // Each reference, so losing one of two pull-ups is listed and counted, as the block count says.
    val gone = had.minusEach(has)
    val added = has.minusEach(had)
    // Same place, same exercises, different settings: what the count and the references can't see.
    val settings =
        before.blocks.zip(after.blocks).mapIndexedNotNull { i, (was, now) ->
            (i + 1).takeIf { was != now && exercisesIn(was) == exercisesIn(now) }
        }
    // Nothing else to say and still not equal is a reorder: every position that moved, so an empty
    // diff only ever means the two really are the same.
    val reordered =
        before.blocks.zip(after.blocks).mapIndexedNotNull { i, (was, now) -> (i + 1).takeIf { was != now } }
    val quiet =
        before.name.trim() == after.name.trim() && before.blocks.size == after.blocks.size &&
            had.sorted() == has.sorted()
    val edited = if (settings.isEmpty() && quiet && before.blocks != after.blocks) reordered else settings
    return listOfNotNull(
        change(DiffLabel.Name, before.name, after.name),
        change(DiffLabel.Blocks, "${before.blocks.size}", "${after.blocks.size}"),
        FieldChange(
            DiffLabel.Exercises,
            DiffValue.Text(gone.joinToString(", ")),
            NO_VALUE,
            cleared = gone.size,
        ).takeIf { gone.isNotEmpty() },
        FieldChange(
            DiffLabel.Exercises,
            NO_VALUE,
            DiffValue.Text(added.joinToString(", ")),
        ).takeIf { added.isNotEmpty() },
        FieldChange(
            DiffLabel.BlocksEdited,
            NO_VALUE,
            DiffValue.Text(edited.joinToString(", ")),
        ).takeIf { edited.isNotEmpty() },
    )
}

/**
 * Two rows, as the workout's exercises are listed: the items that go, counted as [FieldChange.cleared]
 * so dropping two of three marks two (#456), and the items that come. A known item is compared as it
 * is matched, [equipmentMatchKey], so `Dumbbell` → `dumbbell` is the same item and a row for it would
 * warn about nothing. The user's own words are compared as written, trimmed, because they are shown
 * as written: `fat grips` → `Fat Grips` changes what the exercise reads. Order is not compared at
 * all, deliberately: an exercise's items are what it needs at hand, not a sequence, so a reorder
 * alone shows no row even though the file changed.
 */
private fun equipmentChanges(
    before: List<String>,
    after: List<String>,
    known: Set<String>,
): List<FieldChange> {
    val beforeKeys = before.map { equipmentIdentity(it, known) }.toSet()
    val afterKeys = after.map { equipmentIdentity(it, known) }.toSet()
    val gone = before.filter { equipmentIdentity(it, known) !in afterKeys }
    val added = after.filter { equipmentIdentity(it, known) !in beforeKeys }
    return listOfNotNull(
        FieldChange(
            DiffLabel.EquipmentRemoved,
            DiffValue.Items(gone),
            NO_VALUE,
            cleared = gone.size,
        ).takeIf { gone.isNotEmpty() },
        FieldChange(DiffLabel.EquipmentAdded, NO_VALUE, DiffValue.Items(added)).takeIf { added.isNotEmpty() },
    )
}

/** What two items are compared by: a known item by its key, anything else by its trimmed words. */
private fun equipmentIdentity(
    item: String,
    known: Set<String>,
): Pair<Boolean, String> = knownEquipment(item, known)?.let { true to it } ?: (false to trimEquipment(item))

private fun measured(
    field: ConfigField,
    number: String,
): DiffValue = field.unit?.let { DiffValue.Measured(number, it) } ?: DiffValue.Text(number)

/** This list less one of each of [other]'s entries: a multiset difference, repeats kept. */
private fun List<String>.minusEach(other: List<String>): List<String> {
    val left = other.toMutableList()
    return filter { !left.remove(it) }
}

/** One entry per reference, not per id, as RN's `referencedExercises`. */
private fun exercisesIn(block: WorkoutBlock): List<String> =
    when (block) {
        is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
        is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
    }

/** RN's dash for the empty side of a list that grew or shrank: a glyph, the same in every language. */
internal val NO_VALUE = DiffValue.Text("—")

/**
 * Compares the **raw** values and cuts only what's shown: comparing the cut ones would make two notes
 * that first differ past the cut look identical, and the change would vanish from the one screen that
 * exists to show it.
 */
internal fun change(
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
