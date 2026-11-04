package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What an import is about to overwrite, case for case from RN's `library-diff.test.ts`. The words are
 * the screen's (`ImportScreenTest` drives them in `pt`); these are the rules under them.
 */
class LibraryDiffTest {
    private fun reps(config: ExerciseConfig.Reps = ExerciseConfig.Reps(5, 5, targetWeightKg = 60.0, restSec = 120.0)) =
        Exercise("bench", "Bench Press", config)

    private val base = ExerciseConfig.Reps(5, 5, targetWeightKg = 60.0, restSec = 120.0)

    @Test
    fun `names the config field that moved, and only that field`() {
        assertEquals(
            listOf(FieldChange(DiffLabel.Field(FieldName.Sets), DiffValue.Text("5"), DiffValue.Text("6"))),
            diffExercise(reps(), reps(base.copy(sets = 6)), UnitSystem.METRIC),
        )
    }

    @Test
    fun `carries the unit, so a bare number never has to be guessed at`() {
        assertEquals(
            listOf(
                FieldChange(
                    DiffLabel.Field(FieldName.Weight),
                    DiffValue.Measured("60", FieldUnit.Weight),
                    DiffValue.Measured("65", FieldUnit.Weight),
                ),
            ),
            diffExercise(reps(), reps(base.copy(targetWeightKg = 65.0)), UnitSystem.METRIC),
        )
    }

    // A kilogram figure shown to somebody working in pounds describes a change they didn't make.
    @Test
    fun `converts a weight into the reader's own units`() {
        assertEquals(
            listOf(
                FieldChange(
                    DiffLabel.Field(FieldName.Weight),
                    DiffValue.Measured("132.3", FieldUnit.Weight),
                    DiffValue.Measured("143.3", FieldUnit.Weight),
                ),
            ),
            diffExercise(reps(), reps(base.copy(targetWeightKg = 65.0)), UnitSystem.IMPERIAL),
        )
    }

    @Test
    fun `reads a removed optional field as unset rather than as blank`() {
        assertEquals(
            listOf(FieldChange(DiffLabel.Field(FieldName.TargetRepsMax), DiffValue.Text("8"), DiffValue.Unset)),
            diffExercise(reps(base.copy(targetRepsMax = 8)), reps(), UnitSystem.METRIC),
        )
    }

    // Every key of both configs technically changed; listing them would bury the one line that explains it.
    @Test
    fun `reports a type change instead of the config, not alongside it`() {
        val hold = Exercise("bench", "Bench Press", ExerciseConfig.TimedHold(5, holdSecMin = 20.0, restSec = 120.0))
        assertEquals(
            listOf(
                FieldChange(DiffLabel.Type, DiffValue.Type(ExerciseType.Reps), DiffValue.Type(ExerciseType.TimedHold)),
            ),
            diffExercise(reps(), hold, UnitSystem.METRIC),
        )
    }

    @Test
    fun `says nothing about an identical definition`() {
        assertEquals(emptyList<FieldChange>(), diffExercise(reps(), reps(), UnitSystem.METRIC))
    }

    // RN's order: name, type, notes, then the config in the order the form shows it.
    @Test
    fun `lists the name and notes before the config`() {
        val after = reps(base.copy(sets = 6)).copy(name = "Bench", notes = "Pause at the chest")
        assertEquals(
            listOf(
                FieldChange(DiffLabel.Name, DiffValue.Text("Bench Press"), DiffValue.Text("Bench")),
                FieldChange(DiffLabel.Notes, DiffValue.Unset, DiffValue.Text("Pause at the chest")),
                FieldChange(DiffLabel.Field(FieldName.Sets), DiffValue.Text("5"), DiffValue.Text("6")),
            ),
            diffExercise(reps(), after, UnitSystem.METRIC),
        )
    }

