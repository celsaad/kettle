package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.timeText

/**
 * A hold counts up. Its bar runs to the top of the range, with the range itself a band from the minimum
 * (#417) on a real one, and a max-effort hold, with no scale to draw against, has no bar at all. The
 * line under it says where the hold stops by itself, which is the top of the range.
 */
@Composable
internal fun HoldScreen(
    step: RunnerStep.Hold,
    context: StepContext,
) {
    val resources = LocalResources.current
    val readings = context.readings
    StepLayout(
        top = {
            StepHeader(
                resources.stepLabel(step, context.snapshot.inputs),
                step.exerciseName,
                resources.circuitSpoken(step),
            )
            ActionRow(context)
            StepNotes(step.notes)
            PreviousRow(
                (readings.previous as? PreviousSet.Hold)?.let { resources.previousSetText(it, context.units) },
                readings.beatsBest,
            )
        },
        middle = { HoldClock(step, context.elapsed, context.paused) },
        controls = {
            ClockRow(context, doneAction(context))
        },
    )
}

@Composable
private fun HoldClock(
    step: RunnerStep.Hold,
    elapsed: Long,
    paused: Boolean,
) {
    val target = step.holdTargetSec
    PausedLine(paused)
    Row {
        Numeral("$elapsed", HOLD_NUMERAL, Modifier.alignByBaseline(), clockInk(paused))
        Numeral("s", UNIT_NUMERAL, Modifier.alignByBaseline(), runnerColors.textSecondary, leading = null)
    }
    if (target != null) {
        val barSec = step.holdTargetMaxSec?.takeIf { it > target } ?: target
        val bandStart = target.takeIf { barSec > it }?.let { (it / barSec).toFloat() }
        FillBar(if (barSec > 0) (elapsed / barSec).toFloat() else 0f, bandStart, barFill(paused))
    }
    Secondary(holdCaption(step))
}

/**
 * "Target 0:20–0:45 · stops at 0:45" (#462); a single target is where it stops; no target counts up. The
 * counting numeral above stays in seconds: it's a clock running, not a time written down.
 */
@Composable
private fun holdCaption(step: RunnerStep.Hold): String {
    val target = holdTarget(step) ?: return stringResource(R.string.session_hold_captionOpen)
    val end = step.holdEndSec?.let(::timeText)
    val max = target.maxSec
    return when {
        end == null -> stringResource(R.string.session_hold_targetOnly, timeText(target.minSec))
        max == null -> stringResource(R.string.session_hold_stopsAt, end)
        else -> stringResource(R.string.session_hold_target, end, holdTargetText(target))
    }
}

private val HOLD_NUMERAL = 96.sp
private val UNIT_NUMERAL = 38.sp
