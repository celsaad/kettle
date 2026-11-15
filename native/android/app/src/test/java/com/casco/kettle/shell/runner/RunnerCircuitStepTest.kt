package com.casco.kettle.shell.runner

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.WorkoutBlock
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A circuit's reps step as its board draws it (#478): no effort, no load for a bodyweight member, and the
 * last round's reps from round 2; and a paused AMRAP's Resume as its main action. In `pt`, as
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
        compose.onNodeWithText("Carga", substring = true).assertDoesNotExist()
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
        val loaded = started(circuit).advanced().after(RunnerCommand.SetWeight(10.0))
        show(running(circuit, loaded))
        compose.onNodeWithText("Carga", substring = true).assertExists()
        show(running(circuit, loaded.after(RunnerCommand.SetWeight(0.0))))
        compose.onNodeWithText("Carga", substring = true).assertExists()
    }

    @Test
    fun `from round 2 a circuit's step says what was done last round`() {
        show(running(circuit, started(circuit).advanced().advanced().advanced()))
        compose.onNodeWithText("Rodada 1: 8").assertExists()
    }

    /** Resume leads on a paused AMRAP, as on every paused screen (#421); the round goes to the side. */
    @Test
    fun `a paused AMRAP makes Resume the main action, the round to the side`() {
        val amrap = plan(block(grinder))
        show(running(amrap, started(amrap).after(RunnerCommand.TogglePause)))
        val resume = button("Continuar").getUnclippedBoundsInRoot()
        val round = button("Rodada 1 feita").getUnclippedBoundsInRoot()
        assertTrue(resume.right - resume.left > round.right - round.left)
    }
}
