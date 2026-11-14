package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The exercise editor's steppers and its fixed-count save (#441). */
class ExerciseStepsTest {
    private val fields = configFields(ExerciseType.Reps).associateBy { it.key }
    private val sets = fields.getValue(ConfigKey.Sets)
    private val min = fields.getValue(ConfigKey.TargetRepsMin)
    private val max = fields.getValue(ConfigKey.TargetRepsMax)

    private fun reps(vararg values: Pair<ConfigKey, String>) =
        ExerciseDraft("Squat", ExerciseType.Reps, values.toMap(), "")

    @Test
    fun `steps a count within its field's bounds`() {
        assertEquals("4", reps(ConfigKey.Sets to "3").stepping(sets, +1).values[ConfigKey.Sets])
        assertEquals(false, reps(ConfigKey.Sets to "1").canStep(sets, -1))
        assertEquals(false, reps(ConfigKey.Sets to "$MAX_SETS").canStep(sets, +1))
    }

    // A new exercise's blank count starts from its least, not from 0, which the schema refuses.
    @Test
    fun `starts a blank count from the field's least`() {
        assertEquals("2", reps().stepping(sets, +1).values[ConfigKey.Sets])
    }

    // No top is a fixed count: the max stepper starts where the min is.
    @Test
    fun `shows a blank reps max as the min`() {
        assertEquals(8L, reps(ConfigKey.TargetRepsMin to "8").shownCount(max))
        assertEquals("9", reps(ConfigKey.TargetRepsMin to "8").stepping(max, +1).values[ConfigKey.TargetRepsMax])
    }

    @Test
    fun `keeps the reps max at or above the min`() {
        assertEquals(false, reps(ConfigKey.TargetRepsMin to "8", ConfigKey.TargetRepsMax to "8").canStep(max, -1))
    }

    // "8 to 8" stepped up is "9 to 9", not a range the form refuses.
    @Test
    fun `carries the max up with a min stepped past it`() {
        val stepped = reps(ConfigKey.TargetRepsMin to "8", ConfigKey.TargetRepsMax to "8").stepping(min, +1)
        assertEquals("9", stepped.values[ConfigKey.TargetRepsMin])
        assertEquals("9", stepped.values[ConfigKey.TargetRepsMax])
    }

    // A stepper showing "1" for a blank Sets would otherwise be refused as required on Save.
    @Test
    fun `fills blank required counts and times, and leaves optional ones blank`() {
        val filled = reps().withRequiredFilled().values
        assertEquals("1", filled[ConfigKey.Sets])
        assertEquals("1", filled[ConfigKey.TargetRepsMin])
        assertEquals("0", filled[ConfigKey.RestSec])
        assertEquals(null, filled[ConfigKey.TargetRepsMax])
        assertEquals(null, filled[ConfigKey.TargetWeightKg])
    }

    @Test
    fun `keeps what is already set`() {
        assertEquals("4", reps(ConfigKey.Sets to "4").withRequiredFilled().values[ConfigKey.Sets])
    }

    // The ticket's: the same number twice saves as a fixed count, so the file never says 8–8.
    @Test
    fun `saves the same reps twice as a fixed count`() {
        val draft =
            reps(
                ConfigKey.Sets to "3",
                ConfigKey.TargetRepsMin to "8",
                ConfigKey.TargetRepsMax to "8",
                ConfigKey.RestSec to "90",
            )

        assertEquals(
            null,
            (buildExercise("squat", draft, UnitSystem.METRIC).config as ExerciseConfig.Reps).targetRepsMax,
        )
    }

    @Test
    fun `keeps a real range`() {
        val draft =
            reps(
                ConfigKey.Sets to "3",
                ConfigKey.TargetRepsMin to "8",
                ConfigKey.TargetRepsMax to "12",
                ConfigKey.RestSec to "90",
            )

        assertEquals(
            12L,
            (buildExercise("squat", draft, UnitSystem.METRIC).config as ExerciseConfig.Reps).targetRepsMax,
        )
    }
}
