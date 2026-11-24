package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every session the runner writes reads back as itself. The shell puts the written session straight
 * into the log it shows (#324) instead of reading the file again, which is only right while the two
 * agree.
 */
class SessionRoundTripTest {
    private fun assertEveryWriteRoundTrips(runner: Runner) {
        val written = runner.effects.filterIsInstance<RunnerEffect.WriteSession>().map { it.session }
        assertTrue(written.size > 1, "a session that wrote more than its start")
        for (session in written) {
            assertEquals(ParseResult.Ok(session), parseSessionYaml(serializeSessionYaml(session)))
        }
    }

    /** Every step to the end, with some time on each, and a Finish should any step not end by itself. */
    private fun Runner.runThrough() {
        repeat(snapshot.steps.size) {
            if (state.finished) return
            if (step?.exerciseId == "grinder") send(RunnerCommand.SetRounds(4))
            tick(7)
            advance()
        }
        if (!state.finished) finish()
    }

    @Test
    fun `every type of exercise, run to the end`() {
        val ids = RUNNER_EXERCISES.map { it.id }
        val runner = Runner(Workout("w", "W", ids.map(::single)))
        runner.runThrough()

        assertEveryWriteRoundTrips(runner)
    }

    @Test
    fun `a circuit, with a weight, reps and effort entered by hand`() {
        val runner = Runner(workoutOf(circuitOf("pullups", "lsit", between = 15.0, betweenRounds = 60.0)))
        runner.send(RunnerCommand.SetReps(7))
        runner.send(RunnerCommand.SetWeight(22.5))
        runner.send(RunnerCommand.SetRpe(8.0))
        runner.runThrough()

        assertEveryWriteRoundTrips(runner)
    }

    @Test
    fun `a session finished early`() {
        val runner = Runner(workoutOf(single("pullups"), single("plank")))
        runner.advance()
        runner.tick(20)
        runner.finish()

        assertEveryWriteRoundTrips(runner)
    }
}
