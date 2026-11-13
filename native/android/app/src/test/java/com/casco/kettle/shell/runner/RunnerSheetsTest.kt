package com.casco.kettle.shell.runner

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.casco.kettle.domain.RunnerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** −15s, the Swap sheet and End early (#417, part 2). In `pt`, as [RunnerScreenTest] is. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerSheetsTest : RunnerScreenHarness() {
    @Test
    fun `a rest's −15s takes fifteen seconds off, beside +30s`() {
        val reps = plan(block(squat))
        show(running(reps, started(reps).advanced()))
        button("−15s").assertIsTarget().performClick()
        assertEquals(RunnerCommand.AddRestSeconds(-15.0), sent.last())
        button("+30s").performClick()
        assertEquals(RunnerCommand.AddRestSeconds(30.0), sent.last())
    }

    @Test
    fun `the swap sheet groups its own type first, with each plan, and Keep closes it`() {
        show(running(plan(block(squat))))
        button("Trocar").performClick()
        compose.onNodeWithText("Pelo resto deste exercício. Seu treino continua como está.").assertExists()
        compose.onNodeWithText("Também de repetições").assertExists()
        assertTrue(compose.onAllNodesWithText("3 × 8 · 60s de descanso").fetchSemanticsNodes().isNotEmpty())
        button("Manter Agachamento").assertIsTarget().performClick()
        compose.onNodeWithText("Trocar Agachamento").assertDoesNotExist()
        assertTrue(sent.none { it is RunnerCommand.Swap })
    }

    @Test
    fun `another type's row says its type, and a group search empties is left out`() {
        show(running(plan(block(squat))))
        button("Trocar").performClick()
        compose.onNode(hasContentDescription("Buscar exercícios")).performTextInput("prancha")
        compose.onNodeWithText("Também de repetições").assertDoesNotExist()
        compose.onNodeWithText("Outros tipos").assertExists()
        compose.onNodeWithText("Isometria · 2 × 20–30s · 30s de descanso").assertExists()
        button("Prancha").performClick()
        assertEquals(RunnerCommand.Swap(plank), sent.last())
    }

    @Test
    fun `End early lists each exercise in its own unit, and Save and end finishes on the step it was asked on`() {
        val mixed = plan(block(grinder), block(squat), block(everyMinute))
        // The AMRAP logged, then squat's first set and its rest: the second set on screen.
        val state = started(mixed).advanced().advanced().advanced()
        show(running(mixed, state))
        button("Encerrar").performClick()
        compose.onNodeWithText("Encerrar o treino agora?").assertExists()
        compose.onNodeWithText("feito").assertExists()
        compose.onNodeWithText("1 de 2 séries").assertExists()
        compose.onNodeWithText("não iniciado").assertExists()
        button("Continuar").assertIsTarget()
        button("Salvar e encerrar").performClick()
        assertEquals(RunnerCommand.Finish(state.snapshot.visit), sent.last())
        compose.onNodeWithText("Encerrar o treino agora?").assertDoesNotExist()
    }

    @Test
    fun `an EMOM counts its minutes, and Back closes End early`() {
        val emom = plan(block(everyMinute))
        show(running(emom, started(emom).advanced().advanced()))
        button("Encerrar").performClick()
        compose.onNodeWithText("2 de 5 min").assertExists()
        back()
        compose.onNodeWithText("Encerrar o treino agora?").assertDoesNotExist()
        assertTrue(sent.none { it is RunnerCommand.Finish })
    }
}
