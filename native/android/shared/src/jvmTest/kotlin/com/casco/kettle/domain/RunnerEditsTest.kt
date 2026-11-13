package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The engine's step-list edits, from RN `use-session-runner.test.tsx`'s restFollows, upcoming, adding
 * and dropping sets, swapping, ad-hoc and re-seeding sections. Edits take whatever step is on screen,
 * as RN's hook does; [canAddSet], [canDropSet] and [swapChoices] are the buttons' rules.
 */
class RunnerEditsTest {
    private fun Runner.repsEntry(index: Int) = entries[index] as SessionEntry.Reps

    private fun Runner.work() = step as WorkStep

    private fun Runner.add(id: String) = send(RunnerCommand.AddExercise(exercise(id)))

    private fun Runner.swap(id: String) = send(RunnerCommand.Swap(exercise(id)))

    /** Every swap the sheet offers, in its order: its own type first, then the rest. */
    private fun Runner.swapOffered() =
        swapChoices(snapshot, exercises)?.let { it.same + it.other }.orEmpty().map { it.id }

    private fun summary(items: List<UpcomingItem>) =
        items.map {
            when (it) {
                is UpcomingItem.Exercise -> "${it.step.exerciseName} x${it.left}${if (it.started) " (started)" else ""}"
                else -> it::class.simpleName
            }
        }

    @Nested
    inner class RestFollowsTest {
        @Test
        fun `is true between sets that have real rest`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertTrue(restFollows(runner.snapshot.steps, runner.index))
        }

        @Test
        fun `is false between back-to-back sets`() {
            val runner = Runner(workoutOf(single("dips")))
            assertFalse(restFollows(runner.snapshot.steps, runner.index))
            runner.advance()
            assertFalse(restFollows(runner.snapshot.steps, runner.index))
        }

