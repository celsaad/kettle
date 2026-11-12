package com.casco.kettle.shell.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.weightUnit

/**
 * An exercise's target in one line, its weight in the reader's [units]: RN's `exerciseSummary`. Zeros
 * read as absent, as RN's truthiness reads them, so a 0 kg target and a 0 m distance say nothing.
 *
 * A port, not a `:shared` selector with conformance cases: the words come from the same locale
 * bundles, so only which string and which numbers can drift, and RN's copy has one reader left, the
 * workout editor, which M5 ports (#242). The two share no code, so change both until then.
 */
@Composable
fun exerciseSummary(
    config: ExerciseConfig,
    units: UnitSystem,
): String =
    when (config) {
        is ExerciseConfig.Hiit -> {
            stringResource(R.string.summary_hiit, jsNumber(config.restSec), config.rounds, jsNumber(config.workSec))
        }

        is ExerciseConfig.Reps -> {
            repsSummary(config, units)
        }

        is ExerciseConfig.TimedHold -> {
            holdSummary(config)
        }

        is ExerciseConfig.Emom -> {
            stringResource(R.string.summary_emom, jsNumber(config.intervalSec), jsNumber(config.totalMinutes))
        }

        is ExerciseConfig.Amrap -> {
            stringResource(R.string.summary_amrap, jsNumber(config.timeCapSec))
        }

        is ExerciseConfig.Cardio -> {
            cardioSummary(config)
        }

        is ExerciseConfig.Rest -> {
            stringResource(R.string.summary_restSeconds, jsNumber(config.durationSec))
        }
    }

/** " · 20 kg" in the reader's [units], or nothing for no weight: also the Home preview's (#280). */
@Composable
internal fun weightSuffix(
    kg: Double?,
    units: UnitSystem,
): String =
    if (kg == null || kg == 0.0) {
        ""
    } else {
        stringResource(
            R.string.summary_repsWeight,
            stringResource(weightUnit(units)),
            jsNumber(toDisplayWeight(kg, units)),
        )
    }

@Composable
private fun repsSummary(
    config: ExerciseConfig.Reps,
    units: UnitSystem,
): String {
    val weight = weightSuffix(config.targetWeightKg, units)
    return stringResource(
        R.string.summary_reps,
        rangeLabel(config.targetRepsMin.toDouble(), config.targetRepsMax?.toDouble()),
        jsNumber(config.restSec),
        config.sets,
        weight,
    )
}

@Composable
private fun holdSummary(config: ExerciseConfig.TimedHold): String {
    val min = config.holdSecMin
    // A max-effort hold has no duration to show, and "3 × s" is what the shared string reads without one.
    return if (min == null) {
        stringResource(R.string.summary_holdOpen, jsNumber(config.restSec), config.sets)
    } else {
        stringResource(R.string.summary_hold, rangeLabel(min, config.holdSecMax), jsNumber(config.restSec), config.sets)
    }
}

@Composable
private fun cardioSummary(config: ExerciseConfig.Cardio): String {
    val meters = config.distanceMeters
    return if (meters != null && meters != 0.0) {
        stringResource(R.string.summary_cardioDistance, jsNumber(meters))
    } else {
        stringResource(R.string.summary_cardioDuration, jsNumber(config.durationSec ?: 0.0))
    }
}

private fun rangeLabel(
    min: Double,
    max: Double?,
): String = if (max == null || max == 0.0) jsNumber(min) else "${jsNumber(min)}–${jsNumber(max)}"
