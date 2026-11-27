package com.casco.kettle.shell.runner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.WorkoutBlock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A circuit's reps step as its board draws it (#478): no effort, no load row for a bodyweight member until
 * it adds one, and the last round's reps from round 2; and a paused AMRAP's slots. In `pt`, as
 * [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerCircuitStepTest : RunnerScreenHarness() {
    /** A loaded squat and a bodyweight move, two rounds, no rests: squat, move, squat, move. */
    private val circuit =
        plan(WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember(squat.id), CircuitMember(extras[0].id))))

    @Test
    fun `a circuit's bodyweight member has no load and no effort, and its first round no last round`() {
        show(running(circuit, started(circuit).advanced()))
        compose.onNodeWithText("Búlgaro").assertExists()
        // "Carga" as a word: the pill's "Adicionar carga" is lower case.
        compose.onNodeWithText("Carga", substring = true).assertDoesNotExist()
        button("Aumentar carga").assertDoesNotExist()
        compose.onNodeWithText("Esforço", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Rodada 1:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a circuit's loaded member keeps its load`() {
        show(running(circuit))
        compose.onNodeWithText("Carga", substring = true).assertExists()
        compose.onNodeWithText("Esforço", substring = true).assertDoesNotExist()
    }

    /** Set to 0 again, the row stays for the visit rather than leave under the finger. */
    @Test
    fun `a bodyweight member given a load shows it, and keeps it at 0`() {
        val loaded = started(circuit).advanced().withWeight(10.0)
        show(running(circuit, loaded))
        compose.onNodeWithText("Carga", substring = true).assertExists()
        show(running(circuit, loaded.withWeight(0.0)))
        compose.onNodeWithText("Carga", substring = true).assertExists()
    }

    @Test
    fun `from round 2 a circuit's step says what was done last round`() {
        show(running(circuit, started(circuit).advanced().advanced().advanced()))
        compose.onNodeWithText("Rodada 1: 8").assertExists()
    }

    /**
     * The toggle keeps its slot on a paused AMRAP, and the round keeps the main one (#521): when Resume
     * led instead (#478), a quick second tap where Pause had been logged a round. Native graphics: legacy
     * mode measures every word under the slot's 64dp minimum, so "Pausar" and "Continuar" would fit alike
     * there even if the slot followed its current word.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a paused AMRAP's Resume is where Pause was, and the round stays the main action`() {
        val amrap = plan(block(grinder))
        show(running(amrap))
        val pause = button("Pausar").getUnclippedBoundsInRoot()
        val running = button("Rodada 1 feita").getUnclippedBoundsInRoot()

        show(running(amrap, started(amrap).after(RunnerCommand.TogglePause)))
        assertEquals(pause, button("Continuar").getUnclippedBoundsInRoot())
        assertEquals(running, button("Rodada 1 feita").getUnclippedBoundsInRoot())
        button("Continuar").performClick()
        assertEquals(RunnerCommand.TogglePause, sent.last())
    }

    /** In `ja` Pause is the wider word, so a slot sized for its current word shrank on the tap (#521). */
    @Test
    @Config(qualifiers = "ja-w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Japanese at a larger text size the AMRAP's toggle keeps its slot too`() {
        fontScale = 1.3f
        val amrap = plan(block(grinder))
        show(running(amrap))
        val pause = button("一時停止").getUnclippedBoundsInRoot()
        val running = button("1ラウンド完了").getUnclippedBoundsInRoot()

        show(running(amrap, started(amrap).after(RunnerCommand.TogglePause)))
        assertEquals(pause, button("再開").getUnclippedBoundsInRoot())
        assertEquals(running, button("1ラウンド完了").getUnclippedBoundsInRoot())
    }

    /**
     * "Rodada 9 feita" fits beside Back and Pause on a 360dp phone at 110% text; "Rodada 10 feita" didn't,
     * so the tap that reached round 10 restacked the row and put Back and Pause under the finger (#566).
     */
    @Test
    @Config(qualifiers = "pt-w360dp-h800dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the AMRAP's row doesn't restack on the tap that reaches round 10`() {
        fontScale = 1.1f
        val amrap = plan(block(grinder))
        val start = started(amrap)
        show(running(amrap, start.after(RunnerCommand.SetRounds(8, start.snapshot.visit))))
        val nine = button("Rodada 9 feita").getUnclippedBoundsInRoot()
        val pause = button("Pausar").getUnclippedBoundsInRoot()

        show(running(amrap, start.after(RunnerCommand.SetRounds(9, start.snapshot.visit))))
        assertEquals(nine, button("Rodada 10 feita").getUnclippedBoundsInRoot())
        assertEquals(pause, button("Pausar").getUnclippedBoundsInRoot())
    }

    /**
     * The header scrolls with the step on a short screen at a large text size (#521), so each step has to
     * start at the top: sharing one scroll, the next member arrived with its label and name scrolled off.
     */
    @Test
    @Config(qualifiers = "pt-w320dp-h480dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `on a short screen the next member starts at the top, not where the last was scrolled`() {
        fontScale = 2f
        val first = started(circuit)
        show(running(circuit, first))
        compose.onNodeWithText("Agachamento").performTouchInput { swipeUp() }
        compose.onNodeWithText("Circuito · rodada 1 de 2").assertIsNotDisplayed()

        show(running(circuit, first.advanced()))
        compose.onNodeWithText("Circuito · rodada 1 de 2").assertIsDisplayed()
        compose.onNodeWithText("Búlgaro").assertIsDisplayed()
    }

    /**
     * With no load planned or set, a bodyweight member offers "Add load" in the row's place (#521): a vest
     * couldn't be added from the runner without an earlier load to adopt. A tap shows the stepper.
     */
    @Test
    fun `a bodyweight member can add a load, which shows the stepper`() {
        val member = started(circuit).advanced()
        show(running(circuit, member))
        button("Aumentar carga").assertDoesNotExist()
        button("Adicionar carga").assertIsTarget().performClick()
        button("Adicionar carga").assertDoesNotExist()
        button("Aumentar carga").performClick()
        assertEquals(RunnerCommand.SetWeight(2.5, member.snapshot.visit), sent.last())
    }
}
