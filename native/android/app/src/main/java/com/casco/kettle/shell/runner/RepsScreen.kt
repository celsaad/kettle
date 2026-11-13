package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.fromDisplayWeight
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.repsTarget
import com.casco.kettle.domain.restFollows
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.domain.weightStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.weightUnit
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Reps: no clock, so no Pause. The numerals open the pad and the −/+ nudge; the load is in the user's
 * own unit, stored in kilograms, and bottoms out at 0, which logs as bodyweight.
 */
@Composable
internal fun RepsScreen(
    step: RunnerStep.Reps,
    context: StepContext,
) {
    val readings = context.readings
    StepLayout(
        top = {
            StepPill(stringResource(R.string.session_reps_pillLabel), live = false)
            ExerciseName(step.exerciseName)
            SetCountRow(
                stringResource(
                    R.string.session_reps_setLabel,
                    step.setIndex,
                    repsTargetText(repsTarget(step)),
                    step.setTotal,
                ),
                context,
            )
            StepNotes(step.notes)
            LastTime(step, context)
        },
        middle = {
            RepsCounter(context)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                StatCard(stringResource(R.string.session_reps_loadCaps), Modifier.weight(1f)) { LoadControl(context) }
                StatCard(stringResource(R.string.session_reps_rpe), Modifier.weight(1f)) { RpeChoice(context) }
            }
        },
        next = readings.next,
        middleGap = Spacing.four,
        controls = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CARD_PAD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrevTextButton({ context.send(RunnerCommand.Prev) })
                val follows = restFollows(context.snapshot.steps, context.snapshot.stepIndex)
                PrimaryButton(
                    stringResource(if (follows) R.string.session_reps_logSet else R.string.session_reps_logSetNoRest),
                    context::advance,
                    Modifier.weight(1f),
                )
            }
        },
    )
}

private fun spokenUnit(units: UnitSystem) =
    if (units ==
        UnitSystem.IMPERIAL
    ) {
        R.string.units_lbSpoken
    } else {
        R.string.units_kgSpoken
    }

/** Last time's set, which adopts its load with a tap: there's nothing to take from a bodyweight set. */
@Composable
private fun LastTime(
    step: RunnerStep.Reps,
    context: StepContext,
) {
    val resources = LocalResources.current
    val previous = context.readings.previous as? PreviousSet.Reps
    val text = previous?.let { resources.previousSetText(it, context.units) }
    val kg = previous?.weightKg?.takeIf { it > 0 }
    val adopt: (@Composable () -> Unit)? =
        if (text != null && kg != null) {
            {
                TextControl(
                    text,
                    { context.controls.adopt(step.exerciseId, kg) },
                    Modifier.hang(start = Spacing.two),
                    style = KettleType.small.copy(color = runnerColors.accentOnSoft),
                    description =
                        stringResource(
                            R.string.session_reps_adoptPrevious,
                            stringResource(spokenUnit(context.units)),
                            jsNumber(toDisplayWeight(kg, context.units)),
                        ),
                )
            }
        } else {
            null
        }
    PreviousRow(text, context.readings.beatsBest, adopt)
}

@Composable
private fun RepsCounter(context: StepContext) {
    val resources = LocalResources.current
    val reps = context.snapshot.inputs.reps
    val label = stringResource(R.string.session_reps_repsLabel)
    val padLabel = stringResource(R.string.session_reps_repsDone)
    val set = { value: Long -> context.send(RunnerCommand.SetReps(maxOf(0, value))) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STEPPER_GAP)) {
        SymbolButton(R.drawable.icon_remove, stringResource(R.string.common_decrease, label), {
            set(reps - 1)
        }, look = SymbolLook.Big)
        Column(
            Modifier
                .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                .clickable(role = Role.Button) {
                    context.controls.openPad(PadRequest(padLabel, reps.toDouble(), false, null) { set(Math.round(it)) })
                }.semantics {
                    contentDescription =
                        resources.getString(R.string.session_reps_repsDoneAccessibility, "$reps")
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Numeral("$reps", REPS_NUMERAL)
            Caps(stringResource(R.string.session_reps_repsDoneCaps), Modifier.padding(top = REPS_LABEL_GAP))
        }
        SymbolButton(R.drawable.icon_add, stringResource(R.string.common_increase, label), {
            set(reps + 1)
        }, look = SymbolLook.Big)
    }
}

