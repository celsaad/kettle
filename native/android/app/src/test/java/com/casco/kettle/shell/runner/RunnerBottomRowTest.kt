package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.RunnerCommand
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The runner's bottom row (#409) on each step screen, and the rest's Undo: what each slot holds and
 * sends, driven in `pt` as [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
// A phone's size: the runner's screens don't scroll, as RN's don't, so on Robolectric's default
// 320x470dp the controls at the bottom are drawn off the screen and take no taps.
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerBottomRowTest : RunnerScreenHarness() {
    @Test
    fun `a reps step shows its set and target, and its steppers send the reps`() {
        show(running(plan(block(squat))))
        // Once: a work screen has no Next card since #409.
        assertEquals(1, compose.onAllNodesWithText("Agachamento").fetchSemanticsNodes().size)
        compose.onNodeWithText("Série 1 de 2 · meta 5").assertExists()
        button("Aumentar Repetições").performClick()
        assertEquals(RunnerCommand.SetReps(6), sent.last())
        button("Registrar série → Descanso").performClick()
        assertEquals(RunnerCommand.Advance(FIRST_VISIT), sent.last())
        button("Voltar").assertIsTarget().performClick()
        assertEquals(RunnerCommand.Prev, sent.last())
    }

    @Test
    fun `a hold's row is Back, Pause in the middle, and Done to the side`() {
        show(running(plan(block(plank))))
        assertEquals(1, compose.onAllNodesWithText("Prancha").fetchSemanticsNodes().size)
        button("Pausar").performClick()
        button("Voltar").performClick()
        button("Concluir").assertIsTarget().performClick()
        assertEquals(listOf(RunnerCommand.TogglePause, RunnerCommand.Prev, RunnerCommand.Advance(FIRST_VISIT)), sent)
    }

    @Test
    fun `an interval's main button is Pause, with Skip to the side, or Done for a count-up`() {
        show(running(plan(block(burpees))))
        button("Pausar").assertExists()
        button("Pular").assertIsTarget().performClick()
        assertEquals(RunnerCommand.Advance(FIRST_VISIT), sent.last())
        show(running(plan(block(row))))
        button("Pausar").assertExists()
        button("Concluir").assertExists()
        button("Pular").assertDoesNotExist()
    }

    @Test
    fun `a rest has +30s and Skip, and no Pause`() {
        val resting = started(plan(block(squat))).advanced()
        show(running(plan(block(squat)), resting))
        compose.onNodeWithText("de 1:30 restantes").assertExists()
        button("Pausar").assertDoesNotExist()
        button("+30s").performClick()
        assertEquals(RunnerCommand.AddRestSeconds(30.0), sent.last())
        button("Pular descanso →").performClick()
        assertEquals(RunnerCommand.Advance(resting.snapshot.visit), sent.last())
    }

    @Test
    fun `straight after a set the rest names it with Undo, and has no Back beside it`() {
        show(running(plan(block(squat)), started(plan(block(squat))).advanced()))
        compose.onNodeWithText("série 1 · 5 × 60 kg").assertExists()
        button("Voltar").assertDoesNotExist()
        button("Desfazer").assertIsTarget().performClick()
        assertEquals(RunnerCommand.Prev, sent.last())
    }

    @Test
    fun `a rest with nothing to undo has Back in Undo's place`() {
        show(running(plan(block(squat)), started(plan(block(squat))).advanced(), restored = true))
        button("Desfazer").assertDoesNotExist()
        compose.onNodeWithText("Registrado").assertDoesNotExist()
        button("Voltar").performClick()
        assertEquals(RunnerCommand.Prev, sent.last())
    }

    // A resume after process death lands paused (#307), and a rest has no Pause of its own: frozen with
    // nothing to unfreeze it but Skip, as it was on the S25+.

    @Test
    fun `a paused rest makes Resume the main action, and Skip moves to the side`() {
        val paused = started(plan(block(squat))).advanced().after(RunnerCommand.TogglePause)
        show(running(plan(block(squat)), paused))
        button("Continuar").performClick()
        assertEquals(RunnerCommand.TogglePause, sent.last())
        button("Pular descanso →").assertDoesNotExist()
        button("Pular").performClick()
        assertEquals(RunnerCommand.Advance(paused.snapshot.visit), sent.last())
    }
}
