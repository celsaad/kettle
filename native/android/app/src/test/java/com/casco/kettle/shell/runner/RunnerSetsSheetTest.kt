package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.RunnerCommand
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Sets sheet (#410): the sets this session, and adding or removing one, in `pt`. */
@RunWith(RobolectricTestRunner::class)
// A phone's size, as RunnerScreenTest's: the runner's screens don't scroll.
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerSetsSheetTest : RunnerScreenHarness() {
    @Test
    fun `Sets opens the sheet with the set on screen, then the planned, and Add a set`() {
        show(running(plan(block(squat))))
        button("Séries").performClick()
        compose.onNodeWithText("2 séries nesta sessão · planejadas 2").assertExists()
        compose.onNodeWithText("Agora").assertExists()
        compose.onNodeWithText("Planejada").assertExists()
        compose.onNodeWithText("Só esta sessão muda. Seu treino continua com 2 séries.").assertExists()
        // The last planned set can go, down to the one on screen, as the −/+ allowed.
        button("Remover").assertExists()
        button("Adicionar série").assertIsTarget().performClick()
        assertEquals(RunnerCommand.AddSet, sent.last())
    }

    @Test
    fun `an added set says so, and the last planned set is the one Remove takes`() {
        val added = started(plan(block(squat))).after(RunnerCommand.AddSet)
        show(running(plan(block(squat)), added))
        button("Séries").performClick()
        compose.onNodeWithText("3 séries nesta sessão · planejadas 2").assertExists()
        compose.onNodeWithText("Adicionada nesta sessão").assertExists()
        button("Remover").assertIsTarget().performClick()
        assertEquals(RunnerCommand.DropSet, sent.last())
    }

    @Test
    fun `a logged set shows what was logged, and Remove never reaches it or the one on screen`() {
        // Set 1 logged, its rest, set 2 on screen: nothing planned after it to remove.
        val second = started(plan(block(squat))).advanced().advanced()
        show(running(plan(block(squat)), second))
        button("Séries").performClick()
        compose.onNodeWithText("5 × 60 kg").assertExists()
        compose.onNodeWithText("Registrada").assertExists()
        compose.onNodeWithText("Agora").assertExists()
        button("Remover").assertDoesNotExist()
    }

    @Test
    fun `Done and back close the sheet`() {
        show(running(plan(block(squat))))
        button("Séries").performClick()
        button("Concluir").performClick()
        compose.onNodeWithText("Agora").assertDoesNotExist()
        button("Séries").performClick()
        back()
        compose.onNodeWithText("Agora").assertDoesNotExist()
    }
}
