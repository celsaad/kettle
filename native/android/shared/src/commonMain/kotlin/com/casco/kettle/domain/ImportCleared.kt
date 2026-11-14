package com.casco.kettle.domain

// What an update takes away, item by item: Import's "cleared" count (#443), widened past an
// exercise's unset fields on #456. `ImportPreview.clearedBy` picks which of these an item gets.

/**
 * What an exercise update takes away (#456): its fields left unset, or, when its type changes, each
 * setting the old type had that the new one doesn't set, a reps range going to a hold say. Sets and
 * rest, which both carry, are changed rather than cleared.
 */
internal fun exerciseCleared(
    before: Exercise,
    after: Exercise,
    units: UnitSystem,
): Int {
    val notes = if (!before.notes.isNullOrBlank() && after.notes.isNullOrBlank()) 1 else 0
    if (before.config.type == after.config.type) return diffExercise(before, after, units).count { it.clears }
    val kept = draftOf(after, units).values
    return draftOf(before, units).values.count { (key, text) -> text.isNotBlank() && kept[key].isNullOrBlank() } + notes
}

/** The exercise references a workout update drops, one per reference, as the diff lists them. */
internal fun referencesLost(
    before: Workout,
    after: Workout,
): Int {
    val left = after.blocks.flatMap(::exercisesIn).toMutableList()
    return before.blocks.flatMap(::exercisesIn).count { !left.remove(it) }
}

/**
 * What a program update takes away (#456): each week that goes, and on the weeks it keeps, a note
 * dropped and each change (override) no longer there.
 */
internal fun programCleared(
    before: Program,
    after: Program,
): Int {
    val kept = after.weeks.associateBy(::slot)
    return before.weeks.sumOf { week ->
        val now = kept[slot(week)] ?: return@sumOf 1
        val notes = if (!week.notes.isNullOrBlank() && now.notes.isNullOrBlank()) 1 else 0
        val had = (week as? ProgramWeek.Training)?.overrides.orEmpty()
        val has = (now as? ProgramWeek.Training)?.overrides.orEmpty()
        notes + had.count { it !in has }
    }
}
