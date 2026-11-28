package com.casco.kettle.shell.workouteditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.library.exerciseSummary
import com.casco.kettle.shell.library.exerciseTarget
import com.casco.kettle.shell.library.rangeLabel
import com.casco.kettle.shell.library.timeRange
import com.casco.kettle.shell.library.weightSuffix
import com.casco.kettle.shell.runner.timeText

// What a block's row says under its name (#440), in m:ss where it's a time, as every timed line is
// (#462). Reps and holds are written here; the timed types take the library's own line.

/** A plain block's target and its rest between sets: "3 × 6–12 · 48.5 lb · rest 1:15". */
@Composable
internal fun blockTarget(
    config: ExerciseConfig,
    units: UnitSystem,
): String =
    when (config) {
        is ExerciseConfig.Reps -> {
            exerciseTarget(config, units) + restSuffix(config.restSec)
        }

        // "hold", as a circuit's member line says it (#456): "3 × 0:20" alone read as a time of anything.
        is ExerciseConfig.TimedHold -> {
            val min = config.holdSecMin
            val hold =
                if (min == null) {
                    stringResource(R.string.workoutEditor_targetHoldOpen, config.sets)
                } else {
                    stringResource(R.string.workoutEditor_targetHold, timeRange(min, config.holdSecMax), config.sets)
                }
            hold + restSuffix(config.restSec)
        }

        else -> {
            exerciseSummary(config, units)
        }
    }

/**
 * A circuit exercise's target, "8–15 reps" or "hold 0:20–0:45": no sets and no rest, since a circuit
 * runs each exercise once a round and rests on its own terms.
 */
@Composable
internal fun memberTarget(
    config: ExerciseConfig,
    units: UnitSystem,
): String =
    when (config) {
        is ExerciseConfig.Reps -> {
            stringResource(
                R.string.format_repsList,
                rangeLabel(config.targetRepsMin.toDouble(), config.targetRepsMax?.toDouble()),
            ) + weightSuffix(config.targetWeightKg, units)
        }

        is ExerciseConfig.TimedHold -> {
            config.holdSecMin?.let {
                stringResource(
                    R.string.workoutEditor_memberHold,
                    timeRange(it, config.holdSecMax),
                )
            }
                ?: stringResource(R.string.workoutEditor_memberHoldOpen)
        }

        else -> {
            exerciseSummary(config, units)
        }
    }

/** " · rest 1:15", or nothing for no rest. */
@Composable
private fun restSuffix(seconds: Double): String =
    if (seconds > 0) " · " + stringResource(R.string.workoutEditor_restSuffix, timeText(seconds)) else ""
