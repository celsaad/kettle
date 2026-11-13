package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The state file and what a launch does with one after process death (#295): the snapshot's round
 * trip, Resume's rebuild and catch-up, landing paused, the degrade cases and Finish's timestamp. The
 * snapshot is native only, so no `conformance/` case holds it.
 */
class RunnerRecoveryTest {
    /** A session part-way through: a circuit ahead, a logged set, a swap, inputs entered and a pause. */
    private fun busyRunner(): Runner {
        val runner = Runner(workoutOf(single("pullups"), circuitOf("plank", "burpees", between = 15.0), single("lsit")))
        runner.send(RunnerCommand.SetReps(0))
        runner.advance() // a dropped set, and its rest
        runner.tick(20)
        runner.advance()
        runner.advance() // set 2, logged
        runner.advance()
        runner.send(RunnerCommand.Swap(runner.exercise("dips"))) // set 3 becomes dips
        runner.send(RunnerCommand.SetReps(11))
        runner.send(RunnerCommand.SetWeight(12.5))
        runner.send(RunnerCommand.TogglePause)
        return runner
    }

    private fun roundTrip(snapshot: RunnerSnapshot) =
        (decodeRunnerSnapshot(encodeRunnerSnapshot(snapshot)) as SnapshotRead.Read).snapshot

    @Nested
    inner class Snapshot {
        @Test
        fun `round-trips a session part-way through, every field`() {
            val snapshot = busyRunner().snapshot.let { it.copy(droppedLastSet = setOf("1:0", "0~swap1")) }

            assertTrue(snapshot.entryIndex.isNotEmpty() && snapshot.clock.paused && snapshot.swapCount == 1)
            assertEquals(snapshot, roundTrip(snapshot))
        }

        @Test
        fun `round-trips every step kind, a circuit's position included`() {
            val workout =
                workoutOf(
                    single("lsit"),
                    single("plank"),
                    single("deadhang"),
                    circuitOf("pullups", "burpees", betweenRounds = 30.0),
                )
            val all =
                workout.blocks + RUNNER_EXERCISES.filter { it.config !is ExerciseConfig.Rest }.map { single(it.id) } +
                    single("rest")
            val snapshot = Runner(Workout("w", "W", all)).snapshot

            assertTrue(snapshot.steps.any { it.circuit != null })
            assertEquals(snapshot.steps, roundTrip(snapshot).steps)
        }

        @Test
        fun `round-trips an ad-hoc session parked past its last step`() {
            val runner = Runner(null)
            runner.send(RunnerCommand.AddExercise(runner.exercise("dips")))
            repeat(3) { runner.advance() }

            assertEquals(runner.snapshot.steps.size, runner.snapshot.stepIndex)
            assertEquals(runner.snapshot, roundTrip(runner.snapshot))
        }

        @Test
        fun `reads a corrupt file as corrupt rather than throwing`() {
            val good = encodeRunnerSnapshot(busyRunner().snapshot)
            val broken =
                listOf(
                    "",
                    "not json",
                    "[]",
                    good.dropLast(10),
                    good.replace("\"stepIndex\":", "\"stepIndexx\":"),
                    good.replace("\"kind\":\"reps\"", "\"kind\":\"sprint\""),
                    good.replace("\"sessionId\":\"", "\"sessionId\":1,\"x\":\""),
                    good.replace(Regex("\"stepIndex\":\\d+"), "\"stepIndex\":9999"),
                )

            broken.forEach { assertEquals(SnapshotRead.Corrupt, decodeRunnerSnapshot(it), it.take(40)) }
        }

        @Test
        fun `writes an empty effort as null, and reads it back empty or as picked`() {
            val runner = busyRunner()
            val empty = runner.snapshot.copy(inputs = runner.snapshot.inputs.copy(rpe = null))
            val picked = runner.snapshot.copy(inputs = runner.snapshot.inputs.copy(rpe = 7.5))

            assertTrue("\"rpe\":null" in encodeRunnerSnapshot(empty))
            assertEquals(SnapshotRead.Read(empty), decodeRunnerSnapshot(encodeRunnerSnapshot(empty)))
            assertEquals(SnapshotRead.Read(picked), decodeRunnerSnapshot(encodeRunnerSnapshot(picked)))
        }

        /** A session running when the app updates: its version 1 file, effort always a number, still resumes. */
        @Test
        fun `reads a version 1 file, whose effort is always a number`() {
            val runner = busyRunner()
            val before = runner.snapshot.copy(inputs = runner.snapshot.inputs.copy(rpe = 8.0))
            val v1 = encodeRunnerSnapshot(before).replace("\"version\":2,", "\"version\":1,")

            assertTrue("\"version\":1," in v1)
            assertEquals(SnapshotRead.Read(before), decodeRunnerSnapshot(v1))
        }

        @Test
        fun `refuses a version it doesn't know rather than guess at it`() {
            val future = encodeRunnerSnapshot(busyRunner().snapshot).replace("\"version\":2,", "\"version\":3,")

            assertEquals(SnapshotRead.UnknownVersion(3), decodeRunnerSnapshot(future))
        }
    }

    @Nested
    inner class Recovery {
        private fun stored(runner: Runner) = SessionFile.Read(runner.written, modifiedAt = runner.now + 5 * MS)

        @Test
        fun `offers Resume or Finish for a snapshot that fits its session file`() {
            val runner = busyRunner()

            assertEquals(
                RunnerRecovery.Prompt(runner.snapshot, runner.written),
                recoverRunner(encodeRunnerSnapshot(runner.snapshot), stored(runner)),
            )
        }

        @Test
        fun `offers Finish only for a corrupt snapshot, ending it at the file's last write`() {
            val runner = busyRunner()
            val recovery = recoverRunner("{", stored(runner))

            assertEquals(RunnerRecovery.FinishOnly(runner.written, runner.now + 5 * MS), recovery)
            assertEquals(isoInstant(runner.now + 5 * MS), (recovery as RunnerRecovery.Finishable).finished.endedAt)
        }

        @Test
        fun `offers Finish only for an unknown version`() {
            val runner = busyRunner()
            val future = encodeRunnerSnapshot(runner.snapshot).replace("\"version\":2,", "\"version\":3,")

            assertTrue(recoverRunner(future, stored(runner)) is RunnerRecovery.FinishOnly)
        }

        @Test
        fun `offers Finish only for a snapshot that doesn't fit the session file`() {
            val runner = busyRunner()
            val text = encodeRunnerSnapshot(runner.snapshot)
            val emptied = stored(runner).copy(session = runner.written.copy(entries = emptyList()))
            val other = stored(runner).copy(session = runner.written.copy(id = "another"))

            assertTrue(recoverRunner(text, emptied) is RunnerRecovery.FinishOnly)
            assertTrue(recoverRunner(text, other) is RunnerRecovery.FinishOnly)
        }

        /** A read that failed once mustn't cost the session: the state file stays for the next launch. */
        @Test
        fun `keeps the state file when reading the session file failed`() {
            val runner = busyRunner()

            assertEquals(
                RunnerRecovery.Leave,
                recoverRunner(encodeRunnerSnapshot(runner.snapshot), SessionFile.Unreadable),
            )
            assertEquals(RunnerRecovery.Leave, recoverRunner("{", SessionFile.Unreadable))
        }

        @Test
        fun `deletes the state file silently when the session file is gone, finished or doesn't parse`() {
            val runner = busyRunner()
            val text = encodeRunnerSnapshot(runner.snapshot)
            val finished = stored(runner).copy(session = runner.written.copy(endedAt = "2026-07-27T10:00:00.000Z"))

            assertEquals(RunnerRecovery.DeleteSilently, recoverRunner(text, SessionFile.Absent))
            assertEquals(RunnerRecovery.DeleteSilently, recoverRunner("{", SessionFile.Absent))
            // A parse failure won't fix itself, so keeping the state file would offer nothing forever.
            assertEquals(RunnerRecovery.DeleteSilently, recoverRunner(text, SessionFile.Corrupt))
            assertEquals(RunnerRecovery.DeleteSilently, recoverRunner(text, finished))
        }

        /** The dead hours after the last transition don't count toward the session's length. */
        @Test
        fun `stamps a Finish from the prompt with the snapshot's last write, not now`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.tick(30)
            runner.advance()
            val lastWrite = runner.now
            runner.tick(60) // ticks change no snapshot, so they aren't a write

            val prompt = recoverRunner(encodeRunnerSnapshot(runner.snapshot), stored(runner)) as RunnerRecovery.Prompt
            assertEquals(lastWrite, prompt.snapshot.updatedAt)
            assertEquals(isoInstant(lastWrite), prompt.finished.endedAt)
            assertEquals(runner.written.entries, prompt.finished.entries)
        }

        @Test
        fun `commits nothing more on a Finish from the prompt`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(4))

            val prompt = recoverRunner(encodeRunnerSnapshot(runner.snapshot), stored(runner)) as RunnerRecovery.Prompt
            assertTrue(prompt.finished.entries.isEmpty())
        }
    }

    @Nested
    inner class Resume {
        private fun promptFor(runner: Runner) =
            recoverRunner(
                encodeRunnerSnapshot(runner.snapshot),
                SessionFile.Read(runner.written, runner.now),
            ) as RunnerRecovery.Prompt

        @Test
        fun `catches up a countdown that expired during the kill, and lands paused on the next step`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance() // onto the 90 s rest
            val prompt = promptFor(runner)
            val back = T0 + 3_600 * MS

            val resumed = resumeRunner(prompt, back)

            assertEquals(2, resumed.state.snapshot.stepIndex)
            assertTrue(resumed.state.snapshot.clock.paused)
            assertEquals(0L, elapsedSec(resumed.state.snapshot.clock, back + 60 * MS))
            val sets =
                (
                    resumed.state.session.entries
                        .single() as SessionEntry.Reps
                ).sets
            // The planned 90 s, not the hour the app was dead (#304, decided on review).
            assertEquals(90.0, sets.single().restTakenSec)
            assertTrue(RunnerEffect.WriteSession(resumed.state.session) in resumed.effects)
        }

        /**
         * Resume lands paused on any step, rests and reps included, whose RN screens have no Resume
         * (§2.3). The engine pauses and resumes every kind, so the native screens can offer it (#306).
         */
        @Test
        fun `resumes a rest it landed paused on, which then counts out and advances`() {
            val runner = Runner(workoutOf(single("burpees")))
            val back = T0 + 3_600 * MS
            val landed = resumeRunner(promptFor(runner), back).state
            assertTrue(landed.snapshot.step is RunnerStep.Rest && landed.snapshot.clock.paused)

            val resumed = reduceRunner(landed, RunnerCommand.TogglePause, back + 5 * MS).state
            assertEquals(20.0, remainingSec(resumed.snapshot.clock, back + 5 * MS))
            val ended = reduceRunner(resumed, RunnerCommand.TimeUpdate(resumed.snapshot.visit), back + 25 * MS).state
            assertEquals(2, ended.snapshot.stepIndex)
        }

        @Test
        fun `resumes a reps step it landed paused on, and logs it as usual`() {
            val runner = Runner(workoutOf(single("pullups")))
            val landed = resumeRunner(promptFor(runner), T0 + 600 * MS).state
            assertTrue(landed.snapshot.clock.paused)

            val resumed = reduceRunner(landed, RunnerCommand.TogglePause, T0 + 610 * MS).state
            assertFalse(resumed.snapshot.clock.paused)
            val logged = reduceRunner(resumed, RunnerCommand.Advance(resumed.snapshot.visit), T0 + 620 * MS).state
            assertEquals(6L, (logged.session.entries.single() as SessionEntry.Reps).sets.single().reps)
        }

        @Test
        fun `logs a standalone rest that ran out during the kill at its length, and ends there if it was last`() {
            val runner = Runner(Workout("w", "W", listOf(single("dips"), single("rest"))))
            repeat(3) { runner.advance() }
            runner.tick(20)
            val resumed = resumeRunner(promptFor(runner), T0 + 3_600 * MS)

            assertTrue(resumed.state.finished)
            assertEquals(
                SessionEntry.Rest("rest", 60.0),
                resumed.state.session.entries
                    .last(),
            )
            assertEquals(isoInstant(T0 + 60 * MS), resumed.state.session.endedAt)
        }

        @Test
        fun `logs a rest still running at the return as the time taken so far`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            val resumed = resumeRunner(promptFor(runner), T0 + 40 * MS)
            val unpaused = reduceRunner(resumed.state, RunnerCommand.TogglePause, T0 + 40 * MS).state
            val skipped = reduceRunner(unpaused, RunnerCommand.Advance(unpaused.snapshot.visit), T0 + 40 * MS).state

            assertEquals(40.0, (skipped.session.entries.single() as SessionEntry.Reps).sets.single().restTakenSec)
        }

        @Test
        fun `advances at most one step, however long the kill`() {
            val runner = Runner(workoutOf(single("burpees")))
            val resumed = resumeRunner(promptFor(runner), T0 + 86_400 * MS)

            assertEquals(1, resumed.state.snapshot.stepIndex)
        }

        @Test
        fun `lands paused on a step that hadn't ended, keeping the inputs entered`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.send(RunnerCommand.SetReps(9))
            val resumed = resumeRunner(promptFor(runner), T0 + 600 * MS)

            assertEquals(0, resumed.state.snapshot.stepIndex)
            assertTrue(resumed.state.snapshot.clock.paused)
            assertEquals(9L, resumed.state.snapshot.inputs.reps)
        }

        @Test
        fun `stays paused, and its paused time excluded, when it was paused before the kill`() {
            val runner = Runner(workoutOf(single("burpees")))
            runner.tick(10)
            runner.send(RunnerCommand.TogglePause)
            val resumed = resumeRunner(promptFor(runner), T0 + 600 * MS)

            assertEquals(0, resumed.state.snapshot.stepIndex)
            assertEquals(30.0, remainingSec(resumed.state.snapshot.clock, T0 + 600 * MS))
        }

        @Test
        fun `starts with nothing to undo`() {
            val runner = Runner(workoutOf(single("pullups")))
            runner.advance()
            val resumed = resumeRunner(promptFor(runner), runner.now)
            val back = reduceRunner(resumed.state, RunnerCommand.Prev, runner.now)

            assertEquals(
                1,
                (
                    back.state.session.entries
                        .single() as SessionEntry.Reps
                ).sets.size,
            )
        }

        @Test
        fun `completes when the last step ran out during the kill`() {
            val runner = Runner(workoutOf(single("row")))
            val resumed = resumeRunner(promptFor(runner), T0 + 600 * MS)

            assertTrue(resumed.state.finished)
            assertEquals(
                60.0,
                (
                    resumed.state.session.entries
                        .single() as SessionEntry.Cardio
                ).durationSec,
            )
            assertTrue(resumed.effects.any { it is RunnerEffect.Completed })
        }

        @Test
        fun `plays no cue and posts no alert while the prompt is on screen`() {
            val runner = Runner(workoutOf(single("burpees"), single("pullups")))
            repeat(4) { runner.advance() }
            val resumed = resumeRunner(promptFor(runner), T0 + 600 * MS)

            assertFalse(resumed.effects.any { it is RunnerEffect.PlayCue || it is RunnerEffect.StepEnded })
            assertEquals(5, resumed.state.snapshot.stepIndex)
        }

        @Test
        fun `goes on from the rebuild as it would have from the live state`() {
            val runner = busyRunner()
            val resumed = resumeRunner(promptFor(runner), runner.now)
            var state = reduceRunner(resumed.state, RunnerCommand.TogglePause, runner.now).state
            state = reduceRunner(state, RunnerCommand.Advance(state.snapshot.visit), runner.now).state

            assertEquals(listOf("pullups", "dips"), state.session.entries.map { it.exercise })
            assertEquals(RepsSetLog(11, 12.5, null, 0.0), (state.session.entries[1] as SessionEntry.Reps).sets.single())
        }
    }

    /** What a Resume runs: the session's start, read back from its file, against the library as it is now. */
    @Nested
    inner class ResumedPlan {
        private val workout = workoutOf(single("pullups"), single("plank"))
        private val library = Library(1, RUNNER_EXERCISES, listOf(workout), emptyList())

        @Test
        fun `reads a workout's start back from the session it wrote`() {
            val session = Runner(workout).written

            assertEquals(RunnerStart.Planned(SessionParams.Workout("w")), recoveredStart(session))
        }

        @Test
        fun `reads a program week's start back, its day included`() {
            val session = Runner(workout).written.copy(program = "p", programWeek = 2, programDay = "Day 1")

            assertEquals(RunnerStart.Planned(SessionParams.ProgramSlot("p", 2, "Day 1")), recoveredStart(session))
        }

        @Test
        fun `reads an ad-hoc session's start back as ad hoc`() {
            assertEquals(RunnerStart.Adhoc, recoveredStart(Runner(null).written))
        }

        @Test
        fun `runs the snapshot's steps, while the plan holds what the workout plans now`() {
            val runner = Runner(workout)
            runner.send(RunnerCommand.Swap(runner.exercise("dips")))
            runner.send(RunnerCommand.AddSet)
            val edited = library.copy(workouts = listOf(workoutOf(single("lsit"))))

            val plan = resumedPlan(runner.written, runner.snapshot, edited)

            // The session goes on from the snapshot, swap and added set and all.
            assertEquals(
                runner.snapshot.steps,
                resumeRunner(RunnerRecovery.Prompt(runner.snapshot, runner.written), runner.now).state.snapshot.steps,
            )
            // The plan's steps are what was planned, as the library has it now: what "planned" counts
            // against in the Sets sheet and Try, which the snapshot's edited steps would overstate (#410).
            assertEquals(buildSteps(edited.workouts.single(), RUNNER_EXERCISES), plan.steps)
            assertEquals("W", plan.workout?.name)
            assertEquals(RUNNER_EXERCISES, plan.exercises)
        }

        @Test
        fun `keeps a workout deleted since by its id, as History names it`() {
            val runner = Runner(workout)

            val plan = resumedPlan(runner.written, runner.snapshot, library.copy(workouts = emptyList()))

            assertEquals("w", plan.workout?.name)
            assertEquals(RUNNER_EXERCISES, plan.exercises)
        }

        @Test
        fun `keeps the library's name for a workout edited since to nothing it can run`() {
            val runner = Runner(workout)
            val emptied = library.copy(workouts = listOf(Workout("w", "Renamed", listOf(single("gone")))))

            assertEquals("Renamed", resumedPlan(runner.written, runner.snapshot, emptied).workout?.name)
        }

        @Test
        fun `resumes an ad-hoc session with no workout`() {
            val runner = Runner(null)

            assertEquals(null, resumedPlan(runner.written, runner.snapshot, library).workout)
        }
    }

    /**
     * Sessions RN's runner left unfinished (#308): nothing of the native runner's names them, so no
     * prompt ever will, and History would show them running for good.
     */
    @Nested
    inner class StrandedSessions {
        private fun file(
            id: String,
            endedAt: String? = null,
            modifiedAt: Long = T0 + 120 * MS,
        ) = SessionFile.Read(
            Session(1, id, "w", null, null, null, "2026-07-27T09:00:00.000Z", endedAt, emptyList()),
            modifiedAt,
        )

        @Test
        fun `ends an unfinished session no state file names at its file's last write`() {
            val stranded = strandedSessions(listOf(file("rn")), runnerSessionIds = emptySet())

            assertEquals(listOf("rn" to "2026-07-27T09:02:00.000Z"), stranded.map { it.id to it.endedAt })
        }

        @Test
        fun `leaves a finished session, and one the native runner still holds a state file for`() {
            val files = listOf(file("done", endedAt = "2026-07-27T09:30:00.000Z"), file("native"), file("rn"))

            assertEquals(listOf("rn"), strandedSessions(files, runnerSessionIds = setOf("native")).map { it.id })
        }
    }
}
