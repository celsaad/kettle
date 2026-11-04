package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What the import preview lists and counts (#244), the arithmetic RN's `import.tsx` kept inline. The
 * screen's own cases are `ImportScreenTest`'s.
 */
class ImportPreviewTest {
    private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, restSec = 90.0))
    private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
    private val current = Library(1, listOf(pullUps), listOf(pushDay), emptyList())

    private fun preview(incoming: Library) =
        when (val merged = mergeLibraries(current, incoming)) {
            is MergeResult.Ok -> ImportPreview(current, merged.library, merged.summary)
            is MergeResult.Refused -> error("refused: ${merged.error}")
        }

    @Test
    fun `lists exercises, workouts and programs, new before updated in each`() {
        val incoming =
            Library(
                1,
                listOf(Exercise("dips", "Dips", pullUps.config), pullUps.copy(name = "Chin-ups")),
                listOf(pushDay),
                listOf(Program("base-6", "Base 6", listOf(ProgramWeek.Training(1, workoutId = "push-day")))),
            )
        assertEquals(
            listOf(
                ChangedItem("dips", LibraryItemKind.Exercise, isNew = true),
                ChangedItem("pull-ups", LibraryItemKind.Exercise, isNew = false),
                ChangedItem("push-day", LibraryItemKind.Workout, isNew = false),
                ChangedItem("base-6", LibraryItemKind.Program, isNew = true),
            ),
            preview(incoming).items,
        )
    }

    // A program-only file once previewed as "0 new, 0 updated" and then merged its programs anyway.
    @Test
    fun `counts programs with everything else`() {
        val programOnly = Library(1, emptyList(), emptyList(), listOf(Program("base-6", "Base 6", emptyList())))
        with(preview(programOnly)) {
            assertEquals(1, newCount)
            assertEquals(0, updatedCount)
            assertEquals(0, workoutCount)
        }
    }

    // `mergeById` classifies by id, not by value, so re-importing your own export is all updates.
    @Test
    fun `counts a re-imported identical library as updates`() {
        with(preview(current)) {
            assertEquals(0, newCount)
            assertEquals(2, updatedCount)
            assertEquals(1, workoutCount)
        }
    }

    @Test
    fun `diffs an updated item against the library it replaces, and a new one not at all`() {
        val preview =
            preview(
                current.copy(
                    exercises = listOf(pullUps.copy(name = "Chin-ups"), Exercise("dips", "Dips", pullUps.config)),
                ),
            )
        assertEquals(
            listOf(FieldChange(DiffLabel.Name, DiffValue.Text("Pull-ups"), DiffValue.Text("Chin-ups"))),
            preview.changes(ChangedItem("pull-ups", LibraryItemKind.Exercise, isNew = false), UnitSystem.METRIC),
        )
        assertEquals(
            emptyList<FieldChange>(),
            preview.changes(ChangedItem("dips", LibraryItemKind.Exercise, isNew = true), UnitSystem.METRIC),
        )
    }

    @Test
    fun `shows the first eight changes until expanded`() {
        val thirty = (0 until 30).map { "ex-$it" }
        assertEquals(thirty.take(8), thirty.collapsed(expanded = false))
        assertEquals(thirty, thirty.collapsed(expanded = true))
    }
}
