package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * "Last time" on the set row and the live personal-best marker, from RN `use-session-runner.test.tsx`'s
 * last-time, personal-best and adopting-load sections. Prior sessions are a parameter here, not
 * something the engine holds.
 */
class RunnerRecordsTest {
    private fun repsSets(vararg sets: Pair<Long, Double?>) =
        SessionEntry.Reps("pullups", sets.map { RepsSetLog(it.first, it.second, restTakenSec = 90.0) })

    private fun benchLog(weightKg: Double) = listOf(finishedSession("older", repsSets(6L to weightKg)))

    @Nested
    inner class LastTime {
        @Test
        fun `shows nothing on a first-ever session`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertNull(previousSet(runner.snapshot, emptyList()))
        }

        @Test
        fun `shows the matching set from the last time the exercise was trained`() {
            val prior = listOf(finishedSession("older", repsSets(8L to 15.0, 6L to 15.0)))
            val runner = Runner(workoutOf(single("pullups")))

            assertEquals(PreviousSet.Reps(8, 15.0), previousSet(runner.snapshot, prior))
        }

        @Test
        fun `does not change when a set is logged mid-session`() {
            val prior = listOf(finishedSession("older", repsSets(8L to 15.0)))
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(40.0))
            runner.advance()
            runner.advance() // past the trailing rest, onto set 2

            assertTrue(runner.step is RunnerStep.Reps)
            assertEquals(PreviousSet.Reps(8, 15.0), previousSet(runner.snapshot, prior))
        }

        @Test
        fun `skips the in-flight session even when it is passed in with the others`() {
            val prior = listOf(finishedSession("older", repsSets(8L to 15.0)))
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(40.0))
            runner.advance()
            runner.advance()

            assertEquals(40.0, (runner.entries.single() as SessionEntry.Reps).sets.single().weightKg)
            assertNull(runner.state.session.endedAt)
            assertEquals(PreviousSet.Reps(8, 15.0), previousSet(runner.snapshot, listOf(runner.state.session) + prior))
        }

        @Test
        fun `carries a hold set to the hold screen`() {
            val prior =
                listOf(finishedSession("older", SessionEntry.TimedHold("lsit", listOf(TimedHoldSetLog(22.0, 60.0)))))
            val runner = Runner(workoutOf(single("lsit")))

            assertEquals(PreviousSet.Hold(22.0), previousSet(runner.snapshot, prior))
        }

        @Test
        fun `falls back to last time's final set for a set number that did not exist then`() {
            val prior = listOf(finishedSession("older", repsSets(12L to null, 10L to null)))
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.AddSet)
            repeat(4) { runner.advance() }

            // Set 3 of 4: a set number the previous entry, which had two, never reached.
            assertEquals(3L, (runner.step as WorkStep).setIndex)
            assertEquals(4L, (runner.step as WorkStep).setTotal)
            assertEquals(PreviousSet.Reps(10, null), previousSet(runner.snapshot, prior))
        }
    }

    @Nested
    inner class PersonalBest {
        @Test
        fun `stays quiet with nothing to beat`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(200.0))

            assertFalse(beatsPersonalBest(runner.state, emptyList(), runner.now))
        }

        @Test
        fun `stays quiet on a tie`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(20.0))

            assertFalse(beatsPersonalBest(runner.state, benchLog(20.0), runner.now))
        }

        @Test
        fun `marks a load above anything ever logged`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(22.5))

            assertTrue(beatsPersonalBest(runner.state, benchLog(20.0), runner.now))
        }

        @Test
        fun `does not re-mark the following set at the same new weight`() {
            val prior = benchLog(20.0)
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(25.0))
            assertTrue(beatsPersonalBest(runner.state, prior, runner.now))

            runner.advance()
            runner.advance()

            // Load carries across sets, so set 2 is at 25 kg too: matched, not beaten.
            assertEquals(25.0, runner.inputs.weightKg)
            assertFalse(beatsPersonalBest(runner.state, prior, runner.now))
        }

        @Test
        fun `marks again once the load goes up mid-session`() {
            val prior = benchLog(20.0)
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(25.0))
            runner.advance()
            runner.advance()
            runner.send(RunnerCommand.SetWeight(27.5))

            assertTrue(beatsPersonalBest(runner.state, prior, runner.now))
        }

        @Test
        fun `marks a hold only once it passes the longest ever, not on a tie`() {
            // deadhang is max-effort: lsit's 15 s target is also its end, so it would auto-end before 21 s.
            val prior =
                listOf(
                    finishedSession("older", SessionEntry.TimedHold("deadhang", listOf(TimedHoldSetLog(20.0, 30.0)))),
                )
            val runner = Runner(workoutOf(single("deadhang")))

            runner.tick(20)
            assertFalse(beatsPersonalBest(runner.state, prior, runner.now))

            runner.tick(1)
            assertTrue(beatsPersonalBest(runner.state, prior, runner.now))
        }

        @Test
        fun `personalBestFor skips unfinished sessions`() {
            val unfinished = finishedSession("inflight", repsSets(6L to 100.0)).copy(endedAt = null)
            val finished = finishedSession("older", repsSets(6L to 20.0))

            assertEquals(20.0, personalBestFor(listOf(unfinished, finished), "pullups").heaviestSetKg)
            assertNull(personalBestFor(listOf(unfinished), "pullups").heaviestSetKg)
        }
    }

    @Nested
    inner class AdoptingLastTimesLoad {
        @Test
        fun `takes the load from last time as the weight input`() {
            val prior = listOf(finishedSession("older", repsSets(6L to 32.5)))
            val runner = Runner(workoutOf(single("pullups")))

            val load = (previousSet(runner.snapshot, prior) as PreviousSet.Reps).weightKg
            assertEquals(32.5, load)
            runner.send(RunnerCommand.SetWeight(load!!))

            assertEquals(32.5, runner.inputs.weightKg)
        }

        @Test
        fun `has no load to adopt when last time was bodyweight`() {
            val prior =
                listOf(finishedSession("older", SessionEntry.Reps("dips", listOf(RepsSetLog(10, restTakenSec = 0.0)))))
            val runner = Runner(workoutOf(single("dips")))

            assertNull((previousSet(runner.snapshot, prior) as PreviousSet.Reps).weightKg)
            assertEquals(0.0, runner.inputs.weightKg)
        }
    }
}
