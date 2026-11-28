package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What an import is about to overwrite, from RN's library diff tests. The words are
 * the screen's (`ImportScreenTest` drives them in `pt`); these are the rules under them.
 */
class LibraryDiffTest {
    // A vocabulary of the test's own, the keys these cases name: content/equipment.json's are the app's.
    private val known = setOf("barbell", "bench", "rack", "dumbbell", "pull_up_bar")

    private fun reps(config: ExerciseConfig.Reps = ExerciseConfig.Reps(5, 5, targetWeightKg = 60.0, restSec = 120.0)) =
        Exercise("bench", "Bench Press", config)

    private val base = ExerciseConfig.Reps(5, 5, targetWeightKg = 60.0, restSec = 120.0)

    @Test
    fun `names the config field that moved, and only that field`() {
        assertEquals(
            listOf(FieldChange(DiffLabel.Field(FieldName.Sets), DiffValue.Text("5"), DiffValue.Text("6"))),
            diffExercise(reps(), reps(base.copy(sets = 6)), UnitSystem.METRIC, known),
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
            diffExercise(reps(), reps(base.copy(targetWeightKg = 65.0)), UnitSystem.METRIC, known),
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
            diffExercise(reps(), reps(base.copy(targetWeightKg = 65.0)), UnitSystem.IMPERIAL, known),
        )
    }

    @Test
    fun `reads a removed optional field as unset rather than as blank`() {
        assertEquals(
            listOf(FieldChange(DiffLabel.Field(FieldName.TargetRepsMax), DiffValue.Text("8"), DiffValue.Unset)),
            diffExercise(reps(base.copy(targetRepsMax = 8)), reps(), UnitSystem.METRIC, known),
        )
    }

    // Every key of both configs technically changed; listing them would bury the one line that explains it.
    @Test
    fun `reports a type change instead of the config, with only what the new type has no place for`() {
        val hold = Exercise("bench", "Bench Press", ExerciseConfig.TimedHold(5, holdSecMin = 20.0, restSec = 120.0))
        // Sets and rest carry, changed or not; the reps and the weight go, as cleared (#456).
        assertEquals(
            listOf(
                FieldChange(DiffLabel.Type, DiffValue.Type(ExerciseType.Reps), DiffValue.Type(ExerciseType.TimedHold)),
                FieldChange(DiffLabel.Field(FieldName.TargetReps), DiffValue.Text("5"), DiffValue.Unset),
                FieldChange(
                    DiffLabel.Field(FieldName.Weight),
                    DiffValue.Measured("60", FieldUnit.Weight),
                    DiffValue.Unset,
                ),
            ),
            diffExercise(reps(), hold, UnitSystem.METRIC, known),
        )
    }

