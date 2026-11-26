package com.casco.kettle.shell.runner

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.buildSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EMOM's Done and the AMRAP's round button (#417). In `pt`, as [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerIntervalTest : RunnerScreenHarness() {
    /** A finished session that logged [entry]. */
    private fun finished(entry: SessionEntry) =
        Session(1, "old", "w", null, null, null, "2026-08-01T10:00:00.000Z", "2026-08-01T10:30:00.000Z", listOf(entry))

    @Test
    fun `an EMOM minute's Done turns the screen to rest, sending nothing, and Skip then logs it`() {
        show(running(plan(block(everyMinute))))
        button("Pular").assertDoesNotExist()
        button("Concluir").assertIsTarget().performClick()
        // The screen's word alone: the engine hears nothing until the minute ends or Skip logs it.
        assertTrue(sent.isEmpty())
        compose.onNodeWithText("EMOM · minuto 1 de 5 · descansando").assertExists()
        compose.onNodeWithText("Minuto 2 começa em").assertExists()
        button("Concluir").assertDoesNotExist()
        // The reps stay editable while it rests.
        button("Aumentar Reps neste minuto").performClick()
        assertEquals(RunnerCommand.SetReps(11), sent.last())
        button("Pular").performClick()
        assertEquals(RunnerCommand.Advance(FIRST_VISIT), sent.last())
    }

    @Test
    fun `a minute called done is forgotten once the minute turns`() {
        val emom = plan(block(everyMinute))
        show(running(emom))
        button("Concluir").performClick()
        show(running(emom, started(emom).advanced()))
        compose.onNodeWithText("EMOM · minuto 2 de 5").assertExists()
        button("Concluir").assertExists()
    }

    @Test
    fun `an AMRAP logs a round with its main button, and Pause is to the side, with no Skip`() {
        show(running(plan(block(grinder))))
        compose.onNodeWithText("AMRAP · rodada 1").assertExists()
        compose.onNodeWithText("0 rodadas").assertExists()
        button("Pular").assertDoesNotExist()
        button("Desfazer").assertDoesNotExist()
        button("Pausar").assertIsTarget()
        button("Rodada 1 feita").performClick()
        assertEquals(RunnerCommand.SetRounds(1), sent.last())
    }

    @Test
    fun `Undo takes back only the last round, and the pace line reads last time`() {
        prior = listOf(finished(SessionEntry.Amrap(grinder.id, 7, null)))
        val amrap = plan(block(grinder))
        show(running(amrap, started(amrap).after(RunnerCommand.SetRounds(2))))
        compose.onNodeWithText("AMRAP · rodada 3").assertExists()
        compose.onNodeWithText("2 rodadas · última vez 7").assertExists()
        compose.onNodeWithText("Registrado rodada 2").assertExists()
        button("Desfazer").performClick()
        assertEquals(RunnerCommand.SetRounds(1), sent.last())
    }

    // A round logged is work Save and end writes, so the AMRAP on screen isn't "not started" (#520).
    @Test
    fun `End's sheet counts an AMRAP with a round logged as done`() {
        val amrap = plan(block(grinder))
        show(running(amrap, started(amrap).after(RunnerCommand.SetRounds(1))))
        button("Encerrar").performClick()
        compose.onNodeWithText("feito").assertExists()
        compose.onNodeWithText("não iniciado").assertDoesNotExist()
    }

    @Test
    fun `End's sheet still says an AMRAP with nothing logged is not started`() {
        show(running(plan(block(grinder))))
        button("Encerrar").performClick()
        compose.onNodeWithText("não iniciado").assertExists()
    }

    // A session of intervals alone counts rounds, as Sessions' row says since #483 (#520).
    @Test
    fun `the receipt of an AMRAP-only session counts rounds`() {
        val session = finished(SessionEntry.Amrap(grinder.id, 3, null))
        show(RunnerEntry.Complete(RunnerView.Completed(plan(block(grinder)), session, FinishBackupState.OK)))
        compose.onNode(hasText("3") and hasText("Rodadas")).assertExists()
        compose.onNodeWithText("Séries").assertDoesNotExist()
    }

    @Test
    fun `the extra reps come only in the cap's last minute`() {
        val amrap = plan(block(grinder))
        show(running(amrap))
        button("Aumentar Reps na rodada 1").assertDoesNotExist()
        // Ten minutes capped down to one, as a minute left on the clock.
        show(running(amrap, started(amrap).let { it.after(RunnerCommand.AddRestSeconds(-540.0, it.snapshot.visit)) }))
        button("Aumentar Reps na rodada 1").performClick()
        assertEquals(RunnerCommand.SetExtraReps(1), sent.last())
    }

    /** While a minute rests out its notes go (#477): the countdown to the next minute is what matters then. */
    @Test
    fun `an EMOM's notes show while it works, and go while the minute rests out`() {
        val noted = Exercise("noted", "Swing anotado", ExerciseConfig.Emom(60.0, 5.0, 10), notes = "Quadril para trás")
        val workout = Workout("w", "Pernas", listOf(block(noted)))
        val all = exercises + noted
        val emom = RunnerPlan.Runnable(workout, all, buildSteps(workout, all))
        show(running(emom))
        compose.onNodeWithText("Quadril para trás").assertExists()
        button("Concluir").performClick()
        compose.onNodeWithText("Quadril para trás").assertDoesNotExist()
    }
}
