package com.casco.kettle.domain

// The session editor's set moves (#442): a set taken out with what Undo needs to put it back exactly,
// and a set added as a copy of the last. On the drafts, which nothing writes until Save.

/**
 * A set taken out of draft [draft] at [row], whole: its values, the rest the timer logged, and the
 * kilograms behind its load, so Undo puts back the set that was there rather than one rebuilt from text.
 */
data class RemovedSet(
    val draft: Int,
    val row: Int,
    val form: SetForm,
)

/** The drafts without set [row] of draft [draft], and what was taken; null for a set that isn't there. */
fun List<EntryDraft>.removingSet(
    draft: Int,
    row: Int,
): Pair<List<EntryDraft>, RemovedSet>? {
    val form = getOrNull(draft)?.forms?.getOrNull(row) ?: return null
    val removed = changing(draft) { forms -> forms.filterIndexed { i, _ -> i != row } }
    return removed to RemovedSet(draft, row, form)
}

/** The drafts with [removed] back where it was, or at the end where the sets after it have since gone. */
fun List<EntryDraft>.restoring(removed: RemovedSet): List<EntryDraft> =
    changing(removed.draft) { forms ->
        forms.toMutableList().apply { add(removed.row.coerceAtMost(size), removed.form) }
    }

/** Whether the entry logs sets, numbered: reps and holds do; the other types log one result. */
val EntryDraft.logsSets: Boolean
    get() = entry is SessionEntry.Reps || entry is SessionEntry.TimedHold

/** Whether draft [entry] takes another set: one that logs sets and has one to copy. */
val EntryDraft.canAddSet: Boolean
    get() = logsSets && forms.isNotEmpty()

/**
 * Draft [draft] with a set more, a copy of its last: the same reps and load, or the same hold, with
 * the effort blank, since it's that set's own, and no rest, since the timer logged none. The stored
 * kilograms come with the load, so the copy can't drift from the set it copies (#442).
 */
fun List<EntryDraft>.addingSet(draft: Int): List<EntryDraft> {
    if (getOrNull(draft)?.canAddSet != true) return this
    return changing(draft) { forms ->
        val last = forms.last()
        forms + last.copy(values = last.values + (EntryField.Rpe to ""), restTakenSec = 0.0)
    }
}

private fun List<EntryDraft>.changing(
    draft: Int,
    change: (List<SetForm>) -> List<SetForm>,
): List<EntryDraft> = mapIndexed { i, entry -> if (i == draft) entry.copy(forms = change(entry.forms)) else entry }
