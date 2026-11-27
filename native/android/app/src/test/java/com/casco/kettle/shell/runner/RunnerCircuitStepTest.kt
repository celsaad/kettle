package com.casco.kettle.shell.runner

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.WorkoutBlock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
     * led instead (#478), a quick second tap where Pause had been logged a round.
     */
    @Test
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
