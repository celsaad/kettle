package com.casco.kettle.shell.runner

import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.buildSteps
import com.casco.kettle.domain.startRunner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the runner's route shows and what back does there (#297, #306): the decisions the screen makes
 * before anything is drawn, apart from Compose so each case is one line.
 */
class RunnerFlowTest {
    private val pushups =
        Exercise(
            "pushups",
            "Push-ups",
            ExerciseConfig.Reps(
                sets = 2,
                targetRepsMin = 10,
                targetRepsMax = null,
                targetWeightKg = null,
                restSec = 30.0,
            ),
            notes = null,
        )
    private val workout = Workout("w", "W", listOf(WorkoutBlock.ExerciseBlock("pushups", null)))
    private val runnable = RunnerPlan.Runnable(workout, listOf(pushups), buildSteps(workout, listOf(pushups)))
    private val running =
        startRunner(runnable, null, now = 0).state.let { RunnerView.Running(runnable, it.snapshot, it.session) }
    private val countingIn = RunnerView.CountingIn(runnable, null, CountIn(0))
    private val completed =
        RunnerView.Completed(
            runnable,
            Session(1, "s", "w", null, null, null, "a", "b", emptyList()),
            FinishBackupState.OK,
        )

    @Test
    fun `a running session is attached to, whatever the route asked for`() {
        assertEquals(RunnerEntry.Attach(running), runnerEntry(running, RunnerPlan.RestWeek, opened = false))
        assertEquals(RunnerEntry.Attach(countingIn), runnerEntry(countingIn, RunnerPlan.Missing, opened = false))
    }

    @Test
    fun `a session that ended while away shows its completion rather than starting another`() {
        assertEquals(RunnerEntry.Complete(completed), runnerEntry(completed, runnable, opened = false))
    }

    @Test
    fun `only a runnable plan starts, and only once`() {
        assertEquals(RunnerEntry.Start, runnerEntry(RunnerView.Idle, runnable, opened = false))
        assertEquals(RunnerEntry.Waiting, runnerEntry(RunnerView.Idle, runnable, opened = true))
    }

    @Test
    fun `an unread library shows nothing yet`() {
        assertEquals(RunnerEntry.Loading, runnerEntry(RunnerView.Idle, null, opened = false))
    }

    @Test
    fun `a plan that can't start says why before any count-in`() {
        val tooLong = RunnerPlan.TooLong(runnable)
        assertEquals(RunnerEntry.Before(tooLong), runnerEntry(RunnerView.Idle, tooLong, opened = false))
        assertEquals(RunnerEntry.Before(RunnerPlan.RestWeek), runnerEntry(RunnerView.Idle, RunnerPlan.RestWeek, false))
        val empty = RunnerPlan.NoSteps(workout)
        assertEquals(RunnerEntry.Before(empty), runnerEntry(RunnerView.Idle, empty, opened = false))
        assertEquals(RunnerEntry.Before(RunnerPlan.Missing), runnerEntry(RunnerView.Idle, RunnerPlan.Missing, false))
    }

    @Test
    fun `Start anyway on a long workout waits on the service like any start`() {
        assertEquals(RunnerEntry.Waiting, runnerEntry(RunnerView.Idle, RunnerPlan.TooLong(runnable), opened = true))
    }

    @Test
    fun `back closes the pad, then Coming up, then the add picker, then the swap picker`() {
        val entry = RunnerEntry.Attach(running)
        val all = Overlay.entries.toSet()
        assertEquals(BackAction.Close(Overlay.PAD), backAction(entry, all))
        assertEquals(BackAction.Close(Overlay.UPCOMING), backAction(entry, all - Overlay.PAD))
        assertEquals(
            BackAction.Close(Overlay.ADD_PICKER),
            backAction(entry, setOf(Overlay.ADD_PICKER, Overlay.SWAP_PICKER)),
        )
        assertEquals(BackAction.Close(Overlay.SWAP_PICKER), backAction(entry, setOf(Overlay.SWAP_PICKER)))
    }

    @Test
    fun `back on a running session with nothing open asks to finish`() {
        assertEquals(BackAction.ConfirmFinish, backAction(RunnerEntry.Attach(running), emptySet()))
    }

    @Test
    fun `back before the session runs leaves with nothing written`() {
        assertEquals(BackAction.Leave, backAction(RunnerEntry.Attach(countingIn), emptySet()))
        assertEquals(BackAction.Leave, backAction(RunnerEntry.Before(RunnerPlan.RestWeek), emptySet()))
        assertEquals(BackAction.Leave, backAction(RunnerEntry.Waiting, emptySet()))
        assertEquals(BackAction.Leave, backAction(RunnerEntry.Loading, emptySet()))
    }

    @Test
    fun `back on completion is Done`() {
        assertEquals(BackAction.Done, backAction(RunnerEntry.Complete(completed), emptySet()))
    }

    @Test
    fun `an overlay closes itself once the step it opened on is left`() {
        val opened = RunnerOverlays().open(Overlay.SWAP_PICKER, visit = 3).open(Overlay.PAD, visit = 3)
        assertEquals(setOf(Overlay.SWAP_PICKER, Overlay.PAD), opened.on(visit = 3).open)
        assertEquals(emptySet<Overlay>(), opened.on(visit = 4).open)
        // Prev back onto the step it opened on doesn't reopen it.
        assertEquals(emptySet<Overlay>(), opened.on(visit = 4).on(visit = 3).open)
    }

    @Test
    fun `an overlay closes on its own, leaving the rest`() {
        val opened = RunnerOverlays().open(Overlay.UPCOMING, 1).open(Overlay.PAD, 1)
        assertEquals(setOf(Overlay.UPCOMING), opened.close(Overlay.PAD).open)
    }
}
