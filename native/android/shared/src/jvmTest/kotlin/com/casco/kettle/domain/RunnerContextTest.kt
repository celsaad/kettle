package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** What a reps set opens on from last time: the reps held to the range, the load floored at the target. */
class RunnerContextTest {
    private val squat =
        Exercise(
            "squat",
            "Squat",
            ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, targetRepsMax = 12, targetWeightKg = 20.0, restSec = 60.0),
        )
    private val pushups =
        Exercise(
            "pushups",
            "Push-ups",
            ExerciseConfig.Reps(sets = 2, targetRepsMin = 8, targetRepsMax = 15, restSec = 60.0),
        )
    private val fixed = Exercise("fixed", "Fixed", ExerciseConfig.Reps(sets = 2, targetRepsMin = 5, restSec = 60.0))
    private val exercises = RUNNER_EXERCISES + squat + pushups + fixed

    /** A finished session that logged [sets] of [exerciseId], as reps to kilograms (null for bodyweight). */
    private fun last(
        exerciseId: String,
        vararg sets: Pair<Long, Double?>,
    ) = finishedSession(
        "last",
        SessionEntry.Reps(exerciseId, sets.map { (reps, kg) -> RepsSetLog(reps, kg, restTakenSec = 60.0) }),
    )

    private fun runner(
        vararg prior: Session,
        exerciseId: String = "squat",
        units: UnitSystem = UnitSystem.METRIC,
        programLoads: Set<String> = emptySet(),
    ) = Runner(
        workoutOf(single(exerciseId)),
        exercises,
        context = RunnerContext(prior.toList(), units, programLoads),
    )

    /** Each set's reps and load as it opened, logging each as it stands and skipping the rests. */
    private fun Runner.openings(beforeLogging: Runner.(setIndex: Long) -> Unit = {}): List<Pair<Long, Double>> {
        val opened = mutableListOf<Pair<Long, Double>>()
        while (!state.finished) {
            (step as? RunnerStep.Reps)?.let {
                opened += inputs.reps to inputs.weightKg
                beforeLogging(it.setIndex)
            }
            advance()
        }
        return opened
    }

    @Test
    fun `opens each set on last time's reps for that set, held to the range`() {
        val prior = last("squat", 10L to 20.0, 14L to 20.0, 6L to 20.0)

        assertEquals(listOf(10L, 12L, 8L), runner(prior).openings().map { it.first })
    }

    @Test
    fun `falls back to last time's last set when it logged fewer`() {
        val prior = last("squat", 11L to 20.0)

        assertEquals(listOf(11L, 11L, 11L), runner(prior).openings().map { it.first })
    }

    @Test
    fun `opens on the bottom of the range with no history, and a fixed target never moves`() {
        assertEquals(listOf(8L, 8L, 8L), runner().openings().map { it.first })
        assertEquals(
            listOf(5L, 5L),
            runner(last("fixed", 9L to null), exerciseId = "fixed").openings().map { it.first },
        )
    }

    @Test
    fun `opens on last time's load when it was above the target`() {
        val prior = last("squat", 10L to 25.0, 10L to 25.0, 10L to 25.0)

        assertEquals(listOf(10L to 25.0, 10L to 25.0, 10L to 25.0), runner(prior).openings())
    }

    @Test
    fun `floors the load at a raised target, and starts the reps over since it went up`() {
        val prior = last("squat", 11L to 15.0, 11L to 15.0, 11L to 15.0)

        assertEquals(listOf(8L to 20.0, 8L to 20.0, 8L to 20.0), runner(prior).openings())
    }

    @Test
    fun `opens on the program week's load, not last time's`() {
        val prior = last("squat", 10L to 25.0, 10L to 25.0, 10L to 25.0)

        // Lighter than last time is no reason to start the reps over.
        assertEquals(
            listOf(10L to 20.0, 10L to 20.0, 10L to 20.0),
            runner(prior, programLoads = setOf("squat")).openings(),
        )
    }

    @Test
    fun `carries a load changed today over the program's and last time's, starting the reps over when heavier`() {
        val prior = last("squat", 10L to 20.0, 10L to 20.0, 10L to 20.0)
        val opened =
            runner(prior, programLoads = setOf("squat")).openings { set ->
                if (set == 1L) weight(30.0)
            }

        assertEquals(listOf(10L to 20.0, 8L to 30.0, 8L to 30.0), opened)
    }

    @Test
    fun `compares the load as the user reads it, so pounds aren't heavier than themselves`() {
        // 53.5 lb typed on the pad stores as 24.27 kg; last time's 24.26 kg also reads 53.5 lb.
        val typed = fromDisplayWeight(53.5, UnitSystem.IMPERIAL)
        val prior = last("squat", 10L to 24.26, 10L to 24.26, 10L to 24.26)
        val carryTyped: Runner.(Long) -> Unit = { set -> if (set == 1L) weight(typed) }

        assertEquals(10L, runner(prior, units = UnitSystem.IMPERIAL).openings(carryTyped)[1].first)
        // In kilograms 24.27 is heavier, which is the reset the imperial reading must not make.
        assertEquals(8L, runner(prior, units = UnitSystem.METRIC).openings(carryTyped)[1].first)
    }

    @Test
    fun `counts loading a bodyweight exercise as going up`() {
        val prior = last("pushups", 12L to null, 12L to null)
        val opened =
            runner(prior, exerciseId = "pushups").openings { set ->
                if (set == 1L) weight(5.0)
            }

        assertEquals(listOf(12L to 0.0, 8L to 5.0), opened)
    }

    @Test
    fun `opens a circuit's round on last time's set of the same number`() {
        val prior = last("squat", 9L to 20.0, 11L to 20.0)
        val runner =
            Runner(
                workoutOf(circuitOf("squat", "dips", rounds = 2)),
                exercises,
                context = RunnerContext(listOf(prior)),
            )
        val squats = mutableListOf<Long>()
        while (!runner.state.finished) {
            (runner.step as? RunnerStep.Reps)?.takeIf { it.exerciseId == "squat" }?.let { squats += runner.inputs.reps }
            runner.advance()
        }

        assertEquals(listOf(9L, 11L), squats)
    }

    @Test
    fun `seeds a set the rest ran out into during a kill from last time`() {
        val prior = last("squat", 10L to 20.0, 11L to 20.0, 11L to 20.0)
        val context = RunnerContext(listOf(prior))
        val runner = runner(prior)
        runner.advance()
        val prompt = RunnerRecovery.Prompt(runner.snapshot, runner.written)

        val resumed = resumeRunner(prompt, runner.now + 3_600 * MS, context).state.snapshot

        assertEquals(2L, (resumed.step as RunnerStep.Reps).setIndex)
        assertEquals(11L, resumed.inputs.reps)
    }
}
