package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [overrideNote], the display half of the override merge, which RN's `overrideLines` held inside
 * `program-detail.tsx` until Program detail went native (#233). Its jest cases were the screen's; the
 * targeting and per-key ones are here as the selector's, and the words are `ProgramDetailScreenTest`'s.
 */
class OverrideNotesTest {
    private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, restSec = 90.0))
    private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(3, 10, restSec = 90.0))

    // Two members, since the schema refuses a circuit of one, which would refuse every block patch.
    private val finisher =
        WorkoutBlock.Circuit("finisher", 3, members = listOf(CircuitMember("pull-ups"), CircuitMember("pull-ups")))
    private val workout = Workout("w", "W", listOf(WorkoutBlock.ExerciseBlock("pull-ups"), finisher))
    private val library = Library(1, listOf(pullUps, dips), listOf(workout), emptyList())

    private fun exercise(
        id: String,
        vararg config: Pair<String, OverrideValue>,
    ) = ProgramOverride.ExerciseOverride(id, config.toMap())

    private fun block(
        id: String,
        vararg config: Pair<String, OverrideValue>,
    ) = ProgramOverride.BlockOverride(id, config.toMap())

    private fun num(value: Double) = OverrideValue.Numeric(value)

    @Test
    fun `names an exercise the week runs, every key applied`() {
        val note = overrideNote(exercise("pull-ups", "sets" to num(5.0)), library, workout)

        assertEquals(OverrideTarget.Exercise("Pull-ups"), note.target)
        assertEquals(listOf(OverrideKey("sets", num(5.0), unknown = false)), note.keys)
        assertFalse(note.refused)
    }

    // Resolved against the whole library, an exercise the week doesn't run merged cleanly, changed
    // nothing, and showed as applied. The block branch has always been scoped to the workout.
    @Test
    fun `an exercise in the library but not in the week is missing, by its id`() {
        val note = overrideNote(exercise("dips", "sets" to num(5.0)), library, workout)

        assertEquals(OverrideTarget.Missing("dips"), note.target)
    }

    @Test
    fun `names a circuit the week runs by its id`() {
        val note = overrideNote(block("finisher", "rounds" to num(2.0)), library, workout)

        assertEquals(OverrideTarget.Circuit("finisher"), note.target)
        assertEquals(listOf(OverrideKey("rounds", num(2.0), unknown = false)), note.keys)
    }

    @Test
    fun `a circuit the week has no such id for is missing`() {
        assertEquals(OverrideTarget.Missing("warmup"), overrideNote(block("warmup"), library, workout).target)
    }

    @Test
    fun `everything is missing when the week's workout is gone`() {
        assertEquals(
            OverrideTarget.Missing("pull-ups"),
            overrideNote(exercise("pull-ups", "sets" to num(5.0)), library, null).target,
        )
        assertEquals(OverrideTarget.Missing("finisher"), overrideNote(block("finisher"), library, null).target)
    }

    // An unknown key is stripped on its own while the rest of the patch runs, so marking every key
    // "not applied" said a change wasn't happening while it happened.
    @Test
    fun `marks only the key that does nothing`() {
        val note = overrideNote(block("finisher", "rounds" to num(2.0), "exercisez" to num(2.0)), library, workout)

        assertEquals(
            listOf(
                OverrideKey("rounds", num(2.0), unknown = false),
                OverrideKey("exercisez", num(2.0), unknown = true),
            ),
            note.keys,
        )
        assertFalse(note.refused)
    }

    @Test
    fun `a patch the schema refuses is refused as a whole`() {
        val note = overrideNote(exercise("pull-ups", "sets" to num(-1.0)), library, workout)

        assertTrue(note.refused)
        assertEquals(listOf(OverrideKey("sets", num(-1.0), unknown = false)), note.keys)
    }

    // RN returns before its per-key notes on a refusal: nothing in the patch applies, so singling out
    // the unknown key would say the rest did.
    @Test
    fun `a refused patch marks no key unknown, even one that is`() {
        val note = overrideNote(exercise("pull-ups", "sets" to num(-1.0), "setz" to num(2.0)), library, workout)

        assertTrue(note.refused)
        assertEquals(listOf(false, false), note.keys.map { it.unknown })
    }

    @Test
    fun `keeps the patch's own key order`() {
        val note =
            overrideNote(exercise("pull-ups", "rest_sec" to num(60.0), "sets" to num(4.0)), library, workout)

        assertEquals(listOf("rest_sec", "sets"), note.keys.map { it.key })
    }

    @Test
    fun `a workout runs an exercise as a block or as a circuit member`() {
        val onlyInCircuit = Workout("c", "C", listOf(finisher))

        assertTrue(workoutRunsExercise(workout, "pull-ups"))
        assertTrue(workoutRunsExercise(onlyInCircuit, "pull-ups"))
        assertFalse(workoutRunsExercise(workout, "dips"))
        assertFalse(workoutRunsExercise(null, "pull-ups"))
    }
}