    @Nested
    inner class `a long value` {
        private val long = "Stop two reps shy of failure on every set, and hold the top for a beat"

        @Test
        fun `is cut short, so the numbers under it stay visible`() {
            assertEquals(
                listOf(
                    FieldChange(
                        DiffLabel.Notes,
                        DiffValue.Text("Stop two reps shy of failure on every set, and h…"),
                        DiffValue.Text("Slower down."),
                    ),
                ),
                diffExercise(reps().copy(notes = long), reps().copy(notes = "Slower down."), UnitSystem.METRIC),
            )
        }

        // Cut before comparing, two notes that first differ past the cut would look identical.
        @Test
        fun `is still compared in full`() {
            val changes =
                diffExercise(
                    reps().copy(notes = "$long — three sets"),
                    reps().copy(notes = "$long — four sets"),
                    UnitSystem.METRIC,
                )
            assertEquals(listOf(DiffLabel.Notes), changes.map { it.label })
        }
    }

    @Nested
    inner class Workouts {
        private fun push(vararg blocks: WorkoutBlock) = Workout("push", "Push", blocks.toList())

        private fun circuit(vararg ids: String) = WorkoutBlock.Circuit(rounds = 3, members = ids.map(::CircuitMember))

        @Test
        fun `reports the block count and which exercise references vanished`() {
            val before = push(WorkoutBlock.ExerciseBlock("bench"), WorkoutBlock.ExerciseBlock("dips"))
            val after = push(WorkoutBlock.ExerciseBlock("bench"))
            assertEquals(
                listOf(
                    FieldChange(DiffLabel.Blocks, DiffValue.Text("2"), DiffValue.Text("1")),
                    FieldChange(DiffLabel.Exercises, DiffValue.Text("dips"), DiffValue.Text("—")),
                ),
                diffWorkout(before, after),
            )
        }

        // One circuit either way, so a count-only diff would call this no change at all.
        @Test
        fun `sees inside a circuit, so a swapped member is not invisible`() {
            assertEquals(
                listOf(
                    FieldChange(DiffLabel.Exercises, DiffValue.Text("dips"), DiffValue.Text("—")),
                    FieldChange(DiffLabel.Exercises, DiffValue.Text("—"), DiffValue.Text("rows")),
                ),
                diffWorkout(push(circuit("bench", "dips")), push(circuit("bench", "rows"))),
            )
        }
    }

    @Nested
    inner class Programs {
        private fun base(vararg weeks: ProgramWeek) = Program("base", "Base", weeks.toList())

        private fun week(
            number: Long,
            workout: String,
            day: String? = null,
        ) = ProgramWeek.Training(number, day, workout)

        @Test
        fun `reports which shared weeks were edited`() {
            assertEquals(
                listOf(FieldChange(DiffLabel.WeeksEdited, DiffValue.Text("—"), DiffValue.Text("2"))),
                diffProgram(base(week(1, "push"), week(2, "push")), base(week(1, "push"), week(2, "pull"))),
            )
        }

        // Week 2 is new, so it isn't also "edited": reporting both would describe one change twice.
        @Test
        fun `counts an added week once, in the count, not twice`() {
            assertEquals(
                listOf(FieldChange(DiffLabel.Weeks, DiffValue.Text("1"), DiffValue.Text("2"))),
                diffProgram(base(week(1, "push")), base(week(1, "push"), week(2, "push"))),
            )
        }

        // Keyed by number alone, the two would collide and the edit read as week 1 changing twice, or not at all.
        @Test
        fun `distinguishes two same-numbered weeks by their day label`() {
            val before = base(week(1, "push", "Monday"), week(1, "pull", "Thursday"))
            val after = base(week(1, "push", "Monday"), week(1, "legs", "Thursday"))
            assertEquals(
                listOf(FieldChange(DiffLabel.WeeksEdited, DiffValue.Text("—"), DiffValue.Text("1 (Thursday)"))),
                diffProgram(before, after),
            )
        }

        @Test
        fun `sees a training week turned into a rest day`() {
            assertEquals(
                listOf(FieldChange(DiffLabel.WeeksEdited, DiffValue.Text("—"), DiffValue.Text("2"))),
                diffProgram(base(week(1, "push"), week(2, "push")), base(week(1, "push"), ProgramWeek.RestDay(2))),
            )
        }
    }
}
