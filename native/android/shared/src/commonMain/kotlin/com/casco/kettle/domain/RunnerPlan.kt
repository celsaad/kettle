package com.casco.kettle.domain

/**
 * What a start resolves to before the first step, as RN's `session.tsx` decided it before its
 * count-in (§3.1 of #294's runner inventory). One function so the UI, which picks the screen from
 * it, and the service, which owns the plan from it, can't disagree (#297). Only [Runnable] starts a
 * session.
 */
sealed interface RunnerPlan {
    /**
     * A session to run. [workout] is null only for an ad-hoc session, whose [steps] start empty and
     * grow as the user picks. [exercises] is the library's, with a program week's overrides applied,
     * which the swap and add pickers offer. [weightSetBy] is the exercises whose load the program week
     * sets, where the runner doesn't suggest one of its own (#410).
     */
    data class Runnable(
        val workout: Workout?,
        val exercises: List<Exercise>,
        val steps: List<RunnerStep>,
        val weightSetBy: Set<String> = emptySet(),
    ) : RunnerPlan

    /**
     * A workout the step ceiling cuts short, said before the count-in rather than logged as finished
     * partway. "Start anyway" runs [plan], which is what was built.
     */
    data class TooLong(
        val plan: Runnable,
    ) : RunnerPlan

    /** A workout with no runnable steps: no blocks, or every block names a missing exercise. */
    data class NoSteps(
        val workout: Workout,
    ) : RunnerPlan

    /** The program week is a scheduled day off, which isn't broken and says so. */
    data object RestWeek : RunnerPlan

    /** An id that resolves to nothing: no such workout, program or week. */
    data object Missing : RunnerPlan
}

/**
 * Resolves [start] against [library]. A null [start] is a link with no params, which runs the
 * library's first workout, as RN's fallback does. Program weeks go through [resolveWorkoutForWeek], so
 * their overrides reach both the steps and the exercises the pickers offer.
 *
 * One deliberate difference from RN: a start with nothing to fall back on, an empty library, is
 * [RunnerPlan.Missing]. RN's runner took the absent workout for an ad-hoc session, which no one asked for.
 */
fun resolveRunnerPlan(
    start: RunnerStart?,
    library: Library,
): RunnerPlan {
    val resolved: Resolved =
        when (start) {
            RunnerStart.Adhoc -> {
                return RunnerPlan.Runnable(null, library.exercises, emptyList())
            }

            is RunnerStart.Planned -> {
                resolvePlanned(start.params, library)
            }

            null -> {
                library.workouts.firstOrNull()?.let { Resolved.Found(it, library.exercises) } ?: Resolved.Missing
            }
        }
    return when (resolved) {
        Resolved.RestWeek -> RunnerPlan.RestWeek
        Resolved.Missing -> RunnerPlan.Missing
        is Resolved.Found -> planFor(resolved.workout, resolved.exercises, resolved.weightSetBy)
    }
}

private sealed interface Resolved {
    data class Found(
        val workout: Workout,
        val exercises: List<Exercise>,
        val weightSetBy: Set<String> = emptySet(),
    ) : Resolved

    data object RestWeek : Resolved

    data object Missing : Resolved
}

private fun resolvePlanned(
    params: SessionParams,
    library: Library,
): Resolved =
    when (params) {
        is SessionParams.Workout -> {
            library.workouts.firstOrNull { it.id == params.workoutId }?.let { Resolved.Found(it, library.exercises) }
                ?: Resolved.Missing
        }

        is SessionParams.ProgramSlot -> {
            val program = library.programs.firstOrNull { it.id == params.programId }
            // Asked apart from the resolution, which returns null for a rest week and a missing one alike.
            val week = program?.let { findProgramWeek(it, params.week, params.day) }
            val resolved = program?.let { resolveWorkoutForWeek(it, params.week, library, params.day) }
            when {
                week is ProgramWeek.RestDay -> Resolved.RestWeek
                resolved != null -> Resolved.Found(resolved.workout, resolved.exercises, resolved.weightSetBy)
                else -> Resolved.Missing
            }
        }
    }

private fun planFor(
    workout: Workout,
    exercises: List<Exercise>,
    weightSetBy: Set<String>,
): RunnerPlan {
    val built = buildStepsWithLimits(workout, exercises)
    val plan = RunnerPlan.Runnable(workout, exercises, built.steps, weightSetBy)
    return when {
        built.steps.isEmpty() -> RunnerPlan.NoSteps(workout)
        built.truncated -> RunnerPlan.TooLong(plan)
        else -> plan
    }
}
