package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
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
    fun `after a swap the sheet counts the substitute's sets, as the header does`() {
        // Set 1 of 2 of the squat logged, its rest, then the rest of the member swapped for another.
        val swapped = started(plan(block(squat))).advanced().advanced().after(RunnerCommand.Swap(extras[0]))
        show(running(plan(block(squat)), swapped))
        compose.onNodeWithText("Reps · série 1 de 1").assertExists()
        button("Séries").performClick()
        // One set, on screen: the squat's isn't listed under the substitute's name, and nothing planned
        // the substitute, so no "planned" and no footer.
        compose.onNodeWithText("1 série nesta sessão").assertExists()
        compose.onNodeWithText("Registrada").assertDoesNotExist()
        compose.onNodeWithText("Agora").assertExists()
        compose.onNodeWithText("Só esta sessão muda", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an ad-hoc session has no plan to count against, so no planned, footer or added`() {
        val adhoc = RunnerPlan.Runnable(null, exercises, emptyList())
        val added = started(adhoc).after(RunnerCommand.AddExercise(squat), RunnerCommand.AddSet)
        show(running(adhoc, added))
        button("Séries").performClick()
        compose.onNodeWithText("3 séries nesta sessão").assertExists()
        compose.onNodeWithText("Só esta sessão muda", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Adicionada nesta sessão").assertDoesNotExist()
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
