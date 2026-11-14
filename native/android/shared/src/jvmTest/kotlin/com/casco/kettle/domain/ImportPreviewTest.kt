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
        }
    }

    // An update replaces whole: what it leaves out is cleared, the third tile's count (#443).
    @Test
    fun `counts the fields an update clears`() {
        val noted = pullUps.copy(notes = "Dead hang first.", config = ExerciseConfig.Reps(3, 8, 12, 20.0, 90.0))
        val before = Library(1, listOf(noted), listOf(pushDay), emptyList())
        val merged = mergeLibraries(before, Library(1, listOf(pullUps), emptyList(), emptyList())) as MergeResult.Ok

        // The notes, the top of the range and the weight go; nothing else changed.
        assertEquals(3, ImportPreview(before, merged.library, merged.summary).fieldsCleared(UnitSystem.METRIC))
    }

    private fun cleared(
        before: Library,
        incoming: Library,
    ): Int {
        val merged = mergeLibraries(before, incoming) as MergeResult.Ok
        return ImportPreview(before, merged.library, merged.summary).fieldsCleared(UnitSystem.METRIC)
    }

    // The tile once counted only unset exercise fields, and read 0 for an import that took a lot away (#456).
    @Test
    fun `counts a retype's settings the new type doesn't set`() {
        val noted = pullUps.copy(notes = "Dead hang first.", config = ExerciseConfig.Reps(3, 8, 12, 20.0, 90.0))
        val before = Library(1, listOf(noted), emptyList(), emptyList())
        val hold = pullUps.copy(config = ExerciseConfig.TimedHold(3, 30.0, restSec = 60.0))

        // The range's two ends, the weight and the notes; sets and rest change, which isn't clearing them.
        assertEquals(4, cleared(before, Library(1, listOf(hold), emptyList(), emptyList())))
    }

    @Test
    fun `counts each exercise reference a workout loses, a repeat as many times as it goes`() {
        val blocks = listOf("pull-ups", "dips", "pull-ups", "rows").map { WorkoutBlock.ExerciseBlock(it) }
        val exercises = listOf("dips", "rows", "squats").map { Exercise(it, it, pullUps.config) } + pullUps
        val before = Library(1, exercises, listOf(pushDay.copy(blocks = blocks)), emptyList())
        val after = pushDay.copy(blocks = listOf(blocks[0], WorkoutBlock.ExerciseBlock("squats")))

        // One pull-ups, the dips and the rows go; the squats arriving clears nothing.
        assertEquals(3, cleared(before, Library(1, emptyList(), listOf(after), emptyList())))
    }

    @Test
    fun `counts a program's weeks that go, and the notes and changes its kept weeks drop`() {
        val press = ProgramOverride.ExerciseOverride("press", emptyMap())
        val weeks =
            listOf(
                ProgramWeek.Training(1, workoutId = "push-day", notes = "Easy.", overrides = listOf(press)),
                ProgramWeek.Training(2, workoutId = "push-day"),
                ProgramWeek.RestDay(3),
            )
        val before = Library(1, listOf(pullUps), listOf(pushDay), listOf(Program("base", "Base", weeks)))
        val after = Program("base", "Base", listOf(ProgramWeek.Training(1, workoutId = "push-day"), weeks[1]))

        // Week 3 goes; week 1 keeps its place but loses its note and its change.
        assertEquals(3, cleared(before, Library(1, emptyList(), emptyList(), listOf(after))))
    }

    @Test
    fun `marks a workout's lost exercises as cleared, and its new ones not`() {
        val before =
            Library(1, listOf(pullUps, Exercise("squats", "Squats", pullUps.config)), listOf(pushDay), emptyList())
        val after = pushDay.copy(blocks = listOf(WorkoutBlock.ExerciseBlock("squats")))
        val merged = mergeLibraries(before, Library(1, emptyList(), listOf(after), emptyList())) as MergeResult.Ok
        val rows =
            ImportPreview(
                before,
                merged.library,
                merged.summary,
            ).changes(ChangedItem("push-day", LibraryItemKind.Workout, false), UnitSystem.METRIC)

        assertEquals(listOf(true, false), rows.filter { it.label == DiffLabel.Exercises }.map { it.clears })
    }

    @Test
    fun `counts nothing cleared by a new item, or an update that only changes values`() {
        val incoming =
            Library(
                1,
                listOf(Exercise("dips", "Dips", pullUps.config), pullUps.copy(name = "Chin-ups")),
                emptyList(),
                emptyList(),
            )

        assertEquals(0, preview(incoming).fieldsCleared(UnitSystem.METRIC))
    }

    @Test
    fun `names an item as the import has it`() {
        val incoming = Library(1, listOf(pullUps.copy(name = "Chin-ups")), emptyList(), emptyList())
        with(preview(incoming)) { assertEquals("Chin-ups", name(items.single())) }
    }

    // `mergeById` classifies by id, not by value, so re-importing your own export is all updates.
    @Test
    fun `counts a re-imported identical library as updates`() {
        with(preview(current)) {
            assertEquals(0, newCount)
            assertEquals(2, updatedCount)
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
