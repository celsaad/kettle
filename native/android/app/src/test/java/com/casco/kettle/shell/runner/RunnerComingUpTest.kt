package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.WorkoutBlock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Coming up as a subway map (#415). In `pt`, as [RunnerScreenTest] is. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerComingUpTest : RunnerScreenHarness() {
    @Test
    fun `a done exercise reads as one line of what was logged, and the next is the stop on screen`() {
        val two = plan(block(squat), block(plank))
        // Both squat sets, the second at 7 reps, and the rest between: the plank on screen.
        val state =
            started(two)
                .advanced()
                .advanced()
                .after(RunnerCommand.SetReps(7))
                .advanced()
        show(running(two, state))
        button("Pernas").performClick()
        compose.onNodeWithText("2 de 4 etapas concluídas").assertExists()
        compose.onNodeWithText("Agachamento · 2 séries · 5, 7").assertExists()
        compose.onNodeWithText("Prancha").assertExists()
        compose.onNodeWithText("Fim do treino").assertExists()
    }

    @Test
    fun `the circuit you're in is a loop with its round, its rests, each member and the rounds after`() {
        val circuit =
            WorkoutBlock.Circuit(
                rounds = 3,
                restBetweenExercisesSec = null,
                restBetweenRoundsSec = 60.0,
                members = listOf(CircuitMember(squat.id), CircuitMember(plank.id)),
            )
        val loop = plan(circuit)
        show(running(loop, started(loop).advanced()))
        button("Pernas").performClick()
        compose.onNodeWithText("Circuito · rodada 1 de 3").assertExists()
        compose.onNodeWithText("Sem descanso entre os exercícios · 1:00 após cada rodada").assertExists()
        compose.onNodeWithText("Agachamento · 5 repetições").assertExists()
        compose.onNodeWithText("Agora").assertExists()
        compose.onNodeWithText("Depois, rodadas 2–3").assertExists()
    }

    @Test
    fun `on a rest between moves the next move is Now, and no round rest leaves only the moves`() {
        val circuit =
            WorkoutBlock.Circuit(
                rounds = 2,
                restBetweenExercisesSec = 15.0,
                members = listOf(CircuitMember(squat.id), CircuitMember(plank.id)),
            )
        val loop = plan(circuit)
        // The squat logged: the rest before the plank on screen.
        show(running(loop, started(loop).advanced()))
        button("Pernas").performClick()
        compose.onNodeWithText("0:15 entre os exercícios").assertExists()
        compose.onNodeWithText("Agora").assertExists()
    }

    @Test
    fun `an exercise that logged nothing is ticked off without a count of work it didn't do`() {
        val two = plan(block(squat), block(plank))
        // Both squat sets at 0 reps, which logs no entry: the exercise skipped.
        val zero = RunnerCommand.SetReps(0)
        val state =
            started(two)
                .after(zero)
                .advanced()
                .advanced()
                .after(zero)
                .advanced()
        show(running(two, state))
        button("Pernas").performClick()
        compose.onNodeWithText("Agachamento").assertExists()
        compose.onNodeWithText("Agachamento · 2 séries", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a rest block is a tick on the line`() {
        val rested = plan(block(squat), block(rest), block(plank))
        show(running(rested))
        button("Pernas").performClick()
        compose.onNodeWithText("Descanso 1:00").assertExists()
    }
}
