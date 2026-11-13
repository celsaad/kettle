package com.casco.kettle.shell.runner

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * HIIT, EMOM, AMRAP and cardio. A countdown's way out is a quiet "Skip", since its clock ends it; a
 * count-up's is Done, being the only way out. An AMRAP has a screen of its own ([AmrapScreen]).
 *
 * An EMOM minute's is Done (#417): the reps are in, so the screen turns to rest for what's left of the
 * minute, and then Skip, which logs it. Done only says so on screen ([StepContext.resting]): the minute
 * still ends on its own clock and logs the stepper as it stands, which stays editable meanwhile.
 */
@Composable
internal fun IntervalScreen(
    step: RunnerStep.Interval,
    context: StepContext,
) {
    if (step.variant == IntervalVariant.AMRAP) return AmrapScreen(step, context)
    val resources = LocalResources.current
    val emom = step.variant == IntervalVariant.EMOM
    val resting = emom && context.resting
    StepLayout(
        top = {
            val label = resources.stepLabel(step, context.snapshot.inputs)
            StepHeader(
                if (resting) stringResource(R.string.session_emom_resting, label) else label,
                step.exerciseName,
                resources.circuitSpoken(step),
            )
            StepNotes(step.notes)
        },
        middle = {
            if (resting) Secondary(resources.nextStartsIn(step))
            Numeral(formatClock(if (step.countUp) context.elapsed.toDouble() else context.remaining), CLOCK_NUMERAL)
            if (!step.countUp) FillBar(context.spent)
            if (!resting) Secondary(resources.caption(step))
            if (emom) {
                Stepper(
                    stringResource(R.string.session_interval_repsThisMinuteCaps),
                    stringResource(R.string.session_interval_repsThisMinute),
                    context.snapshot.inputs.reps,
                    context,
                ) { RunnerCommand.SetReps(it) }
            }
        },
        controls = {
            val exit =
                when {
                    step.countUp -> doneAction(context)
                    emom && !resting -> minuteDoneAction(context)
                    else -> skipAction(context)
                }
            ClockRow(context, exit)
        },
    )
}

/** An EMOM minute's Done: rest out the minute on screen. Logging is still the minute's clock, or Skip. */
@Composable
private fun minuteDoneAction(context: StepContext) =
    RowAction(stringResource(R.string.session_done), R.drawable.icon_stop, context.controls.minute.restOut)

/** What the rest after an EMOM's Done counts down to: the next minute, or the step's end after the last. */
private fun Resources.nextStartsIn(step: RunnerStep.Interval): String {
    val next = "${step.setIndex + 1}"
    return when {
        step.setIndex >= step.setTotal -> getString(R.string.session_emom_endsIn)
        step.isMinute -> getString(R.string.session_emom_minuteStartsIn, next)
        else -> getString(R.string.session_emom_intervalStartsIn, next)
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

/**
 * An AMRAP (#417): "Round N done" is the main action, each tap a round, with Undo taking back the last;
 * Pause sits to the side, and there is no Skip, the cap or End being the ways out. The extra reps come
 * only in the last minute, since the cap ends the step and logs it in one move: there is no after.
 */
@Composable
private fun AmrapScreen(
    step: RunnerStep.Interval,
    context: StepContext,
) {
    val resources = LocalResources.current
    val inputs = context.snapshot.inputs
    val rounds = inputs.roundsCompleted
    val round = rounds + 1
    StepLayout(
        top = {
            StepHeader(resources.stepLabel(step, inputs), step.exerciseName, resources.circuitSpoken(step))
            StepNotes(step.notes)
        },
        middle = {
            Numeral(formatClock(context.remaining), CLOCK_NUMERAL)
            FillBar(context.spent)
            PaceLine(rounds, context.readings.lastRounds)
            if (context.remaining <= LAST_MINUTE_SEC) {
                Stepper(
                    stringResource(R.string.session_interval_repsIntoRoundCaps, "$round"),
                    stringResource(R.string.session_interval_repsIntoRound, "$round"),
                    inputs.extraReps,
                    context,
                ) { RunnerCommand.SetExtraReps(it) }
            }
        },
        controls = {
            if (rounds > 0) {
                LoggedPanel(stringResource(R.string.session_logged_round, "$rounds")) {
                    context.send(RunnerCommand.SetRounds(rounds - 1))
                }
            }
            BottomRow(
                RowAction(stringResource(R.string.session_amrap_roundDone, "$round"), null) {
                    context.send(RunnerCommand.SetRounds(round))
                },
                back = { context.send(RunnerCommand.Prev) },
                side = pauseAction(context),
            )
        },
    )
}

/** "7 rounds · last time 7": the count in the text's own ink, last time only once there is one. */
@Composable
private fun PaceLine(
    rounds: Long,
    lastRounds: Long?,
) {
    val count = pluralText(R.plurals.session_amrap_rounds, R.string.session_amrap_rounds_zero, rounds.toInt())
    val last = lastRounds?.let { stringResource(R.string.session_amrap_lastTime, "$it") }
    val ink = runnerColors.text
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(count) }
            if (last != null) append(" · $last")
        },
        style = KettleType.small,
        color = runnerColors.textSecondary,
        textAlign = TextAlign.Center,
    )
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
            SymbolButton(
                R.drawable.icon_remove,
                stringResource(R.string.common_decrease, label),
                { set(value - 1) },
                look = SymbolLook.Stepper,
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
                Text("$value", style = KettleType.heading, color = runnerColors.text, textAlign = TextAlign.Center)
            }
            SymbolButton(R.drawable.icon_add, stringResource(R.string.common_increase, label), {
                set(value + 1)
            }, look = SymbolLook.Stepper.copy(accent = true))
        }
    }
}

private val CLOCK_NUMERAL = 88.sp

/** When an AMRAP offers its extra reps: the cap's last minute. A span of time, not [isMinute]'s length. */
private const val LAST_MINUTE_SEC = 60.0
private val STEPPER_ROW_GAP = 6.dp
