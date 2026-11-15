package com.casco.kettle.shell.runner

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.domain.isMinute
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
            // Off while the minute rests out (#477): the countdown to the next one is what matters then.
            if (!resting) StepNotes(step.notes)
        },
        middle = {
            PausedLine(context.paused)
            if (resting) Secondary(resources.nextStartsIn(step))
            Numeral(
                formatClock(if (step.countUp) context.elapsed.toDouble() else context.remaining),
                CLOCK_NUMERAL,
                color = clockInk(context.paused),
            )
            if (!step.countUp) FillBar(context.spent, fill = barFill(context.paused))
            if (!resting) {
                val snapshot = context.snapshot
                ClockHint(
                    resources.caption(step),
                    resources.thenHint(
                        step,
                        snapshot.steps.getOrNull(snapshot.stepIndex + 1),
                    ),
                )
            }
            if (emom) {
                Stepper(
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

/**
 * The start of the line under the bar (#471): how the clock ends. An EMOM's counts down what's left of
 * the minute, its target being the stepper's seed; cardio's ends by itself, with its distance; HIIT's is
 * its length, with what follows at the other end ([thenHint]).
 */
private fun Resources.caption(step: RunnerStep.Interval): String {
    val distance =
        step.cardioDistanceMeters
            ?.takeIf { step.variant == IntervalVariant.CARDIO && it > 0 }
            ?.let { getString(R.string.session_interval_targetDistance, jsNumber(it)) }
    val clock =
        when {
            step.countUp -> {
                getString(R.string.session_countingUp)
            }

            step.variant == IntervalVariant.EMOM && step.isMinute -> {
                getString(R.string.session_interval_leftInMinute)
            }

            step.variant == IntervalVariant.EMOM -> {
                getString(R.string.session_interval_leftInInterval)
            }

            step.variant == IntervalVariant.CARDIO -> {
                getString(R.string.session_interval_of, formatClock(step.targetSec)) + SEPARATOR +
                    getString(R.string.session_interval_endsByItself)
            }

            else -> {
                getString(R.string.session_interval_of, formatClock(step.targetSec))
            }
        }
    return listOfNotNull(distance, clock).joinToString(SEPARATOR)
}

/**
 * What a HIIT round's clock leads to, the line's far end (#471): the rest after it, the next round where
 * no rest comes between, the exercise that follows, and nothing at the session's end. A repeat round
 * isn't named as an exercise: "Then Burpees" during Burpees, with `rest_sec: 0`, read as a change of
 * exercise. A zero-second Rest block, kept as an entry, says nothing either. Only HIIT's: an EMOM's
 * minute and cardio say how they end instead.
 */
private fun Resources.thenHint(
    step: RunnerStep.Interval,
    next: RunnerStep?,
): String? {
    if (step.variant != IntervalVariant.HIIT) return null
    return when (next) {
        is RunnerStep.Rest -> {
            next.seconds.takeIf { it > 0 }?.let { getString(R.string.session_interval_thenRest, formatClock(it)) }
        }

        is WorkStep -> {
            when {
                next.memberKey != step.memberKey -> getString(R.string.session_interval_thenNext, next.exerciseName)

                next.setIndex > step.setIndex -> getString(R.string.session_comingUp_thenRound, "${next.setIndex}")

                // A one-member circuit's next visit: the same exercise again, from its first round.
                else -> null
            }
        }

        null -> {
            null
        }
    }
}

private const val SEPARATOR = " · "

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
            PausedLine(context.paused)
            Numeral(formatClock(context.remaining), CLOCK_NUMERAL, color = clockInk(context.paused))
            FillBar(context.spent, fill = barFill(context.paused))
            PaceLine(rounds, context.readings.lastRounds)
            if (context.remaining <= LAST_MINUTE_SEC) {
                Stepper(
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

/**
 * A whole-number stepper whose numeral opens the pad, the Load stepper's look as the board draws both
 * (#477): outlined 56dp rounded squares at the column's edges, [name] in sentence case over a 28sp value.
 * [name] is the pad's title and what each button says it changes.
 */
@Composable
private fun Stepper(
    name: String,
    value: Long,
    context: StepContext,
    command: (Long) -> RunnerCommand,
) {
    val set = { next: Long -> context.send(command(maxOf(0, next))) }
    val description = stringResource(R.string.session_stepperAccessibility, name, "$value")
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.three),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        SymbolButton(R.drawable.icon_remove, stringResource(R.string.common_decrease, name), {
            set(value - 1)
        }, look = SymbolLook.Load)
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = MinTarget)
                .clickable(role = Role.Button) {
                    context.controls.openPad(PadRequest(name, value.toDouble(), false, null) { set(Math.round(it)) })
                }.semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centred like the value under it, should the name wrap at large text.
            Text(name, style = KettleType.small, color = runnerColors.textSecondary, textAlign = TextAlign.Center)
            // A Numeral, so its size stops at 1.3× the text size: at 200% the full-size value made the middle
            // scroll on a short screen, the clock out of sight.
            Numeral("$value", STEPPER_VALUE, color = runnerColors.text)
        }
        SymbolButton(R.drawable.icon_add, stringResource(R.string.common_increase, name), {
            set(value + 1)
        }, look = SymbolLook.Load)
    }
}

private val CLOCK_NUMERAL = 88.sp

/** When an AMRAP offers its extra reps: the cap's last minute. A span of time, not the length [isMinute] checks. */
private const val LAST_MINUTE_SEC = 60.0

/** The board's 28px stepper value. */
private val STEPPER_VALUE = 28.sp
