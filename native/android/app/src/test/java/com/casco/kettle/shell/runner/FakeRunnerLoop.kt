package com.casco.kettle.shell.runner

import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerTransition
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.buildSteps
import com.casco.kettle.domain.startRunner

/**
 * [RunnerLoop] on a virtual clock: a post runs at once, a delayed one when [advanceBy] reaches it.
 * [now] is the wall clock the runner reads, so a test moves both together.
 */
internal class FakeRunnerLoop(
    var now: Long = T0,
) : RunnerLoop {
    private class Pending(
        val at: Long,
        val block: () -> Unit,
    ) {
        var cancelled = false
    }

    private val pending = mutableListOf<Pending>()

    val scheduled: Int get() = pending.count { !it.cancelled }

    private val held = mutableListOf<() -> Unit>()
    private var holding = false

    override fun post(block: () -> Unit) {
        if (holding) held += block else block()
    }

    /**
     * Queues every post until [release], as the runner's thread does with one sent from the main thread
     * while a timer's block is due ahead of it. Timers still fire on [advanceBy].
     */
    fun hold() {
        holding = true
    }

    fun release() {
        holding = false
        held.toList().also { held.clear() }.forEach { it() }
    }

    override fun postDelayed(
        delayMs: Long,
        block: () -> Unit,
    ): Cancellable {
        val entry = Pending(now + delayMs, block)
        pending += entry
        return Cancellable { entry.cancelled = true }
    }

    /** Loses every timer not yet run, as one end wake was lost on a device (#306), leaving the clock alone. */
    fun dropPending() = pending.clear()

    fun advanceBy(ms: Long) {
        val end = now + ms
        while (true) {
            val next = pending.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
            pending -= next
            now = next.at
            next.block()
        }
        now = end
    }
}

internal const val T0 = 1_785_142_800_000L // 2026-07-27T09:00:00Z

internal val PLANK =
    Exercise("plank", "Plank", ExerciseConfig.TimedHold(2, holdSecMin = 15.0, holdSecMax = 25.0, restSec = 30.0))
internal val PULLUPS = Exercise("pullups", "Pull-ups", ExerciseConfig.Reps(3, 6, targetWeightKg = 20.0, restSec = 90.0))
internal val BURPEES =
    Exercise("burpees", "Burpees", ExerciseConfig.Hiit(workSec = 40.0, restSec = 20.0, rounds = 2))
internal val ROW = Exercise("row", "Row", ExerciseConfig.Cardio(durationSec = 60.0))
internal val WALK = Exercise("walk", "Walk", ExerciseConfig.Cardio())
internal val GRINDER = Exercise("grinder", "Grinder", ExerciseConfig.Amrap(timeCapSec = 300.0))
internal val SWINGS =
    Exercise("swings", "KB Swings", ExerciseConfig.Emom(intervalSec = 60.0, totalMinutes = 2.0, targetReps = 10))
internal val RUNNER_EXERCISES = listOf(PLANK, PULLUPS, BURPEES, ROW, WALK, GRINDER, SWINGS)

internal fun workoutOf(vararg exercises: Exercise) =
    Workout("w", "Morning", exercises.map { WorkoutBlock.ExerciseBlock(it.id) })

internal fun planOf(vararg exercises: Exercise): RunnerPlan.Runnable {
    val workout = workoutOf(*exercises)
    return RunnerPlan.Runnable(workout, RUNNER_EXERCISES, buildSteps(workout, RUNNER_EXERCISES))
}

/** A session started at [now] on [exercises], one block each. */
internal fun started(
    vararg exercises: Exercise,
    now: Long = T0,
): RunnerTransition = startRunner(planOf(*exercises), null, now)

/** Whether a state file has been written and not since deleted, for whichever session. */
internal fun RunnerStateFile.written(): Boolean = dir.listFiles().orEmpty().any { sessionIdOfStateFile(it) != null }
