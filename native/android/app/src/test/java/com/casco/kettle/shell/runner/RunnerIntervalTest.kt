package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
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
        button("Aumentar REPS NESTE MINUTO").performClick()
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
        compose.onNodeWithText("rodada 2").assertExists()
        button("Desfazer").performClick()
        assertEquals(RunnerCommand.SetRounds(1), sent.last())
    }

    @Test
    fun `the extra reps come only in the cap's last minute`() {
        val amrap = plan(block(grinder))
        show(running(amrap))
        button("Aumentar REPS NA RODADA 1").assertDoesNotExist()
        // Ten minutes capped down to one, as a minute left on the clock.
        show(running(amrap, started(amrap).after(RunnerCommand.AddRestSeconds(-540.0))))
        button("Aumentar REPS NA RODADA 1").performClick()
        assertEquals(RunnerCommand.SetExtraReps(1), sent.last())
    }
}
