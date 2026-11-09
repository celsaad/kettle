package com.casco.kettle.shell.nav

import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking

/**
 * `src/app/session-native.tsx`, the stub RN keeps under the native runner (#302): RN pushes and pops it
 * like any other route, and the shell draws the runner over it. Goes when routing moves native (#309).
 */
const val NATIVE_RUNNER_PATH = "/session-native"

/** Whether [path] is the runner: what the always-dark theme and anything else about it is keyed on. */
fun isRunnerPath(path: String?): Boolean = path == NATIVE_RUNNER_PATH

/**
 * The one way into the runner (#297), in every build since the flip release (#308). [open] is
 * `JsBridge.open`, a parameter so a test hears the push.
 */
fun openRunner(
    start: RunnerStart,
    open: (Stacking, String, Map<String, String>) -> Unit = JsBridge::open,
) = open(Stacking.Push, NATIVE_RUNNER_PATH, start.routeParams())

/** A start's params as the runner reads them back off its stub's route ([runnerStartFrom]). */
internal fun RunnerStart.routeParams(): Map<String, String> =
    when (this) {
        RunnerStart.Adhoc -> {
            mapOf(ADHOC to "1")
        }

        is RunnerStart.Planned -> {
            when (val params = params) {
                is SessionParams.Workout -> {
                    mapOf(WORKOUT_ID to params.workoutId)
                }

                // Left out rather than empty, as expo-router drops RN's undefined `day`.
                is SessionParams.ProgramSlot -> {
                    mapOf(PROGRAM_ID to params.programId, WEEK to params.week.toString()) +
                        listOfNotNull(params.day?.let { DAY to it })
                }
            }
        }
    }

/** The start [params] were pushed with, read back from RN's report of the route; null when none. */
fun runnerStartFrom(params: Map<String, String>): RunnerStart? {
    val workoutId = params[WORKOUT_ID]
    val programId = params[PROGRAM_ID]
    val week = params[WEEK]?.toLongOrNull()
    return when {
        params[ADHOC] == "1" -> {
            RunnerStart.Adhoc
        }

        workoutId != null -> {
            RunnerStart.Planned(SessionParams.Workout(workoutId))
        }

        programId != null && week != null -> {
            RunnerStart.Planned(
                SessionParams.ProgramSlot(programId, week, params[DAY]),
            )
        }

        else -> {
            null
        }
    }
}

private const val ADHOC = "adhoc"
private const val WORKOUT_ID = "workoutId"
private const val PROGRAM_ID = "programId"
private const val WEEK = "week"
private const val DAY = "day"
