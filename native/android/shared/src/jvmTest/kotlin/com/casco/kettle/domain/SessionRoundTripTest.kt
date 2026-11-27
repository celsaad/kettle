package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every session native writes reads back as itself: the runner's, the session editor's and the launch
 * prompt's Finish. The shell puts the written session straight
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
            if (step?.exerciseId == "grinder") rounds(4)
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
        runner.reps(7)
        runner.weight(22.5)
        runner.rpe(8.0)
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

    /** A finished session as the runner left it, a set of pull-ups and a plank. */
    private fun finished(): Session {
        val runner = Runner(workoutOf(single("pullups"), single("plank")))
        runner.advance()
        runner.tick(20)
        runner.finish()
        return runner.written
    }

    private fun assertRoundTrips(session: Session) =
        assertEquals(ParseResult.Ok(session), parseSessionYaml(serializeSessionYaml(session)))

    /** Pounds typed in are stored as kilograms, the double most likely to come back different. */
    @Test
    fun `a session edited in pounds, with an effort typed in`() {
        val session = finished()
        val editing = sessionEditing(listOf(session), session.id, UnitSystem.IMPERIAL) as SessionEditing.Editable
        val drafts =
            editing.drafts.map { draft ->
                draft.copy(
                    forms =
                        draft.forms.map { form ->
                            if (EntryField.Load !in form.values) {
                                form
                            } else {
                                form.copy(
                                    values =
                                        form.values + mapOf(EntryField.Load to "137.3", EntryField.Rpe to "7.5"),
                                )
                            }
                        },
                )
            }

        val edited = editedSession(session, drafts, UnitSystem.IMPERIAL)

        assertNotEquals(session, edited)
        assertRoundTrips(edited)
    }

    @Test
    fun `a session the launch prompt finishes`() {
        val running = finished().copy(endedAt = null)

        assertRoundTrips(RunnerRecovery.FinishOnly(running, T0 + 12_345).finished)
    }
}
