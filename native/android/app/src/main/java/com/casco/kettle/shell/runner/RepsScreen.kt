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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import com.casco.kettle.shell.theme.RunnerColors
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.three)) {
                StatCard(stringResource(R.string.session_reps_loadCaps), Modifier.weight(1f)) { LoadControl(context) }
                StatCard(stringResource(R.string.session_reps_rpe), Modifier.weight(1f)) { RpeChoice(context) }
            }
        },
        next = readings.next,
        controls = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.three)) {
                PrevTextButton(context)
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
                    style = KettleType.small.copy(color = RunnerColors.accentOnSoft),
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.four)) {
        GlyphButton("−", stringResource(R.string.common_decrease, label), { set(reps - 1) }, size = STEPPER)
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
            Numeral("$reps", BIG_NUMERAL)
            Caps(stringResource(R.string.session_reps_repsDoneCaps))
        }
        GlyphButton("+", stringResource(R.string.common_increase, label), { set(reps + 1) }, size = STEPPER)
    }
}

@Composable
private fun StatCard(
    label: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RunnerColors.backgroundElement)
            .padding(Spacing.two),
        verticalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        Caps(label, Modifier.padding(start = Spacing.one))
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlyphButton("−", stringResource(R.string.session_reps_decreaseLoad), { set(display - weightStep(units)) })
        Text(
            if (kg > 0) {
                buildAnnotatedString {
                    append(jsNumber(display))
                    withStyle(KettleType.small.toSpanStyle()) { append(" $unit") }
                }
            } else {
                AnnotatedString(stringResource(R.string.session_reps_bodyweightAbbr))
            },
            Modifier
                .weight(1f)
                .heightIn(min = MinTarget)
                .clickable(
                    role = Role.Button,
                ) { context.controls.openPad(PadRequest(padLabel, display, true, unit, set)) }
                .semantics { contentDescription = description }
                .padding(top = Spacing.two),
            style = KettleType.heading,
            color = RunnerColors.text,
            textAlign = TextAlign.Center,
        )
        GlyphButton("+", stringResource(R.string.session_reps_increaseLoad), { set(display + weightStep(units)) })
    }
}

@Composable
private fun RpeChoice(context: StepContext) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.one)) {
        for (option in RPE_OPTIONS) {
            val selected = option == context.snapshot.inputs.rpe
            val description = stringResource(R.string.session_reps_rpeOption, jsNumber(option))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTarget)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) RunnerColors.accent else Color.Transparent)
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
                    color = if (selected) RunnerColors.background else RunnerColors.text,
                )
            }
        }
    }
}

private const val RPE_EASY = 7.0
private const val RPE_HARD = 8.0
private const val RPE_LIMIT = 9.0
private val RPE_OPTIONS = listOf(RPE_EASY, RPE_HARD, RPE_LIMIT)
private val STEPPER = 64.dp
