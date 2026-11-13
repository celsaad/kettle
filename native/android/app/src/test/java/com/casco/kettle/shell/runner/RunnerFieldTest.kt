package com.casco.kettle.shell.runner

import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.StepClock
import com.casco.kettle.domain.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which field the runner draws on (#418): what the step's clock is doing, not the exercise type. */
class RunnerFieldTest {
    private fun clock(
        endSec: Double = 0.0,
        pausedAt: Long? = null,
    ) = StepClock(phaseStartedAt = 0, pausedAt = pausedAt, pausedMs = 0, stepEndSec = endSec)

    private val reps = RunnerStep.Reps(0, "k", "squat", "Squat", 8, 12, null, 1, 3, null)

    private fun hold(endSec: Double?) = RunnerStep.Hold(0, "k", "plank", "Plank", 20.0, 45.0, endSec, 1, 3, null)

    private fun interval(
        variant: IntervalVariant,
        countUp: Boolean = false,
    ) = RunnerStep.Interval(0, "k", "x", "X", variant, 40.0, countUp, 1, 4, notes = null)

    private val rest = RunnerStep.Rest(0, "k", "rest", standalone = false, seconds = 60.0)

    @Test
    fun `a step only a tap ends is yours to pace`() {
        assertEquals(RunnerField.PACE, stepField(reps, clock(), now = 5_000))
        assertEquals(RunnerField.PACE, stepField(hold(endSec = null), clock(), now = 5_000))
        assertEquals(RunnerField.PACE, stepField(interval(IntervalVariant.CARDIO, countUp = true), clock(), 5_000))
    }

    @Test
    fun `a step the clock ends is work, whatever its type`() {
        assertEquals(RunnerField.WORK, stepField(hold(endSec = 45.0), clock(45.0), now = 5_000))
        for (variant in IntervalVariant.entries) {
            assertEquals("$variant", RunnerField.WORK, stepField(interval(variant), clock(40.0), now = 5_000))
        }
    }

    @Test
    fun `anything paused is pace, a rest included`() {
        val paused = clock(60.0, pausedAt = 58_000)
        assertEquals(RunnerField.PACE, stepField(hold(endSec = 45.0), paused, now = 59_000))
        assertEquals(RunnerField.PACE, stepField(interval(IntervalVariant.HIIT), paused, now = 59_000))
        assertEquals(RunnerField.PACE, stepField(rest, paused, now = 59_000))
    }

    @Test
    fun `a rest gets ready on the second its ticks start`() {
        // 60s, so whole seconds 57 to 59 are the three with a tick (the engine's TICK_SECONDS).
        assertEquals(RunnerField.REST, stepField(rest, clock(60.0), now = 0))
        assertEquals(RunnerField.REST, stepField(rest, clock(60.0), now = 56_999))
        assertEquals(RunnerField.GET_READY, stepField(rest, clock(60.0), now = 57_000))
        assertEquals(RunnerField.GET_READY, stepField(rest, clock(60.0), now = 59_999))
    }

    @Test
    fun `the count-in gets ready, and every screen around a session is pace`() {
        val plan = RunnerPlan.Runnable(Workout("w", "W", emptyList()), emptyList(), emptyList())
        assertEquals(
            RunnerField.GET_READY,
            runnerField(RunnerEntry.Attach(RunnerView.CountingIn(plan, null, CountIn(0))), now = 1_000),
        )
        for (entry in listOf(RunnerEntry.Loading, RunnerEntry.Start, RunnerEntry.Waiting, RunnerEntry.Before(plan))) {
            assertEquals("$entry", RunnerField.PACE, runnerField(entry, now = 1_000))
        }
    }
}
