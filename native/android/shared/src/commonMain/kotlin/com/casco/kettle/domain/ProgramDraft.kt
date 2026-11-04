package com.casco.kettle.domain

// The program editor's draft (#243) and its moves, which RN kept inline in `program-editor.tsx`. Save
// is `ProgramEditing.kt`'s; the overrides panel under each week is `OverrideEditing.kt`'s.

/**
 * One week of the draft, under a [key] that stays with it: two cards can share a number, and a
 * card's position moves when one above it goes.
 *
 * [week]'s day and notes are held as typed and trimmed by [submitProgram]. RN trimmed on every
 * keystroke, which ate the space in "Day 1" the moment it was typed.
 */
data class DraftWeek(
    val key: Int,
    val week: ProgramWeek,
)

/** The program being edited, as the editor's own copy: nothing reaches the library until Save. */
data class ProgramDraft(
    val name: String = "",
    val weeks: List<DraftWeek> = emptyList(),
    /** The key the next added week gets, so no two weeks of one draft ever share one. */
    val nextKey: Int = weeks.size,
) {
    /**
     * Distinct week numbers, not cards: a four-week program written out a day at a time is 28 cards,
     * and "28 weeks" is neither what the author has nor what Programs says of it.
     */
    val weekCount: Int get() = weeks.map { it.week.week }.distinct().size
}

/** [program]'s draft, or an empty one for a new program. */
fun programDraftOf(program: Program?): ProgramDraft =
    ProgramDraft(program?.name.orEmpty(), program?.weeks.orEmpty().mapIndexed(::DraftWeek))

/**
 * A week at the end, numbered past the highest there, not by count: counting renumbers into a
 * collision once a week has been removed. Its workout is the first of [workouts], so the common
 * program, one workout every week, needs no picking; with none, it has none, and Save says so.
 */
fun ProgramDraft.addingWeek(workouts: List<Workout>): ProgramDraft {
    val number = (weeks.maxOfOrNull { it.week.week } ?: 0) + 1
    val week = ProgramWeek.Training(number, workoutId = workouts.firstOrNull()?.id.orEmpty())
    return copy(weeks = weeks + DraftWeek(nextKey, week), nextKey = nextKey + 1)
}

fun ProgramDraft.removingWeek(key: Int): ProgramDraft = copy(weeks = weeks.filterNot { it.key == key })

/** The week keyed [key] renumbered [by], never below 1: week numbers are 1-based everywhere. */
fun ProgramDraft.steppingWeek(
    key: Int,
    by: Int,
): ProgramDraft = withWeek(key) { it.withShared(week = (it.week + by).coerceAtLeast(1)) }

fun ProgramDraft.withDay(
    key: Int,
    day: String,
): ProgramDraft = withWeek(key) { it.withShared(day = day) }

fun ProgramDraft.withNotes(
    key: Int,
    notes: String,
): ProgramDraft = withWeek(key) { it.withShared(notes = notes) }

/**
 * The week keyed [key] switched between rest and training. Rest is a change of kind: the workout and
 * the overrides go, since the schema refuses a rest week carrying either. Back to training, it gets
 * the first of [workouts], as a new week does, rather than the one it had: remembering that would
 * mean keeping a shadow of a field the week no longer has.
 */
fun ProgramDraft.togglingRest(
    key: Int,
    workouts: List<Workout>,
): ProgramDraft =
    withWeek(key) {
        when (it) {
            is ProgramWeek.Training -> {
                ProgramWeek.RestDay(it.week, it.day, it.notes)
            }

            is ProgramWeek.RestDay -> {
                ProgramWeek.Training(it.week, it.day, workouts.firstOrNull()?.id.orEmpty(), it.notes)
            }
        }
    }

/** The week keyed [key] running [workoutId]. A rest week has no workout to choose, so it stays as it is. */
fun ProgramDraft.choosingWorkout(
    key: Int,
    workoutId: String,
): ProgramDraft = withWeek(key) { (it as? ProgramWeek.Training)?.copy(workoutId = workoutId) ?: it }

/** The week keyed [key] with [overrides], none stored as absent, as the file writes a week with none. */
fun ProgramDraft.withOverrides(
    key: Int,
    overrides: List<ProgramOverride>,
): ProgramDraft = withWeek(key) { (it as? ProgramWeek.Training)?.copy(overrides = overrides.ifEmpty { null }) ?: it }

private fun ProgramDraft.withWeek(
    key: Int,
    change: (ProgramWeek) -> ProgramWeek,
): ProgramDraft = copy(weeks = weeks.map { if (it.key == key) it.copy(week = change(it.week)) else it })
