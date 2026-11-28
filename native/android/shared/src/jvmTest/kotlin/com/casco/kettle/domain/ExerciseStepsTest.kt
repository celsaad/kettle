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

    // A count typed in (#463) is kept as typed, for Save's validation to judge.
    @Test
    fun `keeps a typed count as typed`() {
        assertEquals("120", reps(ConfigKey.Sets to "3").typing(sets, "120").values[ConfigKey.Sets])
        assertEquals("", reps(ConfigKey.Sets to "3").typing(sets, "").values[ConfigKey.Sets])
    }

    // "8 to 12" with 50 typed as the min is "50 to 50", as a stepped min carries it.
    @Test
    fun `carries the max up with a min typed past it, and leaves it below`() {
        val typed = reps(ConfigKey.TargetRepsMin to "8", ConfigKey.TargetRepsMax to "12").typing(min, "50")
        assertEquals("50", typed.values[ConfigKey.TargetRepsMax])
        val lower = reps(ConfigKey.TargetRepsMin to "8", ConfigKey.TargetRepsMax to "12").typing(min, "10")
        assertEquals("12", lower.values[ConfigKey.TargetRepsMax])
    }

    // A name-only Save writes something a person would train (#456), not 1 × 1 with no rest.
    @Test
    fun `starts a new reps exercise at 3 x 8-12 with a 1-30 rest, and no weight`() {
        val filled = reps().withDefaults().values
        assertEquals("3", filled[ConfigKey.Sets])
        assertEquals("8", filled[ConfigKey.TargetRepsMin])
        assertEquals("12", filled[ConfigKey.TargetRepsMax])
        assertEquals("90", filled[ConfigKey.RestSec])
        assertEquals(null, filled[ConfigKey.TargetWeightKg])
    }

    @Test
    fun `starts a new HIIT at 0-30 on and off for 8 rounds`() {
        val hiit = ExerciseDraft("Sprints", ExerciseType.Hiit, emptyMap(), "").withDefaults().values
        assertEquals(
            listOf("30", "30", "8"),
            listOf(hiit[ConfigKey.WorkSec], hiit[ConfigKey.RestSec], hiit[ConfigKey.Rounds]),
        )
    }

    @Test
    fun `starts each timed type at its own target`() {
        fun start(type: ExerciseType) = ExerciseDraft("x", type, emptyMap(), "").withDefaults().values
        val hold = start(ExerciseType.TimedHold)
        val emom = start(ExerciseType.Emom)

        assertEquals(
            listOf("3", "30", "60"),
            listOf(hold[ConfigKey.Sets], hold[ConfigKey.HoldSecMin], hold[ConfigKey.RestSec]),
        )
        assertEquals(listOf("60", "10"), listOf(emom[ConfigKey.IntervalSec], emom[ConfigKey.TotalMinutes]))
        assertEquals("600", start(ExerciseType.Amrap)[ConfigKey.TimeCapSec])
        assertEquals("60", start(ExerciseType.Rest)[ConfigKey.DurationSec])
    }

    // A cardio with no duration counts up, which a default would take away.
    @Test
    fun `leaves an optional field with no default blank`() {
        assertEquals(
            null,
            ExerciseDraft("Run", ExerciseType.Cardio, emptyMap(), "").withDefaults().values[ConfigKey.DurationSec],
        )
    }

    // The reps 1:30 rest was never the user's, so HIIT gets its own 0:30.
    @Test
    fun `takes the new type's default where the old one's was untouched`() {
        val switched = reps().withDefaults().switchingTo(ExerciseType.Hiit).values

        assertEquals("30", switched[ConfigKey.RestSec])
    }

    @Test
    fun `carries what two types share across a switch`() {
        assertEquals("4", reps(ConfigKey.Sets to "4").switchingTo(ExerciseType.TimedHold).values[ConfigKey.Sets])
    }

    // A Rest of 0:00, and Cardio's same key needs a second: carried, Save would refuse it.
    @Test
    fun `drops a carried value the new type refuses`() {
        val rest = ExerciseDraft("Breather", ExerciseType.Rest, mapOf(ConfigKey.DurationSec to "0"), "")

        assertEquals(null, rest.switchingTo(ExerciseType.Cardio).values[ConfigKey.DurationSec])
    }

    // Cardio's typed "abc" would sit under Rest's time button as 0:00, refused by Save unseen.
    @Test
    fun `drops carried text that is not a number`() {
        val cardio = ExerciseDraft("Run", ExerciseType.Cardio, mapOf(ConfigKey.DurationSec to "abc"), "")

        assertEquals("60", cardio.switchingTo(ExerciseType.Rest).values[ConfigKey.DurationSec])
    }

    @Test
    fun `keeps what is already set`() {
        assertEquals("4", reps(ConfigKey.Sets to "4").withDefaults().values[ConfigKey.Sets])
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