    @Test
    fun `says nothing about an identical definition`() {
        assertEquals(emptyList<FieldChange>(), diffExercise(reps(), reps(), UnitSystem.METRIC, known))
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
            diffExercise(reps(), after, UnitSystem.METRIC, known),
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
                diffExercise(reps().copy(notes = long), reps().copy(notes = "Slower down."), UnitSystem.METRIC, known),
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
                    known,
                )
            assertEquals(listOf(DiffLabel.Notes), changes.map { it.label })
        }
    }

    @Nested
    inner class Equipment {
        private fun needing(vararg items: String) = reps().copy(equipment = items.toList())

        private fun removed(vararg items: String) =
            FieldChange(DiffLabel.EquipmentRemoved, DiffValue.Items(items.toList()), NO_VALUE, cleared = items.size)

        private fun added(vararg items: String) =
            FieldChange(DiffLabel.EquipmentAdded, NO_VALUE, DiffValue.Items(items.toList()))

        // As the workout's exercises are: an item still listed is never shown, or spoken, as taken away.
        @Test
        fun `lists a swap as the item that goes and the item that comes, each as its file spells it`() {
            assertEquals(
                listOf(removed("bench"), added("Rack")),
                diffExercise(needing("Barbell", "bench"), needing("Barbell", "Rack"), UnitSystem.METRIC, known),
            )
        }

        // Import's warning and counts are cleared and nothing else (#456): every item that goes counts.
        @Test
        fun `counts each item a removal takes away`() {
            assertEquals(
                listOf(removed("Barbell", "bench", "my old belt")),
                diffExercise(needing("Barbell", "bench", "my old belt"), reps(), UnitSystem.METRIC, known),
            )
        }

        @Test
        fun `reads equipment gained as nothing taken away`() {
            val changes = diffExercise(reps(), needing("Dumbbell"), UnitSystem.METRIC, known)
            assertEquals(listOf(added("Dumbbell")), changes)
            assertEquals(0, changes.sumOf { it.cleared })
        }

        // The same known item, matched as known items are: a row for it would warn about nothing.
        @Test
        fun `says nothing about a known item's change of spelling`() {
            assertEquals(
                emptyList<FieldChange>(),
                diffExercise(
                    needing("Dumbbell", "Pull-up bar"),
                    needing("dumbbell", "pull_up_bar"),
                    UnitSystem.METRIC,
                    known,
                ),
            )
        }

        // The user's own words are shown as written, so a change to how they're written is a change.
        @Test
        fun `lists a change of case in the user's own words`() {
            assertEquals(
                listOf(removed("fat grips"), added("Fat Grips")),
                diffExercise(needing("fat grips"), needing("Fat Grips"), UnitSystem.METRIC, known),
            )
        }

        // Only what is known folds: with no vocabulary, `Dumbbell` is the user's own words too.
        @Test
        fun `folds only the items the vocabulary knows`() {
            assertEquals(
                listOf(removed("Dumbbell"), added("dumbbell")),
                diffExercise(needing("Dumbbell"), needing("dumbbell"), UnitSystem.METRIC, emptySet()),
            )
        }

        // Accepted: an exercise's items are what it needs at hand, not a sequence.
        @Test
        fun `says nothing about a change of order alone`() {
            assertEquals(
                emptyList<FieldChange>(),
                diffExercise(
                    needing("Barbell", "fat grips"),
                    needing("fat grips", "Barbell"),
                    UnitSystem.METRIC,
                    known,
                ),
            )
        }

        @Test
        fun `lists equipment after the notes and before the config`() {
            val after = needing("Bench").copy(notes = "Pause", config = base.copy(sets = 6))
            assertEquals(
                listOf(DiffLabel.Notes, DiffLabel.EquipmentAdded, DiffLabel.Field(FieldName.Sets)),
                diffExercise(reps(), after, UnitSystem.METRIC, known).map { it.label },
            )
        }

        // A type change replaces the config's rows, never the equipment's.
        @Test
        fun `still lists an equipment change beside a type change`() {
            val hold =
                Exercise("bench", "Bench Press", ExerciseConfig.TimedHold(5, holdSecMin = 20.0, restSec = 120.0))
                    .copy(equipment = listOf("Rack"))
            val changes = diffExercise(needing("Barbell"), hold, UnitSystem.METRIC, known)
            assertEquals(
                listOf(removed("Barbell"), added("Rack")),
                changes.filter {
                    it.label != DiffLabel.Type &&
                        it.label !is DiffLabel.Field
                },
            )
            assertEquals(DiffLabel.Type, changes.first().label)
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
                    FieldChange(DiffLabel.Exercises, DiffValue.Text("dips"), DiffValue.Text("—"), cleared = 1),
                ),
                diffWorkout(before, after),
            )
        }

        // One circuit either way, so a count-only diff would call this no change at all.
        @Test
        fun `sees inside a circuit, so a swapped member is not invisible`() {
            assertEquals(
                listOf(
                    FieldChange(DiffLabel.Exercises, DiffValue.Text("dips"), DiffValue.Text("—"), cleared = 1),
                    FieldChange(DiffLabel.Exercises, DiffValue.Text("—"), DiffValue.Text("rows")),
                ),
                diffWorkout(push(circuit("bench", "dips")), push(circuit("bench", "rows"))),
            )
        }

        // The workout editor's rest (#440) lives in the block: an update dropping it read as identical (#443).
        @Test
        fun `names a block whose settings changed, the same exercises in it`() {
            val rest = WorkoutBlock.ExerciseBlock("rest", BlockConfigOverride(durationSec = 120.0))
            val before = push(WorkoutBlock.ExerciseBlock("bench"), rest, circuit("bench", "dips"))
            val after =
                push(
                    WorkoutBlock.ExerciseBlock("bench"),
                    WorkoutBlock.ExerciseBlock("rest"),
                    circuit("bench", "dips").copy(rounds = 5),
                )

            assertEquals(
                listOf(FieldChange(DiffLabel.BlocksEdited, DiffValue.Text("—"), DiffValue.Text("2, 3"))),
                diffWorkout(before, after),
            )
        }

        // Same blocks, new order: no count or reference moved, and it is still not the same workout.
        @Test
        fun `names the blocks a reorder moved, rather than calling it identical`() {
            val bench = WorkoutBlock.ExerciseBlock("bench")
            val dips = WorkoutBlock.ExerciseBlock("dips")

            assertEquals(
                listOf(FieldChange(DiffLabel.BlocksEdited, DiffValue.Text("—"), DiffValue.Text("1, 2"))),
                diffWorkout(push(bench, dips), push(dips, bench)),
            )
        }

        @Test
        fun `reports nothing for a workout that is the same`() {
            val same = push(WorkoutBlock.ExerciseBlock("bench"), circuit("bench", "dips"))
            assertEquals(emptyList<FieldChange>(), diffWorkout(same, same.copy()))
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
        fun `names an added week in its own row, not as edited too`() {
            assertEquals(
                listOf(
                    FieldChange(DiffLabel.Weeks, DiffValue.Text("1"), DiffValue.Text("2")),
                    FieldChange(DiffLabel.WeeksAdded, DiffValue.Text("—"), DiffValue.Text("2")),
                ),
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
                listOf(
                    FieldChange(DiffLabel.WeeksEdited, DiffValue.Text("—"), DiffValue.Text("2")),
                    FieldChange(DiffLabel.WeeksLosing, DiffValue.Text("—"), DiffValue.Text("2"), cleared = 1),
                ),
                diffProgram(base(week(1, "push"), week(2, "push")), base(week(1, "push"), ProgramWeek.RestDay(2))),
            )
        }
    }
}
