package com.casco.kettle.domain

/** Which of the library's three lists an item is in. */
enum class LibraryItemKind { Exercise, Workout, Program }

/** One id an import adds or replaces. It carries no diff: only a row on screen ever asks for one. */
data class ChangedItem(
    val id: String,
    val kind: LibraryItemKind,
    val isNew: Boolean,
)

/**
 * What an import is about to do (#244), as RN's `import.tsx` worked it out inline: the ids it adds and
 * replaces, counted, and what each replaced one loses. [before] is the library the merge was made
 * against, [merged] what Merge would write.
 */
class ImportPreview(
    private val before: Library,
    val merged: Library,
    summary: MergeSummary,
) {
    /**
     * Programs are listed and counted with the rest because the merge writes all three: a program-only
     * file once previewed as "0 new, 0 updated" and then merged its programs anyway.
     */
    val items: List<ChangedItem> =
        with(summary) {
            items(newExercises, updatedExercises, LibraryItemKind.Exercise) +
                items(newWorkouts, updatedWorkouts, LibraryItemKind.Workout) +
                items(newPrograms, updatedPrograms, LibraryItemKind.Program)
        }

    val newCount: Int = items.count { it.isNew }

    val updatedCount: Int = items.size - newCount

    /** The third tile: workouts the file touches, new or replaced. */
    val workoutCount: Int = items.count { it.kind == LibraryItemKind.Workout }

    /**
     * What updated [item] loses, weights in [units]; nothing for a new one. Asked per row drawn rather
     * than worked out for every id up front: a re-imported library is all updates, and diffing every
     * one of them on every frame is the expensive half of the preview for rows nobody sees.
     */
    fun changes(
        item: ChangedItem,
        units: UnitSystem,
    ): List<FieldChange> {
        if (item.isNew) return emptyList()
        return when (item.kind) {
            LibraryItemKind.Exercise -> {
                diffById(
                    Library::exercises,
                    Exercise::id,
                    item.id,
                ) { b, a -> diffExercise(b, a, units) }
            }

            LibraryItemKind.Workout -> {
                diffById(Library::workouts, Workout::id, item.id, ::diffWorkout)
            }

            LibraryItemKind.Program -> {
                diffById(Library::programs, Program::id, item.id, ::diffProgram)
            }
        }
    }

    private fun <T> diffById(
        list: (Library) -> List<T>,
        idOf: (T) -> String,
        id: String,
        diff: (T, T) -> List<FieldChange>,
    ): List<FieldChange> {
        val was = list(before).firstOrNull { idOf(it) == id }
        val will = list(merged).firstOrNull { idOf(it) == id }
        return if (was != null && will != null) diff(was, will) else emptyList()
    }

    private fun items(
        added: List<String>,
        updated: List<String>,
        kind: LibraryItemKind,
    ) = added.map { ChangedItem(it, kind, isNew = true) } + updated.map { ChangedItem(it, kind, isNew = false) }
}

/**
 * The first [COLLAPSED_CHANGES] until [expanded]. A re-imported library of any size renders a row per
 * id, and a wall is not a review: the count tiles say how much, these rows answer "is my own stuff in
 * here", which is a question asked about a few ids.
 */
fun <T> List<T>.collapsed(expanded: Boolean): List<T> = if (expanded) this else take(COLLAPSED_CHANGES)

/** What fits above the fold beside the tiles on a small phone, the only thing it is chosen for. */
const val COLLAPSED_CHANGES = 8
