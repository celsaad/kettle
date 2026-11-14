package com.casco.kettle.shell.workouteditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.wholeSeconds
import com.casco.kettle.shell.R
import com.casco.kettle.shell.library.exerciseSummary
import com.casco.kettle.shell.library.exerciseTarget
import com.casco.kettle.shell.library.rangeLabel
import com.casco.kettle.shell.library.weightSuffix
import com.casco.kettle.shell.runner.formatClock

// What a block's row says under its name (#440), in m:ss where it's a time. Reps and holds are written
// here; the timed types keep the library's own line, which isn't this ticket's to reword.

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

        is ExerciseConfig.TimedHold -> {
            stringResource(R.string.workoutEditor_targetHold, holdText(config), config.sets) +
                restSuffix(config.restSec)
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
            config.holdSecMin?.let { stringResource(R.string.workoutEditor_memberHold, holdText(config)) }
                ?: stringResource(R.string.workoutEditor_memberHoldOpen)
        }

        else -> {
            exerciseSummary(config, units)
        }
    }

/** "0:20–0:45", or "0:30" for a fixed hold, or "max hold" for none. */
@Composable
private fun holdText(config: ExerciseConfig.TimedHold): String {
    val min = config.holdSecMin ?: return stringResource(R.string.workoutEditor_memberHoldOpen)
    val max = config.holdSecMax?.takeIf { it != 0.0 }
    return if (max == null) clock(min) else "${clock(min)}–${clock(max)}"
}

/** A time as the picker shows it, to the whole second it rounds to: a file's 74.6 s reads 1:15 on both. */
private fun clock(seconds: Double): String = formatClock(wholeSeconds(seconds).toDouble())

/** " · rest 1:15", or nothing for no rest. */
@Composable
private fun restSuffix(seconds: Double): String =
    if (seconds > 0) " · " + stringResource(R.string.workoutEditor_restSuffix, clock(seconds)) else ""
