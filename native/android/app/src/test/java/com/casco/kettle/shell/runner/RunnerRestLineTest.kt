package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.buildSteps
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A rest's words (#473): the line under "Next" as the board writes it, "Set 2 of 4 · 48.5 lb · 8–12 reps",
 * with the load the set will open on; and in a circuit, the round where a set read as a second set of
 * the same move. In `pt`, as [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerRestLineTest : RunnerScreenHarness() {
    private fun text(text: String) = compose.onNodeWithText(text)

    @Test
    fun `a rest names the next set's position, its load and its target`() {
        val squats = plan(block(squat))
        show(running(squats, started(squats).advanced()))
        text("Série 2 de 2 · 60 kg · 5 repetições").assertExists()
    }

    /** The set opens on the load just logged, not the plan's: the rest says the same. */
    @Test
    fun `a rest names the load carried from the set just logged`() {
        val squats = plan(block(squat))
        show(running(squats, started(squats).withWeight(70.0).advanced()))
        text("Série 2 de 2 · 70 kg · 5 repetições").assertExists()
    }

    @Test
    fun `a set with no load names none`() {
        val unloaded = plan(block(extras[0]))
        show(running(unloaded, started(unloaded).advanced()))
        text("Série 2 de 3 · 8 repetições").assertExists()
    }

    @Test
    fun `in a circuit a rest says the round, both of what's next and of what was logged`() {
        val circuit =
            plan(
                WorkoutBlock.Circuit(
                    rounds = 2,
                    restBetweenExercisesSec = 30.0,
                    restBetweenRoundsSec = 60.0,
                    members = listOf(CircuitMember(squat.id), CircuitMember(extras[0].id)),
                ),
            )
        // The squat logged in round 1, then its rest, before the second member's round 1.
        show(running(circuit, started(circuit).advanced()))
        text("Rodada 1 de 2 · 8 repetições").assertExists()
        text("Registrado Agachamento · rodada 1 · 5 × 60 kg").assertExists()
        compose.onNodeWithText("Série", substring = true).assertDoesNotExist()
    }

    /** After a circuit's last round no circuit rest follows; a Rest block's still says whose round was logged. */
    @Test
    fun `after a circuit, a Rest block still says the round just logged`() {
        val circuitThenRest =
            plan(
                WorkoutBlock.Circuit(rounds = 1, members = listOf(CircuitMember(squat.id))),
                block(rest),
                block(plank),
            )
        show(running(circuitThenRest, started(circuitThenRest).advanced()))
        text("Registrado Agachamento · rodada 1 · 5 × 60 kg").assertExists()
    }

    /** A single target is a count: "1 repetição", where a hard-coded noun said "1 repetições". */
    @Test
    fun `a single target takes the count's own plural`() {
        val single = Exercise("single", "Levantamento", ExerciseConfig.Reps(2, 1, null, 100.0, 120.0))
        show(
            running(
                planWith(listOf(single), block(single)),
                started(planWith(listOf(single), block(single))).advanced(),
            ),
        )
        text("Série 2 de 2 · 100 kg · 1 repetição").assertExists()
    }

    /** An interval's line is its own, unchanged: inside a circuit a HIIT member runs all its rounds per visit. */
    @Test
    fun `a HIIT's rest names its own round, in a circuit too`() {
        show(running(plan(block(burpees)), started(plan(block(burpees))).advanced()))
        text("HIIT · rodada 2 de 2").assertExists()

        val circuit = plan(WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember(burpees.id))))
        show(running(circuit, started(circuit).advanced()))
        text("HIIT · rodada 2 de 2").assertExists()
    }

    private fun planWith(
        more: List<Exercise>,
        vararg blocks: WorkoutBlock,
    ): RunnerPlan.Runnable {
        val workout = Workout("w", "Pernas", blocks.toList())
        val all = exercises + more
        return RunnerPlan.Runnable(workout, all, buildSteps(workout, all))
    }
}
