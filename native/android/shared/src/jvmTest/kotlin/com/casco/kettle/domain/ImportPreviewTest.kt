package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * What the import preview lists and counts (#244), the arithmetic RN's Import screen kept inline. The
 * screen's own cases are `ImportScreenTest`'s.
 */
class ImportPreviewTest {
    private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, restSec = 90.0))
    private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
    private val current = Library(1, listOf(pullUps), listOf(pushDay), emptyList())

    private fun preview(incoming: Library) =
        when (val merged = mergeLibraries(current, incoming)) {
            is MergeResult.Ok -> ImportPreview(current, merged.library, merged.summary, emptySet())
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
        assertEquals(
            3,
            ImportPreview(before, merged.library, merged.summary, emptySet()).clearedTotal(UnitSystem.METRIC),
        )
    }

    private fun cleared(
        before: Library,
        incoming: Library,
    ): Int {
        val merged = mergeLibraries(before, incoming) as MergeResult.Ok
        return ImportPreview(before, merged.library, merged.summary, emptySet()).clearedTotal(UnitSystem.METRIC)
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

        // Week 3 goes; week 1 keeps its place but loses its note and its change, which is one week losing.
        assertEquals(2, cleared(before, Library(1, emptyList(), emptyList(), listOf(after))))
    }

    private fun programUpdate(
        was: ProgramWeek,
        now: ProgramWeek,
    ): Int {
        val before = Library(1, listOf(pullUps), listOf(pushDay), listOf(Program("base", "Base", listOf(was))))
        return cleared(before, Library(1, emptyList(), emptyList(), listOf(Program("base", "Base", listOf(now)))))
    }

    // An override that only changes its patch takes nothing away: the reviewers' tile read "1 cleared".
    @Test
    fun `counts a change that keeps its target and keys as edited, not cleared`() {
        val sets = { n: Double ->
            ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to OverrideValue.Numeric(n)))
        }
        val week = { n: Double -> ProgramWeek.Training(1, workoutId = "push-day", overrides = listOf(sets(n))) }

        assertEquals(0, programUpdate(week(3.0), week(4.0)))
    }

    @Test
    fun `counts a change that keeps its target but drops a key`() {
        val patch = { keys: List<String> ->
            ProgramOverride.ExerciseOverride("pull-ups", keys.associateWith { OverrideValue.Numeric(3.0) })
        }
        val week = { keys: List<String> ->
            ProgramWeek.Training(1, workoutId = "push-day", overrides = listOf(patch(keys)))
        }

        assertEquals(1, programUpdate(week(listOf("sets", "rest_sec")), week(listOf("sets"))))
    }

    @Test
    fun `counts a training week turned into a rest day`() {
        assertEquals(1, programUpdate(ProgramWeek.Training(1, workoutId = "push-day"), ProgramWeek.RestDay(1)))
    }

    // A renamed day was "1 cleared" over a diff that said nothing changed (#456 review).
    @Test
    fun `lists a renamed day as one slot removed and one added`() {
        val before =
            Library(
                1,
                listOf(pullUps),
                listOf(pushDay),
                listOf(Program("base", "Base", listOf(ProgramWeek.Training(1, "Mon", "push-day")))),
            )
        val after = Program("base", "Base", listOf(ProgramWeek.Training(1, "Monday", "push-day")))
        val merged = mergeLibraries(before, Library(1, emptyList(), emptyList(), listOf(after))) as MergeResult.Ok
        val rows =
            ImportPreview(
                before,
                merged.library,
                merged.summary,
                emptySet(),
            ).changes(ChangedItem("base", LibraryItemKind.Program, false), UnitSystem.METRIC)

        assertEquals(
            listOf(
                FieldChange(DiffLabel.WeeksRemoved, DiffValue.Text("1 (Mon)"), DiffValue.Text("\u2014"), cleared = 1),
                FieldChange(DiffLabel.WeeksAdded, DiffValue.Text("\u2014"), DiffValue.Text("1 (Monday)")),
            ),
            rows,
        )
    }

    // The count pointed at nothing in the open item: a retype's lost settings are now rows of their own.
    @Test
    fun `lists a retype's lost settings as cleared rows the count agrees with`() {
        val before =
            Library(
                1,
                listOf(pullUps.copy(config = ExerciseConfig.Reps(3, 8, 12, 20.0, 90.0))),
                emptyList(),
                emptyList(),
            )
        val hold = pullUps.copy(config = ExerciseConfig.TimedHold(3, 30.0, restSec = 60.0))
        val merged = mergeLibraries(before, Library(1, listOf(hold), emptyList(), emptyList())) as MergeResult.Ok
        val preview = ImportPreview(before, merged.library, merged.summary, emptySet())
        val item = ChangedItem("pull-ups", LibraryItemKind.Exercise, false)

        assertEquals(3, preview.changes(item, UnitSystem.METRIC).count { it.clears })
        assertEquals(3, preview.clearedBy(item, UnitSystem.METRIC))
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
                emptySet(),
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

        assertEquals(0, preview(incoming).clearedTotal(UnitSystem.METRIC))
    }

    // The tile, a row's count and its open diff all ask for the same diff; a re-imported library is all updates.
    @Test
    fun `diffs an item once per preview, whoever asks`() {
        val preview = preview(Library(1, listOf(pullUps.copy(name = "Chin-ups")), emptyList(), emptyList()))
        val item = preview.items.single()

        assertSame(preview.changes(item, UnitSystem.METRIC), preview.changes(item, UnitSystem.METRIC))
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
