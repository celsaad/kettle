package com.casco.kettle.shell.runner

import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerTransition
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The ongoing notification and the step-end alert (#296): what each says, as a function of the
 * runner's state, so the watch remote can add actions to it later (#300). The names are the user's
 * and come through verbatim.
 */
@RunWith(RobolectricTestRunner::class)
class RunnerNotificationsTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources

    private fun running(transition: RunnerTransition) =
        RunnerView.Running(planOf(PULLUPS), transition.state.snapshot, transition.state.session)

    private fun content(view: RunnerView.Active) = ongoingContent(resources, view)

    @Test
    fun `counts the count-in down under the workout's name`() {
        val view = RunnerView.CountingIn(planOf(PULLUPS), null, CountIn(T0))

        assertEquals(OngoingContent("Morning", "Get ready", Chronometer(T0 + 3000, countDown = true)), content(view))
    }

    @Test
    fun `gives a reps step its set position and no clock`() {
        assertEquals(OngoingContent("Morning", "Pull-ups · Set 1 of 3", null), content(running(started(PULLUPS))))
    }

    @Test
    fun `counts a rest down to its end, +30s included`() {
        val logged =
            started(PULLUPS).let {
                reduceRunner(
                    it.state,
                    RunnerCommand.Advance(it.state.snapshot.visit),
                    T0 + 5000,
                )
            }
        val longer = reduceRunner(logged.state, RunnerCommand.AddRestSeconds(30.0), T0 + 5000)

        assertEquals(
            OngoingContent("Morning", "Rest", Chronometer(T0 + 5000 + 120_000, countDown = true)),
            content(running(longer)),
        )
    }

    @Test
    fun `counts a countdown interval down`() {
        assertEquals(Chronometer(T0 + 40_000, countDown = true), content(running(started(BURPEES))).chronometer)
    }

    @Test
    fun `counts a hold and count-up cardio up, as the screen does`() {
        assertEquals(
            OngoingContent("Morning", "Plank", Chronometer(T0, countDown = false)),
            content(running(started(PLANK))),
        )
        assertEquals(Chronometer(T0, countDown = false), content(running(started(WALK))).chronometer)
    }

    @Test
    fun `leaves out paused time from the clock`() {
        val paused = started(PLANK).let { reduceRunner(it.state, RunnerCommand.TogglePause, T0 + 2000) }
        val resumed = reduceRunner(paused.state, RunnerCommand.TogglePause, T0 + 12_000)

        assertEquals(Chronometer(T0 + 10_000, countDown = false), content(running(resumed)).chronometer)
    }

    @Test
    fun `stops the clock and says so while paused`() {
        val paused = started(PLANK).let { reduceRunner(it.state, RunnerCommand.TogglePause, T0 + 2000) }

        assertEquals(OngoingContent("Morning", "Plank · Paused", null), content(running(paused)))
    }

    @Test
    fun `names an ad-hoc session, and says when nothing is queued`() {
        val plan = RunnerPlan.Runnable(null, RUNNER_EXERCISES, emptyList())
        val start = startRunner(plan, null, T0)

        assertEquals(
            OngoingContent("Ad-hoc session", "Nothing queued", null),
            content(RunnerView.Running(plan, start.state.snapshot, start.state.session)),
        )
    }

    @Test
    fun `titles the alert by the kind of step that ended`() {
        assertEquals("Rest complete", alertTitle(resources, StepEndTitle.REST))
        assertEquals("Hold complete", alertTitle(resources, StepEndTitle.HOLD))
        assertEquals("Interval complete", alertTitle(resources, StepEndTitle.INTERVAL))
        assertEquals("Time's up", alertTitle(resources, StepEndTitle.TIME_UP))
    }

    @Test
    fun `names what comes next, skipping a rest, or says the workout is complete`() {
        val hiit = started(BURPEES, PULLUPS)
        val workEnded = reduceRunner(hiit.state, RunnerCommand.TimeUpdate(hiit.state.snapshot.visit), T0 + 40_000)
        val ended = workEnded.effects.filterIsInstance<com.casco.kettle.domain.RunnerEffect.StepEnded>().single()

        assertEquals(
            StepEndAlert(StepEndTitle.INTERVAL, "Burpees"),
            stepEndAlert(ended.step, ended.next, workEnded.state.snapshot),
        )
        assertEquals("Burpees", alertBody(resources, StepEndAlert(StepEndTitle.INTERVAL, "Burpees")))
        assertEquals("Workout complete", alertBody(resources, StepEndAlert(StepEndTitle.TIME_UP, null)))
    }

    @Test
    @Config(qualifiers = "pt")
    fun `speaks the user's language around the user's names`() {
        val paused = started(PLANK).let { reduceRunner(it.state, RunnerCommand.TogglePause, T0 + 2000) }

        assertEquals("Plank · Pausado", content(running(paused)).text)
        assertEquals("Pull-ups · Série 1 de 3", content(running(started(PULLUPS))).text)
        assertEquals("Tempo esgotado", alertTitle(resources, StepEndTitle.TIME_UP))
        assertEquals("Treino concluído", alertBody(resources, StepEndAlert(StepEndTitle.TIME_UP, null)))
    }
}
