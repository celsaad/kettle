package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The engine's write path and its one-level undo, from RN `use-session-runner.test.tsx`'s undo,
 * write-through, logging, circuits, zero-work and finish sections (§2.1 and §2.5 of #294's inventory).
 * Undo first, as #304 asks: it is the most intricate state machine in the runner.
 */
class RunnerWritesTest {
    private fun Runner.pullupSets() = entry<SessionEntry.Reps>("pullups")?.sets.orEmpty()

    @Nested
    inner class Undo {
        @Test
        fun `retracts the entry a first set had just created`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            assertEquals(1, runner.entries.size)

            runner.prev()
            assertEquals(0, runner.entries.size)
            assertEquals(0, runner.index)

            // Redo, then finish the exercise: the popped set must not resurface as a duplicate.
            repeat(5) { runner.advance() }
            assertEquals(3, runner.pullupSets().size)
        }

        @Test
        fun `shrinks a written entry back by one set rather than retracting the whole thing`() {
            val runner = Runner(workoutOf(single("pullups"), single("grinder")))
            repeat(5) { runner.advance() }
            assertEquals(3, runner.pullupSets().size)

            runner.prev()
            assertEquals(1, runner.entries.filterIsInstance<SessionEntry.Reps>().size)
            assertEquals(2, runner.pullupSets().size)

            runner.advance()
            assertEquals(1, runner.entries.filterIsInstance<SessionEntry.Reps>().size)
            assertEquals(3, runner.pullupSets().size)
        }

        @Test
        fun `undoes only one level, so a second Prev just moves the index`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.advance() // the rest, which only overwrote a field
            runner.prev()
            runner.prev()
            runner.prev()

            assertEquals(0, runner.index)
            assertEquals(1, runner.pullupSets().size)
        }

        @Test
        fun `does not go below the first step`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.prev()
            assertEquals(0, runner.index)
        }

        @Test
        fun `takes back a hiit round, an emom minute and a one-shot entry`() {
            val hiit = Runner(workoutOf(single("burpees")))
            hiit.advance()
            hiit.advance()
            hiit.advance()
            hiit.prev()
            assertEquals(1L, hiit.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)

            val emom = Runner(workoutOf(single("swings")))
            emom.advance()
            emom.advance()
            emom.prev()
            assertEquals(1, emom.entry<SessionEntry.Emom>("swings")?.minutes?.size)

            val amrap = Runner(workoutOf(single("grinder"), single("pullups")))
            amrap.send(RunnerCommand.SetRounds(4))
            amrap.advance()
            assertEquals(1, amrap.entries.size)
            amrap.prev()
            assertEquals(0, amrap.entries.size)
        }

        @Test
        fun `retracts a member's entry entirely when its only hiit round is undone, and redoes into a fresh one`() {
            val runner = Runner(workoutOf(single("pullups"), single("burpees")))
            repeat(5) { runner.advance() }
            runner.advance() // burpees round 1
            runner.prev()
            assertEquals(listOf("pullups"), runner.entries.map { it.exercise })

            runner.advance()
            assertEquals(listOf("pullups", "burpees"), runner.entries.map { it.exercise })
            assertEquals(1L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)
        }

        /** RN `runner.test:1336`: every step-list edit must clear the undo record. */
        @Test
        fun `does not retract a logged set when Prev follows an added set`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(9))
            runner.advance()
            runner.send(RunnerCommand.AddSet)
            runner.prev()

            assertEquals(listOf(9L), runner.pullupSets().map { it.reps })
        }

        /** RN `runner.test:1572`. */
        @Test
        fun `does not retract a logged set when Prev follows a swap`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(9))
            runner.advance()
            runner.send(RunnerCommand.Swap(runner.exercise("dips")))
            runner.prev()

            assertEquals(1, runner.entries.size)
            assertEquals(listOf(9L), runner.pullupSets().map { it.reps })
        }

        @Test
        fun `does not retract the set before an ad-hoc add when Prev follows it`() {
            val runner = Runner(null)
            runner.send(RunnerCommand.AddExercise(runner.exercise("dips")))
            runner.advance()
            runner.send(RunnerCommand.AddExercise(runner.exercise("pullups")))
            runner.prev()

            assertEquals(1, runner.entry<SessionEntry.Reps>("dips")?.sets?.size)
        }
    }

    @Nested
    inner class WriteThrough {
        @Test
        fun `creates the session file as it starts, with nothing in it`() {
            val runner = Runner(workoutOf(single("pullups")))

            assertEquals("2026-07-27T09:00:00.000Z", runner.written.startedAt)
            assertEquals("2026-07-27T09-00-00-000Z", runner.written.id)
            assertEquals("w", runner.written.workout)
            assertNull(runner.written.endedAt)
            assertTrue(runner.entries.isEmpty())
        }

        @Test
        fun `records the program week a session was started from`() {
            val workout = workoutOf(single("pullups"))
            val plan = RunnerPlan.Runnable(workout, RUNNER_EXERCISES, buildSteps(workout, RUNNER_EXERCISES))
            val start = RunnerStart.Planned(SessionParams.ProgramSlot("p", 2, "Day 1"))
            val session = startRunner(plan, start, T0).state.session

            assertEquals(Triple("p", 2L, "Day 1"), Triple(session.program, session.programWeek, session.programDay))
        }

        @Test
        fun `writes the first set before the exercise is anywhere near finished`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(7))
            runner.advance()

            assertEquals(listOf(7L), runner.pullupSets().map { it.reps })
        }

        @Test
        fun `grows that same entry set by set instead of appending one per set`() {
            val runner = Runner(workoutOf(single("pullups")))
            repeat(3) { runner.advance() }

            assertEquals(1, runner.entries.size)
            assertEquals(2, runner.pullupSets().size)
        }

        @Test
        fun `records the rest taken after a set that is already on disk`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.tick(45)
            runner.advance()

            assertEquals(45.0, runner.pullupSets()[0].restTakenSec)
        }

        @Test
        fun `does not clamp rest to its length`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.away(300) // a 90 s rest, noticed after five minutes

            assertEquals(2, runner.index)
            assertEquals(300.0, runner.pullupSets()[0].restTakenSec)
        }

        @Test
        fun `writes a hiit round as it completes, and counts up in place`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.advance()
            assertEquals(1L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)

            runner.advance()
            runner.advance()
            assertEquals(2L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)
        }

        @Test
        fun `keeps each circuit member in its own entry while both are still going`() {
            val runner = Runner(workoutOf(circuitOf("pullups", "lsit", between = 15.0, betweenRounds = 60.0)))
            runner.advance()
            runner.advance()
            runner.tick(5) // a hold ended at 0 s is a skip, and is never written
            runner.advance()

            assertEquals(listOf("pullups", "lsit"), runner.entries.map { it.exercise })

            runner.advance()
            runner.advance()
            assertEquals(2, runner.entries.size)
            assertEquals(2, runner.pullupSets().size)
            assertEquals(1, runner.entry<SessionEntry.TimedHold>("lsit")?.sets?.size)
        }

        @Test
        fun `asks for every write as it happens, and saves the snapshot on every transition`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.clearEffects()
            runner.advance()

            assertEquals(1, runner.effects.count { it is RunnerEffect.WriteSession } - 1)
            assertTrue(RunnerEffect.SaveSnapshot(debounce = false) in runner.effects)
        }

        @Test
        fun `debounces the snapshot save of an input edit`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.clearEffects()
            runner.send(RunnerCommand.SetReps(4))

            assertEquals(listOf(RunnerEffect.SaveSnapshot(debounce = true)), runner.effects.drop(1))
        }

        @Test
        fun `saves nothing for an edit that changes nothing`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.clearEffects()
            runner.send(RunnerCommand.SetReps(6))

            assertEquals(1, runner.effects.size)
        }
    }

    @Nested
    inner class Logging {
        @Test
        fun `logs one hiit entry with the rounds actually completed`() {
            val runner = Runner(workoutOf(single("burpees")))
            repeat(5) { runner.advance() }

            assertEquals(listOf(SessionEntry.Hiit("burpees", 3)), runner.entries)
        }

        @Test
        fun `records reps, weight and rpe on each logged set`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(8))
            runner.send(RunnerCommand.SetWeight(22.5))
            runner.send(RunnerCommand.SetRpe(9.0))
            repeat(5) { runner.advance() }

            assertEquals(3, runner.pullupSets().size)
            assertEquals(RepsSetLog(8, 22.5, 9.0, 0.0), runner.pullupSets()[0])
        }

        @Test
        fun `seeds reps from the target and load from config, and carries load between sets`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertEquals(6L, runner.inputs.reps)
            assertEquals(20.0, runner.inputs.weightKg)

            runner.send(RunnerCommand.SetWeight(30.0))
            runner.send(RunnerCommand.SetReps(4))
            runner.advance()
            runner.advance()

            assertEquals(6L, runner.inputs.reps)
            assertEquals(30.0, runner.inputs.weightKg)
        }

        @Test
        fun `falls back to the target load after a bodyweight set`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(0.0))
            runner.advance()
            runner.advance()

            assertEquals(20.0, runner.inputs.weightKg)
        }

        @Test
        fun `keeps rpe for the whole session rather than re-seeding it`() {
            val runner = Runner(workoutOf(single("pullups"), single("dips")))
            runner.send(RunnerCommand.SetRpe(7.0))
            repeat(6) { runner.advance() }

            assertEquals("dips", runner.step?.exerciseId)
            assertEquals(7.0, runner.inputs.rpe)
            assertEquals(listOf(7.0, 7.0, 7.0), runner.pullupSets().map { it.rpe })
        }

        @Test
        fun `seeds an EMOM minute from its target, and logs it without a tap`() {
            val runner = Runner(workoutOf(single("swings")))
            assertEquals(10L, runner.inputs.reps)

            runner.advance()
            assertEquals(10L, runner.inputs.reps)
            runner.send(RunnerCommand.SetReps(8))
            runner.advance()
            runner.advance()

            assertEquals(listOf(10L, 8L, 10L).map(::EmomMinuteLog), runner.entry<SessionEntry.Emom>("swings")?.minutes)
        }

        @Test
        fun `leaves an EMOM with no prescribed reps at zero, which logs the minute as unrecorded`() {
            val runner = Runner(workoutOf(single("climb")))
            assertEquals(0L, runner.inputs.reps)
            runner.advance()
            runner.advance()

            assertEquals(listOf(EmomMinuteLog(), EmomMinuteLog()), runner.entry<SessionEntry.Emom>("climb")?.minutes)
        }

        @Test
        fun `logs bodyweight as an absent weight rather than a zero load`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetWeight(0.0))
            runner.advance()

            assertNull(runner.pullupSets()[0].weightKg)
        }

        @Test
        fun `logs an amrap's rounds and extra reps once`() {
            val runner = Runner(workoutOf(single("grinder")))
            runner.send(RunnerCommand.SetRounds(5))
            runner.send(RunnerCommand.SetExtraReps(3))
            runner.advance()

            assertEquals(listOf(SessionEntry.Amrap("grinder", 5, 3)), runner.entries)
        }

        @Test
        fun `logs a standalone rest as its own entry with the time taken`() {
            val rest = Workout("w", "W", listOf(single("pullups"), single("rest"), single("dips")))
            val runner = Runner(rest)
            repeat(5) { runner.advance() }
            assertEquals("rest", runner.step?.exerciseId)
            runner.tick(42)
            runner.advance()

            assertEquals(SessionEntry.Rest("rest", 42.0), runner.entries[1])
        }

        @Test
        fun `completes the session once the last step is done`() {
            val runner = Runner(workoutOf(single("grinder")))
            runner.send(RunnerCommand.SetRounds(1))
            runner.tick(10)
            runner.advance()

            assertTrue(runner.state.finished)
            assertEquals(1, runner.completions)
            assertEquals("2026-07-27T09:00:10.000Z", runner.written.endedAt)
        }

        @Test
        fun `groups each circuit member into one entry across rounds`() {
            val runner = Runner(workoutOf(circuitOf("pullups", "lsit", between = 15.0, betweenRounds = 60.0)))
            repeat(runner.snapshot.steps.size) {
                if (runner.step is RunnerStep.Hold) runner.tick(5)
                runner.advance()
            }

            assertEquals(2, runner.pullupSets().size)
            assertEquals(2, runner.entry<SessionEntry.TimedHold>("lsit")?.sets?.size)
            assertEquals(2, runner.entries.size)
        }
    }

    @Nested
    inner class ZeroWork {
        @Test
        fun `writes no entry for an exercise whose only set was zero`() {
            val runner = Runner(workoutOf(single("pullups"), single("lsit")))
            runner.send(RunnerCommand.SetReps(0))
            runner.advance()

            assertTrue(runner.entries.isEmpty())
        }

        @Test
        fun `writes no entry for a hold ended at 0s`() {
            val runner = Runner(workoutOf(single("lsit"), single("pullups")))
            runner.advance()

            assertTrue(runner.entries.isEmpty())
        }

        @Test
        fun `drops the zero set and its rest without touching the set before it`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.tick(30)
            runner.advance()
            runner.send(RunnerCommand.SetReps(0))
            runner.advance()
            runner.tick(50)
            runner.advance()
            runner.advance()

            assertEquals(listOf(6L, 6L), runner.pullupSets().map { it.reps })
            assertEquals(30.0, runner.pullupSets()[0].restTakenSec)
        }

        @Test
        fun `has nothing to undo after a dropped set, so Prev keeps the set before it`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.advance()
            runner.send(RunnerCommand.SetReps(0))
            runner.advance()
            runner.prev()

            assertEquals(1, runner.pullupSets().size)
            assertEquals(2, runner.index)
        }

        @Test
        fun `logs no cardio ended at 0s, even with a distance target`() {
            val runner = Runner(workoutOf(single("run"), single("pullups")))
            runner.advance()

            assertTrue(runner.entries.isEmpty())
        }

        @Test
        fun `logs no AMRAP with no rounds, and Prev then leaves the entry before it alone`() {
            val runner = Runner(workoutOf(single("row"), single("grinder"), single("pullups")))
            runner.tick(60)
            assertEquals(listOf(SessionEntry.Cardio("row", 60.0)), runner.entries)

            runner.advance()
            assertEquals(1, runner.entries.size)
            runner.prev()
            assertEquals(1, runner.entries.size)
        }

        @Test
        fun `keeps hiit and emom work however little was entered`() {
            val runner = Runner(workoutOf(single("climb")))
            runner.advance()

            assertNotNull(runner.entry<SessionEntry.Emom>("climb"))
        }
    }

    @Nested
    inner class Finish {
        @Test
        fun `commits the in-progress set rather than discarding it`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(4))
            runner.finish()

            assertEquals(listOf(4L), runner.pullupSets().map { it.reps })
            assertNotNull(runner.written.endedAt)
            assertEquals(1, runner.completions)
        }

        @Test
        fun `aimed at a rest that has since ended, ends without logging the set it handed over to`() {
            // #319: the rest's last tick queued ahead of a Finish seeded the next set, and Finish logged it.
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            val rest = runner.snapshot.visit
            runner.away(90)
            check(runner.step is RunnerStep.Reps) { "the rest should have handed over to set 2" }
            runner.finish(visit = rest)

            assertEquals(1, runner.pullupSets().size)
            assertNotNull(runner.written.endedAt)
            assertEquals(1, runner.completions)
        }

        @Test
        fun `aimed at the set on screen, logs it`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.away(90)
            runner.finish(visit = runner.snapshot.visit)

            assertEquals(2, runner.pullupSets().size)
            assertEquals(1, runner.completions)
        }

        @Test
        fun `logs nothing more when it is called a second time`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.finish()
            val ended = runner.written
            runner.now += 60 * MS
            runner.finish()
            runner.advance()

            assertEquals(1, runner.pullupSets().size)
            assertEquals(1, runner.completions)
            assertEquals(ended, runner.written)
        }

        @Test
        fun `asks for one write at completion, the final one`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.clearEffects()
            runner.finish()

            val (write, completed) = runner.effects.drop(1)
            assertEquals(RunnerEffect.WriteSession(runner.written), write)
            assertEquals(RunnerEffect.Completed(runner.written), completed)
        }
    }
}