@Composable
private fun StatCard(
    label: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.runnerCard(16.dp, runnerColors),
        verticalArrangement = Arrangement.spacedBy(Spacing.half),
    ) {
        Caps(label, tracking = 1.sp)
        content()
    }
}

/** The load, in display units: −/+ by the equipment's smallest jump, the value opening a decimal pad. */
@Composable
private fun LoadControl(context: StepContext) {
    val units = context.units
    val kg = context.snapshot.inputs.weightKg
    val display = toDisplayWeight(kg, units)
    val unit = stringResource(weightUnit(units))
    val padLabel = stringResource(R.string.session_reps_loadLabel)
    val description =
        if (kg > 0) {
            stringResource(
                R.string.session_reps_loadAccessibilityWeighted,
                stringResource(spokenUnit(units)),
                jsNumber(display),
            )
        } else {
            stringResource(R.string.session_reps_loadAccessibilityBodyweight)
        }
    val set = { value: Double -> context.send(RunnerCommand.SetWeight(fromDisplayWeight(maxOf(0.0, value), units))) }
    // The −/+ targets hang their slack into the card's padding, so the circles sit where RN's 30dp ones
    // do and the value keeps RN's room; it stays one line, drawing over that slack if it must.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.hang(start = LOAD_SLACK)) {
            SymbolButton(R.drawable.icon_remove, stringResource(R.string.session_reps_decreaseLoad), {
                set(display - weightStep(units))
            }, look = SymbolLook.Load)
        }
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = MinTarget)
                .clickable(
                    role = Role.Button,
                ) { context.controls.openPad(PadRequest(padLabel, display, true, unit, set)) }
                .semantics(mergeDescendants = true) { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (kg > 0) {
                    buildAnnotatedString {
                        append(jsNumber(display))
                        withStyle(
                            SpanStyle(fontSize = LOAD_UNIT, color = runnerColors.textSecondary),
                        ) { append(" $unit") }
                    }
                } else {
                    AnnotatedString(stringResource(R.string.session_reps_bodyweightAbbr))
                },
                Modifier.wrapContentWidth(unbounded = true),
                style = KettleType.heading,
                color = runnerColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Box(Modifier.hang(end = LOAD_SLACK)) {
            SymbolButton(R.drawable.icon_add, stringResource(R.string.session_reps_increaseLoad), {
                set(display + weightStep(units))
            }, look = SymbolLook.Load)
        }
    }
}

@Composable
private fun RpeChoice(context: StepContext) {
    Row(Modifier.padding(top = Spacing.one), horizontalArrangement = Arrangement.spacedBy(RPE_GAP)) {
        for (option in RPE_OPTIONS) {
            val selected = option == context.snapshot.inputs.rpe
            val description = stringResource(R.string.session_reps_rpeOption, jsNumber(option))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTarget)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) runnerColors.accent else runnerColors.text.copy(alpha = CHIP_ALPHA))
                    .clickable(role = Role.Button) { context.send(RunnerCommand.SetRpe(option)) }
                    .semantics {
                        contentDescription = description
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    jsNumber(option),
                    style = KettleType.smallMedium,
                    color = if (selected) runnerColors.onAccent else runnerColors.text,
                )
            }
        }
    }
}

private const val RPE_EASY = 7.0
private const val RPE_HARD = 8.0
private const val RPE_LIMIT = 9.0
private val RPE_OPTIONS = listOf(RPE_EASY, RPE_HARD, RPE_LIMIT)
private val RPE_GAP = 6.dp
private val REPS_NUMERAL = 88.sp
private val STEPPER_GAP = 22.dp
private val REPS_LABEL_GAP = 6.dp
private val LOAD_UNIT = 15.sp

/** A load target's slack around its circle, which fits under the card's 14dp padding. */
private val LOAD_SLACK = (MinTarget - SymbolLook.Load.size) / 2

/** RN's `rgba(243,239,228,0.08)`: an unpicked chip, filled faintly so all three read as choices. */
private const val CHIP_ALPHA = 0.08f