        @Test
        fun `is false on the last step of the workout`() {
            val runner = Runner(workoutOf(single("pullups")))
            repeat(4) { runner.advance() } // set, rest, set, rest: now on set 3, the final step
            assertFalse(restFollows(runner.snapshot.steps, runner.index))
        }
    }

    @Nested
    inner class Upcoming {
        @Test
        fun `lists what comes after the step on screen, and moves with it`() {
            val runner = Runner(workoutOf(single("pullups"), single("lsit")))
            val expected = listOf("Pull-ups x2 (started)", "L-Sit x3")
            assertEquals(expected, summary(upcomingOutline(runner.snapshot.steps, runner.index)))

            // Onto the rest after set 1: the same two sets are still to come.
            runner.advance()
            assertTrue(runner.step is RunnerStep.Rest)
            assertEquals(expected, summary(upcomingOutline(runner.snapshot.steps, runner.index)))

            // Onto set 2: one fewer.
            runner.advance()
            assertEquals(
                listOf("Pull-ups x1 (started)", "L-Sit x3"),
                summary(upcomingOutline(runner.snapshot.steps, runner.index)),
            )
        }

        @Test
        fun `follows a set added mid-session`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.AddSet)
            assertEquals(listOf("Pull-ups x3 (started)"), summary(upcomingOutline(runner.snapshot.steps, runner.index)))
        }

        /** Kotlin's equivalent of RN's "not rebuilt by the tick": the snapshot changes on transitions only. */
        @Test
        fun `is not rebuilt by the tick`() {
            val runner = Runner(workoutOf(single("lsit"), single("pullups")))
            val before = runner.snapshot
            runner.tick(1)
            assertSame(before, runner.snapshot)
        }
    }

    @Nested
    inner class AddingAndDroppingSets {
        @Test
        fun `offers the controls on a plain reps step`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertTrue(canAddSet(runner.snapshot))
            assertTrue(canDropSet(runner.state))
        }

        @Test
        fun `offers nothing inside a circuit`() {
            val runner = Runner(workoutOf(circuitOf("pullups", "dips")))
            assertFalse(canAddSet(runner.snapshot))
            assertFalse(canDropSet(runner.state))
        }

        @Test
        fun `adds a set to the exercise on screen`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertEquals(1L, runner.work().setIndex)
            assertEquals(3L, runner.work().setTotal)

            runner.send(RunnerCommand.AddSet)

            assertEquals(1L, runner.work().setIndex)
            assertEquals(4L, runner.work().setTotal)
            assertEquals(7, runner.snapshot.steps.size)
        }

        @Test
        fun `drops one, and stops at the set in progress`() {
            val runner = Runner(workoutOf(single("plank")))
            assertEquals(2L, runner.work().setTotal)

            runner.send(RunnerCommand.DropSet)
            assertEquals(1L, runner.work().setIndex)
            assertEquals(1L, runner.work().setTotal)

            // Nothing left but the set being performed, so there is nothing further to give up.
            assertFalse(canDropSet(runner.state))
        }

        @Test
        fun `will not drop below what has already been logged`() {
            val runner = Runner(workoutOf(single("pullups")))
            repeat(4) { runner.advance() }

            assertEquals(3L, runner.work().setIndex)
            assertEquals(3L, runner.work().setTotal)
            assertFalse(canDropSet(runner.state))

            val before = runner.snapshot
            runner.send(RunnerCommand.DropSet)
            assertSame(before, runner.snapshot)
        }

        /**
         * A zero set is never logged, so a floor counted from the log alone let a drop remove the set
         * on screen and leave the index past the list (#314 review). The floor is also positional now.
         */
        @Test
        fun `never drops the set on screen after a zero set`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(0))
            runner.advance()
            runner.advance()
            assertEquals(2, runner.index)

            runner.send(RunnerCommand.DropSet)
            runner.send(RunnerCommand.DropSet)

            assertEquals(3, runner.snapshot.steps.size)
            assertEquals(2L, runner.work().setIndex)
            assertFalse(canDropSet(runner.state))
        }

        @Test
        fun `never drops the rest on screen after a zero set`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.advance()
            runner.send(RunnerCommand.SetReps(0))
            runner.advance()
            assertTrue(runner.step is RunnerStep.Rest)

            runner.send(RunnerCommand.DropSet)

            assertTrue(runner.step is RunnerStep.Rest)
            assertEquals(5, runner.snapshot.steps.size)
        }

        @Test
        fun `does not retract a logged set when Prev follows a mutation`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(9))
            runner.advance()
            runner.send(RunnerCommand.AddSet) // from the rest step, as RN's case does
            runner.prev()

            assertEquals(1, runner.entries.size)
            assertEquals(listOf(9L), runner.repsEntry(0).sets.map { it.reps })
        }

        @Test
        fun `logs an added set into the same entry, not a second one`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.AddSet)
            repeat(7) { runner.advance() } // four sets and three rests

            assertEquals(1, runner.entries.size)
            assertEquals(4, runner.repsEntry(0).sets.size)
        }

        @Test
        fun `keeps the log button honest after adding to a back-to-back exercise`() {
            val runner = Runner(workoutOf(single("dips")))
            assertFalse(restFollows(runner.snapshot.steps, runner.index))

            runner.send(RunnerCommand.AddSet)

            assertFalse(restFollows(runner.snapshot.steps, runner.index))
            assertEquals(4L, runner.work().setTotal)
        }

        /** RN's hook edits from any step, the rest after a set included; only its screen hides the controls. */
        @Test
        fun `edits the member of a rest step, as RN's hook does`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            assertTrue(runner.step is RunnerStep.Rest)
            assertFalse(canAddSet(runner.snapshot))

            runner.send(RunnerCommand.AddSet)
            assertEquals(4, setStepsForMember(runner.snapshot.steps, "0"))
            runner.send(RunnerCommand.DropSet)
            assertEquals(3, setStepsForMember(runner.snapshot.steps, "0"))

            runner.swap("dips")
            assertEquals("dips", runner.step?.exerciseId)
            assertEquals(2L, runner.work().setTotal)
        }

        /** RN's `mutateSteps` clears undo before it knows whether the list moved. */
        @Test
        fun `clears undo even when the edit changes nothing`() {
            val runner = Runner(workoutOf(circuitOf("pullups", "dips")))
            runner.advance() // pull-ups round 1, logged; now on dips
            val steps = runner.snapshot.steps
            runner.send(RunnerCommand.AddSet) // a circuit member's sets are refused
            assertSame(steps, runner.snapshot.steps)
            runner.prev()

            assertEquals(1, runner.repsEntry(0).sets.size)
        }

        @Test
        fun `does nothing with nothing on screen`() {
            val runner = Runner(null)
            val before = runner.state
            runner.send(RunnerCommand.AddSet)
            runner.swap("dips")

            assertSame(before, runner.state)
        }
    }

    @Nested
    inner class Swapping {
        @Test
        fun `offers every other exercise, minus the one on screen and the built-in rest`() {
            val runner = Runner(workoutOf(single("pullups")))
            val offered = runner.swapOffered()

            assertFalse("pullups" in offered)
            assertFalse("rest" in offered)
            assertEquals(
                RUNNER_EXERCISES.map { it.id }.filter { it != "rest" && it != "pullups" }.sorted(),
                offered.sorted(),
            )
        }

        @Test
        fun `groups the choices as the sheet shows them, its own type apart from the rest`() {
            val runner = Runner(workoutOf(single("lsit")))
            val choices = swapChoices(runner.snapshot, runner.exercises)!!

            assertEquals("lsit", choices.current.id)
            assertEquals(listOf("plank", "deadhang"), choices.same.map { it.id })
            assertTrue(choices.other.none { it.config is ExerciseConfig.TimedHold || it.config is ExerciseConfig.Rest })
        }

        @Test
        fun `lists the same type first, then the rest in library order`() {
            val runner = Runner(workoutOf(single("lsit")))
            val offered = runner.swapOffered()

            assertEquals(listOf("plank", "deadhang"), offered.take(2))
            assertEquals(
                RUNNER_EXERCISES
                    .filter { it.config !is ExerciseConfig.Rest && it.config !is ExerciseConfig.TimedHold }
                    .map { it.id },
                offered.drop(2),
            )
        }

        @Test
        fun `gives a hold substitute the remaining set count`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.advance()
            runner.swap("plank")

            assertTrue(runner.step is RunnerStep.Hold)
            assertEquals("plank", runner.work().exerciseId)
            assertEquals(1L, runner.work().setIndex)
            assertEquals(2L, runner.work().setTotal)
        }

        @Test
        fun `runs a non-set substitute in its own shape and logs it as its own entry`() {
            val runner = Runner(workoutOf(single("pullups"), single("plank")))
            runner.send(RunnerCommand.SetReps(7))
            runner.advance()
            runner.advance()
            runner.swap("burpees")

            // All three of its own rounds, though only two pull-up sets were left; then on to the plank.
            assertTrue(runner.step is RunnerStep.Interval)
            assertEquals("burpees", runner.work().exerciseId)
            repeat(5) { runner.advance() } // round, rest, round, rest, round

            assertTrue(runner.step is RunnerStep.Hold)
            assertEquals("plank", runner.work().exerciseId)
            assertEquals(listOf("pullups", "burpees"), runner.entries.map { it.exercise })
            assertEquals(3L, runner.entry<SessionEntry.Hiit>("burpees")?.roundsCompleted)
        }

        @Test
        fun `offers nothing inside a circuit`() {
            val runner = Runner(workoutOf(circuitOf("pullups", "dips")))
            assertNull(swapChoices(runner.snapshot, runner.exercises))

            // Refused by the mutation itself (#303), so the list stands; undo is cleared, as RN's always is.
            val before = runner.snapshot
            runner.swap("plank")
            assertSame(before, runner.snapshot)
        }

        @Test
        fun `runs the substitute for what was left`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            runner.advance()
            assertEquals(2L, runner.work().setIndex)
            assertEquals(3L, runner.work().setTotal)

            runner.swap("dips")

            // Two of three sets were left, so the substitute gets two, starting from its own set 1.
            assertEquals("dips", runner.work().exerciseId)
            assertEquals(1L, runner.work().setIndex)
            assertEquals(2L, runner.work().setTotal)
        }

        @Test
        fun `keeps the work already done in its own entry and appends a new one`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(7))
            runner.advance()
            runner.advance()
            runner.swap("dips")
            runner.send(RunnerCommand.SetReps(11))
            runner.advance()

            assertEquals(2, runner.entries.size)
            assertEquals("pullups", runner.repsEntry(0).exercise)
            assertEquals(listOf(7L), runner.repsEntry(0).sets.map { it.reps })
            assertEquals("dips", runner.repsEntry(1).exercise)
            assertEquals(listOf(11L), runner.repsEntry(1).sets.map { it.reps })
        }

        @Test
        fun `does not disturb the index of an entry written before the swap`() {
            val runner = Runner(workoutOf(single("pullups"), single("plank")))
            runner.advance()
            runner.advance()
            runner.swap("dips")
            runner.advance()
            runner.advance()
            // Onto the plank block, which is its own member and must still append cleanly after the swap.
            assertEquals("plank", runner.work().exerciseId)
            runner.tick(5) // a 0s hold is never written
            runner.advance()

            assertEquals(listOf("pullups", "dips", "plank"), runner.entries.map { it.exercise })
            assertEquals(1, runner.repsEntry(0).sets.size)
            assertEquals(2, runner.repsEntry(1).sets.size)
        }

        @Test
        fun `leaves the original out of the log entirely when nothing was done under it`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.swap("dips")
            runner.advance()

            assertEquals(listOf("dips"), runner.entries.map { it.exercise })
        }

        @Test
        fun `issues a fresh member key on every swap`() {
            val runner = Runner(workoutOf(single("pullups")))
            val original = runner.work().memberKey

            runner.swap("dips")
            val first = runner.work().memberKey
            runner.advance()
            runner.swap("pullups")
            val second = runner.work().memberKey
            runner.advance()

            assertEquals("$original~swap1", first)
            assertEquals("$first~swap2", second)
            assertNotEquals(original, first)
            assertNotEquals(original, second)
            assertNotEquals(first, second)
            assertEquals(listOf("dips", "pullups"), runner.entries.map { it.exercise })
        }

        /** The picker leaves both out ([swapChoices]); RN's hook takes either if asked. */
        @Test
        fun `swaps even to the exercise on screen, under a fresh key, as RN's hook does`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(7))
            runner.advance()
            runner.advance()
            runner.swap("pullups")

            assertEquals("0~swap1", runner.step?.memberKey)
            runner.advance()
            assertEquals(listOf("pullups", "pullups"), runner.entries.map { it.exercise })
        }
    }

    @Nested
    inner class AdHocSession {
        @Test
        fun `creates a session file with no workout, and no steps to run`() {
            val runner = Runner(null)

            assertNull(runner.written.workout)
            assertTrue(runner.snapshot.steps.isEmpty())
            assertNull(runner.step)
            assertTrue(runner.snapshot.adhoc)
        }

        @Test
        fun `runs an exercise once one is added`() {
            val runner = Runner(null)
            runner.add("pullups")

            assertEquals("pullups", runner.work().exerciseId)
            assertEquals(1L, runner.work().setIndex)
            assertEquals(3L, runner.work().setTotal)
            assertEquals(1, blockTotal(runner.snapshot))
        }

        @Test
        fun `parks rather than completing when the added exercise runs out`() {
            val runner = Runner(null)
            runner.add("dips")
            repeat(3) { runner.advance() } // three back-to-back sets exhaust it

            assertNull(runner.step)
            assertEquals(0, runner.completions)
            assertFalse(runner.state.finished)
        }

        @Test
        fun `picks straight up where it parked when another exercise is added`() {
            val runner = Runner(null)
            runner.add("dips")
            repeat(3) { runner.advance() }
            runner.add("pullups")

            assertEquals("pullups", runner.work().exerciseId)
            assertEquals(1L, runner.work().setIndex)
            assertEquals(2, blockTotal(runner.snapshot))
        }

        @Test
        fun `logs each added exercise into its own entry, in order`() {
            val runner = Runner(null)
            runner.add("dips")
            // Adding queues behind the current work rather than jumping to it, so dips runs out first.
            runner.advance()
            runner.add("pullups")
            runner.advance()
            runner.advance()
            assertEquals("pullups", runner.work().exerciseId)

            runner.advance()

            assertEquals(listOf("dips", "pullups"), runner.entries.map { it.exercise })
        }

        @Test
        fun `is finished by hand, committing the set in progress`() {
            val runner = Runner(null)
            runner.add("pullups")
            runner.send(RunnerCommand.SetReps(9))
            runner.finish()

            assertTrue(runner.state.finished)
            assertEquals(1, runner.completions)
            assertEquals(listOf(9L), runner.repsEntry(0).sets.map { it.reps })
        }

        @Test
        fun `can be finished having added nothing at all`() {
            val runner = Runner(null)
            runner.finish()

            assertTrue(runner.state.finished)
            assertEquals(emptyList<SessionEntry>(), runner.entries)
        }

        /** Only an ad-hoc screen offers it; RN's hook appends to a workout's list just the same. */
        @Test
        fun `appends an added exercise to a workout session too`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.add("dips")

            assertEquals(8, runner.snapshot.steps.size)
            assertEquals(
                "adhoc0",
                runner.snapshot.steps
                    .last()
                    .memberKey,
            )
            assertEquals(1, blockTotal(runner.snapshot))
        }
    }

    @Nested
    inner class ReSeeding {
        @Test
        fun `seeds reps from the exercise added to a parked ad-hoc session`() {
            val runner = Runner(null)
            runner.add("pullups")
            // pullups targets 6; parking had zeroed reps and the index does not move when the step lands.
            assertEquals(6L, runner.inputs.reps)
        }

        @Test
        fun `seeds reps from the substitute after a swap`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertEquals(6L, runner.inputs.reps)
            runner.swap("dips")
            assertEquals(8L, runner.inputs.reps)
        }

        @Test
        fun `seeds the load from the substitute too`() {
            val runner = Runner(workoutOf(single("pullups")))
            assertEquals(20.0, runner.inputs.weightKg)
            runner.swap("dips")
            // dips carries no target weight, so it is bodyweight rather than pullups' 20 kg.
            assertEquals(0.0, runner.inputs.weightKg)
        }

        @Test
        fun `leaves the set in progress alone when the set count changes`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(14))
            runner.send(RunnerCommand.AddSet)
            assertEquals(14L, runner.inputs.reps)
        }
    }
}
