package com.casco.kettle.shell.runner

import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerEffect
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerTransition
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The ongoing notification and the step-end alert (#296): what each says, and the session controls on
 * the ongoing one (docs/watch-remote-plan.md), as a function of the runner's state. The copy is checked
 * in `pt`, where a hardcoded English string can't pass; the names are the user's, verbatim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class RunnerNotificationsTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources

    private fun running(
        transition: RunnerTransition,
        vararg exercises: Exercise = arrayOf(PULLUPS),
        logged: LoggedUnit? = null,
    ) = RunnerView.Running(
        planOf(*exercises),
        transition.state.snapshot,
        transition.state.session,
        logged = logged,
    )

    private fun content(view: RunnerView.Active) = ongoingContent(resources, view)

    private fun RunnerTransition.then(
        command: RunnerCommand,
        at: Long = T0 + 5000,
    ) = reduceRunner(state, command, at)

    private fun RunnerTransition.advanced(at: Long = T0 + 5000) = then(RunnerCommand.Advance(state.snapshot.visit), at)

    private fun OngoingContent.labels() = actions.map { it.label }

    @Test
    fun `leads the count-in with getting ready and the first exercise, under the workout's name`() {
        val view = RunnerView.CountingIn(planOf(PULLUPS), null, CountIn(T0))

        assertEquals(
            OngoingContent("Prepare-se", "Pull-ups", "Morning", Chronometer(T0 + 3000, countDown = true)),
            content(view),
        )
    }

    @Test
    fun `gives a reps set what Log set will log, the set in the chip, and no Back on the first step`() {
        val first = content(running(started(PULLUPS)))

        assertEquals("Pull-ups", first.title)
        assertEquals("Reps · série 1 de 3 · 20 kg · 6 repetições", first.text)
        assertEquals("Morning", first.subText)
        assertNull(first.chronometer)
        assertEquals("Série 1/3", first.shortText)
        assertEquals(listOf(SessionAction("Registrar série", SessionControl.Advance)), first.actions)
    }

    @Test
    fun `follows an input edited on the phone, since Log set logs it`() {
        val edited = started(PULLUPS).then(RunnerCommand.SetReps(8)).then(RunnerCommand.SetWeight(22.5))

        assertEquals("Reps · série 1 de 3 · 22.5 kg · 8 repetições", content(running(edited)).text)
    }

    @Test
    fun `names what comes after a rest, with Undo for the set just logged and +30s`() {
        val rest = content(running(started(PULLUPS).advanced(), logged = LoggedUnit.Round(1)))

        assertEquals("Descanso", rest.title)
        assertEquals("A seguir: Pull-ups", rest.text)
        assertEquals(listOf("Pular descanso", "Desfazer", "+30s"), rest.labels())
        assertEquals(
            listOf(SessionControl.Advance, SessionControl.Prev, SessionControl.AddRest),
            rest.actions.map { it.control },
        )
    }

    @Test
    fun `says Back, not Undo, where nothing was just logged`() {
        assertEquals(listOf("Pular descanso", "Voltar", "+30s"), content(running(started(PULLUPS).advanced())).labels())
    }

    @Test
    fun `gives a hold, a HIIT round and cardio Done, and their position`() {
        val hold = content(running(started(PLANK), PLANK))
        assertEquals("Isometria · série 1 de 2", hold.text)
        assertEquals(listOf("Concluir"), hold.labels())

        val round = content(running(started(BURPEES), BURPEES))
        assertEquals("Esforço · rodada 1 de 2", round.text)
        assertEquals(listOf("Concluir"), round.labels())

        val walk = content(running(started(WALK), WALK))
        assertNull(walk.text)
        assertEquals(listOf("Concluir"), walk.labels())
    }

    /** The screen's EMOM Done only rests out the minute; an advance from the card would start the next. */
    @Test
    fun `gives an EMOM minute Back alone, never an advance`() {
        val first = started(SWINGS)
        assertEquals("EMOM · minuto 1 de 2 · 10 repetições", content(running(first, SWINGS)).text)
        assertEquals(emptyList<SessionAction>(), content(running(first, SWINGS)).actions)

        val second = first.advanced()
        assertEquals(listOf(SessionAction("Voltar", SessionControl.Prev)), content(running(second, SWINGS)).actions)
    }

    @Test
    fun `counts an AMRAP's rounds so far, and its button sets the next one`() {
        val amrap = started(GRINDER)
        assertEquals("0 rodadas", content(running(amrap, GRINDER)).text)
        assertEquals(
            listOf(SessionAction("Rodada 1 feita", SessionControl.Rounds(1))),
            content(running(amrap, GRINDER)).actions,
        )

        val three = amrap.then(RunnerCommand.SetRounds(3))
        assertEquals("3 rodadas", content(running(three, GRINDER)).text)
        assertEquals(SessionControl.Rounds(4), content(running(three, GRINDER)).actions.first().control)
    }

    @Test
    fun `stops the clock while paused, says so in the chip, and offers Resume alone`() {
        val paused = content(running(started(PLANK).then(RunnerCommand.TogglePause, T0 + 2000), PLANK))

        assertEquals("Plank", paused.title)
        assertEquals("Pausado", paused.text)
        assertEquals("Pausado", paused.shortText)
        assertNull(paused.chronometer)
        assertEquals(listOf(SessionAction("Continuar", SessionControl.Resume)), paused.actions)
    }

    @Test
    fun `aims every card at its session and the step it shows`() {
        val set = started(PULLUPS)
        val rest = set.advanced()

        assertEquals(SessionAim(set.state.session.id, set.state.snapshot.visit), content(running(set)).aim)
        assertEquals(SessionAim(rest.state.session.id, rest.state.snapshot.visit), content(running(rest)).aim)
    }

    @Test
    fun `names an ad-hoc session, and offers nothing while nothing is queued`() {
        val plan = RunnerPlan.Runnable(null, RUNNER_EXERCISES, emptyList())
        val start = startRunner(plan, null, T0)

        assertEquals(
            OngoingContent("Nada na fila", null, "Sessão avulsa", null),
            content(RunnerView.Running(plan, start.state.snapshot, start.state.session)),
        )
    }

    @Test
    fun `counts a rest down to its end, +30s included`() {
        val logged = started(PULLUPS).advanced()
        val longer = logged.then(RunnerCommand.AddRestSeconds(30.0, logged.state.snapshot.visit))

        assertEquals(Chronometer(T0 + 5000 + 120_000, countDown = true), content(running(longer)).chronometer)
    }

    @Test
    fun `counts a countdown interval down`() {
        assertEquals(
            Chronometer(T0 + 40_000, countDown = true),
            content(running(started(BURPEES), BURPEES)).chronometer,
        )
    }

    @Test
    fun `counts a hold and count-up cardio up, as the screen does`() {
        assertEquals(Chronometer(T0, countDown = false), content(running(started(PLANK), PLANK)).chronometer)
        assertEquals(Chronometer(T0, countDown = false), content(running(started(WALK), WALK)).chronometer)
    }

    @Test
    fun `leaves out paused time from the clock`() {
        val resumed =
            started(PLANK)
                .then(RunnerCommand.TogglePause, T0 + 2000)
                .then(RunnerCommand.TogglePause, T0 + 12_000)

        assertEquals(Chronometer(T0 + 10_000, countDown = false), content(running(resumed, PLANK)).chronometer)
    }

    @Test
    fun `titles the alert by the kind of step that ended`() {
        assertEquals("Descanso concluído", alertTitle(resources, StepEndTitle.REST))
        assertEquals("Isometria concluída", alertTitle(resources, StepEndTitle.HOLD))
        assertEquals("Intervalo concluído", alertTitle(resources, StepEndTitle.INTERVAL))
        assertEquals("Tempo esgotado", alertTitle(resources, StepEndTitle.TIME_UP))
    }

    @Test
    fun `names what comes next, skipping a rest, or says the workout is complete`() {
        val hiit = started(BURPEES, PULLUPS)
        val workEnded = hiit.then(RunnerCommand.TimeUpdate(hiit.state.snapshot.visit), T0 + 40_000)
        val ended = workEnded.effects.filterIsInstance<RunnerEffect.StepEnded>().single()

        assertEquals(
            StepEndAlert(StepEndTitle.INTERVAL, "Burpees"),
            stepEndAlert(ended.step, ended.next, workEnded.state.snapshot),
        )
        assertEquals("Burpees", alertBody(resources, StepEndAlert(StepEndTitle.INTERVAL, "Burpees")))
        assertEquals("Treino concluído", alertBody(resources, StepEndAlert(StepEndTitle.TIME_UP, null)))
    }
}
