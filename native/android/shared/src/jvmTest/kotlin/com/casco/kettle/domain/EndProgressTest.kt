package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** End early's per-exercise progress (#417): each exercise in its own unit, counted behind the step on screen. */
class EndProgressTest {
    private fun Runner.progress() = endProgress(snapshot).map { "${it.name} ${it.done}/${it.total} ${it.unit}" }

    @Test
    fun `counts the steps behind the one on screen, in each kind's unit, and leaves rests out`() {
        val runner = Runner(workoutOf(single("pullups"), single("burpees"), single("swings"), single("grinder")))
        runner.advance() // set 1 logged; the rest after it is on screen
        assertEquals(
            listOf(
                "Pull-ups 1/3 SETS",
                "Burpees 0/3 ROUNDS",
                "KB Swings 0/3 MINUTES",
                "Grinder 0/1 ONCE",
            ),
            runner.progress(),
        )
    }

    @Test
    fun `an EMOM whose intervals aren't minutes counts intervals`() {
        val short = Exercise("short", "Short", ExerciseConfig.Emom(intervalSec = 40.0, totalMinutes = 2.0))
        val runner = Runner(workoutOf(single("short")), RUNNER_EXERCISES + short)
        assertEquals(EndUnit.INTERVALS, endProgress(runner.snapshot).single().unit)
    }

    @Test
    fun `a circuit counts rounds, an AMRAP in one too, and a lone cardio is one go`() {
        val runner = Runner(workoutOf(circuitOf("pullups", "grinder", rounds = 3), single("row")))
        assertEquals(listOf("Pull-ups 0/3 ROUNDS", "Grinder 0/3 ROUNDS", "Row 0/1 ONCE"), runner.progress())
    }

    @Test
    fun `a swap makes the exercise swapped in a row of its own, and an added set grows the total`() {
        val runner = Runner(workoutOf(single("pullups")))
        runner.send(RunnerCommand.AddSet)
        runner.advance()
        runner.advance() // past set 1 and its rest, onto set 2
        runner.send(RunnerCommand.Swap(runner.exercise("dips")))
        assertEquals(listOf("Pull-ups 1/1 SETS", "Dips 0/3 SETS"), runner.progress())
    }
}
