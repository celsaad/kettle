package com.casco.kettle.shell.runner

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.WorkoutBlock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The runner's header (#409): End at the top right, then each step's label line, its kind and where it
 * sits, and the rest's "Next" in the name's place. In `pt`, as [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
// A phone's size, as RunnerScreenTest's: the runner's screens don't scroll.
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerHeaderTest : RunnerScreenHarness() {
    @Test
    fun `each kind of step says what it is and where it sits`() {
        val labels =
            listOf(
                squat to "Reps · série 1 de 2",
                plank to "Isometria · série 1 de 2",
                burpees to "Esforço · rodada 1 de 2",
                everyMinute to "EMOM · minuto 1 de 5",
                grinder to "AMRAP",
                row to "Cardio",
            )
        for ((exercise, label) in labels) {
            show(running(plan(block(exercise))))
            compose.onNodeWithText(label).assertExists()
            compose.onNodeWithText(exercise.name).assertExists()
        }
    }

    @Test
    fun `inside a circuit the label line is the circuit's round`() {
        val circuit = WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember("squat"), CircuitMember("plank")))
        show(running(plan(circuit)))
        compose.onNodeWithText("Circuito · rodada 1 de 2").assertExists()
        compose.onNodeWithText("Reps · série 1 de 2").assertDoesNotExist()
    }

    @Test
    fun `a rest names what comes next in its header`() {
        show(running(plan(block(squat)), started(plan(block(squat))).advanced()))
        compose.onNodeWithText("Descanso").assertExists()
        compose.onNodeWithText("A seguir: Agachamento").assertExists()
        compose.onNodeWithText("PRÓXIMO").assertDoesNotExist()
    }

    @Test
    fun `End is a 48dp pill that asks first`() {
        show(running(plan(block(squat))))
        button("Encerrar").assertIsTarget().performClick()
        compose.onNode(isDialog()).assertExists()
    }
}
