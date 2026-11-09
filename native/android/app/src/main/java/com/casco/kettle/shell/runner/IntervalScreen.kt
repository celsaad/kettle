package com.casco.kettle.shell.runner

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

/**
 * HIIT, EMOM, AMRAP and cardio. A countdown's Done is a quiet "Skip", since its clock ends it; a
 * count-up's is the filled button, being the only way out.
 */
@Composable
internal fun IntervalScreen(
    step: RunnerStep.Interval,
    context: StepContext,
) {
    val resources = LocalResources.current
    val target = context.snapshot.clock.stepEndSec
    StepLayout(
        top = {
            StepPill(stringResource(variantLabel(step.variant)))
            ExerciseName(step.exerciseName)
            resources.setLabel(step)?.let { Secondary(it) }
            StepNotes(step.notes)
        },
        middle = {
            Numeral(formatClock(if (step.countUp) context.elapsed.toDouble() else context.remaining), CLOCK_NUMERAL)
            if (!step.countUp) FillBar(if (target > 0) ((target - context.remaining) / target).toFloat() else 0f)
            Secondary(resources.caption(step))
            IntervalInputs(step.variant, context)
        },
        next = context.readings.next,
        controls = {
            PauseRow(context)
            if (step.countUp) {
                PrimaryButton(
                    stringResource(R.string.session_interval_doneUp),
                    context::advance,
                    Modifier.fillMaxWidth(),
                )
            } else {
                TextControl(
                    stringResource(R.string.session_interval_skipArrow),
                    context::advance,
                    Modifier.align(Alignment.CenterHorizontally).bleed(top = Spacing.two, bottom = SKIP_SLACK),
                    style = KettleType.heading.copy(color = RunnerColors.accent),
                )
            }
        },
    )
}

/** "Round n of N" for HIIT; "Minute n of N" only at a 60s EMOM, its interval otherwise; nothing else. */
private fun Resources.setLabel(step: RunnerStep.Interval): String? =
    when (step.variant) {
        IntervalVariant.HIIT -> {
            getString(R.string.session_interval_roundLabel, "${step.setIndex}", "${step.setTotal}")
        }

        IntervalVariant.EMOM -> {
            val key =
                if (step.targetSec ==
                    SECONDS_PER_MINUTE
                ) {
                    R.string.session_interval_minuteLabel
                } else {
                    R.string.session_interval_intervalLabel
                }
            getString(key, "${step.setIndex}", "${step.setTotal}")
        }

        else -> {
            null
        }
    }

private fun Resources.caption(step: RunnerStep.Interval): String =
    listOfNotNull(
        step.targetReps
            ?.takeIf { step.variant == IntervalVariant.EMOM && it > 0 }
            ?.let { getString(R.string.session_interval_targetReps, "$it") },
        step.cardioDistanceMeters
            ?.takeIf { step.variant == IntervalVariant.CARDIO && it > 0 }
            ?.let { getString(R.string.session_interval_targetDistance, jsNumber(it)) },
        if (step.countUp) {
            getString(R.string.session_countingUp)
        } else {
            getString(R.string.session_interval_ofTotal, formatClock(step.targetSec))
        },
    ).joinToString(" · ")

/** An EMOM minute's reps, or an AMRAP's rounds and extra reps. */
@Composable
private fun IntervalInputs(
    variant: IntervalVariant,
    context: StepContext,
) {
    val inputs = context.snapshot.inputs
    when (variant) {
        IntervalVariant.EMOM -> {
            Stepper(
                stringResource(R.string.session_interval_repsThisMinuteCaps),
                stringResource(R.string.session_interval_repsThisMinute),
                inputs.reps,
                context,
            ) { RunnerCommand.SetReps(it) }
        }

        IntervalVariant.AMRAP -> {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.four)) {
                Stepper(
                    stringResource(R.string.session_interval_roundsCaps),
                    stringResource(R.string.session_interval_roundsLabel),
                    inputs.roundsCompleted,
                    context,
                ) { RunnerCommand.SetRounds(it) }
                Stepper(
                    stringResource(R.string.session_interval_extraRepsCaps),
                    stringResource(R.string.session_interval_extraRepsLabel),
                    inputs.extraReps,
                    context,
                ) { RunnerCommand.SetExtraReps(it) }
            }
        }

        else -> {}
    }
}

/** A whole-number stepper whose numeral opens the pad. [label] is the caps kicker, [name] the pad's. */
@Composable
private fun Stepper(
    label: String,
    name: String,
    value: Long,
    context: StepContext,
    command: (Long) -> RunnerCommand,
) {
    val set = { next: Long -> context.send(command(maxOf(0, next))) }
    val description = stringResource(R.string.session_stepperAccessibility, label, "$value")
    Column(
        Modifier.padding(top = Spacing.three),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        Caps(label, tracking = 1.2.sp)
        // RN spaces a 44dp circle 14dp from a 36dp value. Here the circle sits in a 48dp target and the
        // value is one, so the gap is what puts each circle where RN draws it; RN's own would leave two
        // AMRAP steppers 16dp wider than the column, squeezing the last + into an oval.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STEPPER_ROW_GAP),
        ) {
            GlyphButton(
                "−",
                stringResource(R.string.common_decrease, label),
                { set(value - 1) },
                look = GlyphLook.Stepper,
            )
            Box(
                Modifier
                    .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                    .clickable(role = Role.Button) {
                        context.controls.openPad(
                            PadRequest(name, value.toDouble(), false, null) { set(Math.round(it)) },
                        )
                    }.semantics(mergeDescendants = true) { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text("$value", style = KettleType.heading, color = RunnerColors.text, textAlign = TextAlign.Center)
            }
            GlyphButton("+", stringResource(R.string.common_increase, label), {
                set(value + 1)
            }, look = GlyphLook.Stepper.copy(accent = true))
        }
    }
}

private fun variantLabel(variant: IntervalVariant): Int =
    when (variant) {
        IntervalVariant.HIIT -> R.string.session_interval_variantHiit
        IntervalVariant.EMOM -> R.string.session_interval_variantEmom
        IntervalVariant.AMRAP -> R.string.session_interval_variantAmrap
        IntervalVariant.CARDIO -> R.string.session_interval_variantCardio
    }

private val CLOCK_NUMERAL = 88.sp

/**
 * The heading face's line at 1× in the display font since #323 (Skip's node measured 23dp at the S25+'s
 * 0.9 scale). Above [SKIP_SLACK], which reads it.
 */
private val HEADING_LINE = 26.dp

/** The slack under Skip's words in its 48dp target, which hangs into the frame's 16dp padding. */
private val SKIP_SLACK = (MinTarget - HEADING_LINE) / 2
private val STEPPER_ROW_GAP = 6.dp
private const val SECONDS_PER_MINUTE = 60.0
