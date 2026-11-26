package com.casco.kettle.shell.runner

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.RunnerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(compose.onAllNodesWithText("3 × 8").fetchSemanticsNodes().isNotEmpty())
        // A loaded candidate leaves its load out too (#479): the bench's 60 kg wrapped names at 1×.
        compose.onNodeWithText("2 × 8–12").assertExists()
        compose.onNodeWithText("60 kg", substring = true).assertDoesNotExist()
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
        compose.onNodeWithText("Isometria · 2 × 20–30 s").assertExists()
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

    /**
     * Every sheet's grabber (#474): a button named by its word, whose TalkBack click, dismiss and collapse
     * each close the sheet, as the shell's sheet's do. The sheet runs the screen's width, where Swap and
     * End early were inset.
     */
    @Test
    fun `a sheet runs the full width, and its grabber's TalkBack actions each close it`() {
        for (action in listOf(SemanticsActions.OnClick, SemanticsActions.Dismiss, SemanticsActions.Collapse)) {
            show(running(plan(block(squat))))
            button("Encerrar").performClick()
            val grabber = compose.onNodeWithContentDescription("Alça de arrastar")
            grabber.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            val strip = grabber.getUnclippedBoundsInRoot()
            assertEquals((root.right - root.left).value, (strip.right - strip.left).value, 1f)

            grabber.performSemanticsAction(action)
            compose.onNodeWithText("Encerrar o treino agora?").assertDoesNotExist()
        }
    }

    /** A finger's tap on the grabber does nothing, as on the shell's: a drag closes, never a stray tap. */
    @Test
    fun `a tap on the grabber leaves the sheet open`() {
        show(running(plan(block(squat))))
        button("Trocar").performClick()
        compose.onNodeWithContentDescription("Alça de arrastar").performTouchInput { click() }
        compose.onNodeWithText("Trocar Agachamento").assertExists()
    }

    @Test
    fun `a long drag down the grabber closes the sheet, and a short one springs back`() {
        show(running(plan(block(squat))))
        button("Encerrar").performClick()
        val grabber = compose.onNodeWithContentDescription("Alça de arrastar")
        val before = grabber.getUnclippedBoundsInRoot().top
        grabber.performTouchInput { swipeDown(startY = centerY, endY = centerY + 30.dp.toPx(), durationMillis = 600) }
        compose.waitForIdle()
        compose.onNodeWithText("Encerrar o treino agora?").assertExists()
        assertEquals(before.value, grabber.getUnclippedBoundsInRoot().top.value, 1f)

        grabber.performTouchInput { swipeDown(startY = centerY, endY = centerY + 200.dp.toPx(), durationMillis = 600) }
        compose.onNodeWithText("Encerrar o treino agora?").assertDoesNotExist()
    }

    /**
     * A flick closes the sheet however short, as the shell's sheet's does: 80dp in 60ms is about 1300dp/s,
     * short of the 96dp a drag needs. A shorter swipe gave the velocity tracker too few events to read.
     */
    @Test
    fun `a quick flick on the grabber closes the sheet, short of the drag that would`() {
        show(running(plan(block(squat))))
        button("Encerrar").performClick()
        compose
            .onNodeWithContentDescription("Alça de arrastar")
            .performTouchInput { swipeDown(startY = centerY, endY = centerY + 80.dp.toPx(), durationMillis = 60) }
        compose.onNodeWithText("Encerrar o treino agora?").assertDoesNotExist()
    }

    @Test
    fun `a sheet open over the orange field lights the bar icons, and closing it darkens them again`() {
        show(running(plan(block(everyMinute))))
        assertTrue(runnerBars.darkIcons)
        button("Encerrar").performClick()
        compose.waitForIdle()
        assertFalse(runnerBars.darkIcons)
        back()
        assertTrue(runnerBars.darkIcons)
    }
}
