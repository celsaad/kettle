package com.casco.kettle.shell.nav

import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking

/** `src/app/session.tsx`, RN's runner. */
const val RUNNER_PATH = "/session"

/** `src/app/session-native.tsx`, the stub RN keeps under the native runner (#302). */
const val NATIVE_RUNNER_PATH = "/session-native"

/**
 * Whether [path] is a runner, RN's or native's: what the always-dark theme and anything else about the
 * runner is keyed on, so the two can't drift apart while both exist.
 */
fun isRunnerPath(path: String?): Boolean = path == RUNNER_PATH || path == NATIVE_RUNNER_PATH

/**
 * The one way into a runner (#297): the native one behind its stub while [nativeRunner] is on, as on
 * `.dev`, and RN's otherwise, as on Play until the flip release (#308). Both read the same params.
 * [open] is `JsBridge.open`, a parameter so a test hears the push.
 */
fun openRunner(
    start: RunnerStart,
    open: (Stacking, String, Map<String, String>) -> Unit = JsBridge::open,
    nativeRunner: Boolean = NATIVE_RUNNER,
) = open(Stacking.Push, if (nativeRunner) NATIVE_RUNNER_PATH else RUNNER_PATH, start.routeParams())

/** A start's params as the runner's route reads them, `src/app/session.tsx`'s. */
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
