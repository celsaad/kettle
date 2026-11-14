package com.casco.kettle.domain

// A program's half of the import diff (#244), split from `LibraryDiff.kt`, whose rows and
// [FieldChange.cleared] it shares: the weeks that go, arrive, change or lose something (#456).

/**
 * The workout diff's reasoning one level up: week count, plus which weeks differ in any way. Which
 * field of week 4 moved is a question the program editor answers better than a preview line can.
 */
fun diffProgram(
    before: Program,
    after: Program,
): List<FieldChange> {
    val previous = before.weeks.associateBy(::slot)
    val next = after.weeks.associateBy(::slot)
    val removed = before.weeks.filter { slot(it) !in next }
    val added = after.weeks.filter { slot(it) !in previous }
    // Only weeks on both sides: one that's new or gone has its own row, and listing it here too would
    // report the same edit twice.
    val edited = after.weeks.filter { week -> previous[slot(week)]?.let { it != week } == true }
    val losing = edited.filter { week -> previous.getValue(slot(week)).losesIn(week) }
    return listOfNotNull(
        change(DiffLabel.Name, before.name, after.name),
        change(DiffLabel.Weeks, "${before.weeks.size}", "${after.weeks.size}"),
        FieldChange(
            DiffLabel.WeeksRemoved,
            slots(removed),
            NO_VALUE,
            cleared = removed.size,
        ).takeIf { removed.isNotEmpty() },
        FieldChange(DiffLabel.WeeksAdded, NO_VALUE, slots(added)).takeIf { added.isNotEmpty() },
        FieldChange(DiffLabel.WeeksEdited, NO_VALUE, slots(edited)).takeIf { edited.isNotEmpty() },
        FieldChange(
            DiffLabel.WeeksLosing,
            NO_VALUE,
            slots(losing),
            cleared = losing.size,
        ).takeIf { losing.isNotEmpty() },
    )
}

/**
 * Whether this week, replaced by [now], loses anything: its note, its workout to a rest day, or a
 * change. A change whose target stays but whose patch moves is edited, not lost; one that keeps its
 * target but drops a key loses that key.
 */
private fun ProgramWeek.losesIn(now: ProgramWeek): Boolean {
    val note = !notes.isNullOrBlank() && now.notes.isNullOrBlank()
    val workout = this is ProgramWeek.Training && now is ProgramWeek.RestDay
    val kept = (now as? ProgramWeek.Training)?.overrides.orEmpty().associateBy(::target)
    val had = (this as? ProgramWeek.Training)?.overrides.orEmpty()
    return note || workout || had.any { kept[target(it)]?.config?.keys?.containsAll(it.config.keys) != true }
}

private fun target(override: ProgramOverride): String =
    when (override) {
        is ProgramOverride.ExerciseOverride -> "exercise:${override.exerciseId}"
        is ProgramOverride.BlockOverride -> "block:${override.blockId}"
    }

private fun slots(weeks: List<ProgramWeek>) =
    DiffValue.Text(
        weeks.joinToString(", ") { week ->
            week.day?.ifEmpty { null }?.let { "${week.week} ($it)" }
                ?: "${week.week}"
        },
    )

/** A week's place in the rotation: its number and day, which no two weeks share. */
private fun slot(week: ProgramWeek) = week.week to week.day.orEmpty()
