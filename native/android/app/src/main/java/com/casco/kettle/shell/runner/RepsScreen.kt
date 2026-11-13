package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import com.casco.kettle.shell.theme.TypeIcon

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
            val resources = LocalResources.current
            StepHeader(resources.stepLabel(step), step.exerciseName, resources.circuitSpoken(step))
            ActionRow(context)
            StepNotes(step.notes)
            LastTime(step, context)
        },
        middle = {
            RepsCounter(step, context)
            StatCard(stringResource(R.string.session_reps_loadCaps), Modifier.fillMaxWidth()) { LoadControl(context) }
            EffortTrack(context)
        },
        middleGap = Spacing.four,
        controls = {
            val follows = restFollows(context.snapshot.steps, context.snapshot.stepIndex)
            BottomRow(
                RowAction(
                    stringResource(if (follows) R.string.session_reps_logSet else R.string.session_reps_logSetNoRest),
                    null,
                    context::advance,
                ),
                back = { context.send(RunnerCommand.Prev) },
            )
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
private fun RepsCounter(
    step: RunnerStep.Reps,
    context: StepContext,
) {
    val resources = LocalResources.current
    val reps = context.snapshot.inputs.reps
    val label = stringResource(R.string.session_reps_repsLabel)
    val padLabel = stringResource(R.string.session_reps_repsDone)
    val set = { value: Long -> context.send(RunnerCommand.SetReps(maxOf(0, value))) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // The target above the stepper, as the canvas draws it, not inside it: between − and + it widened the
        // middle with the text size and, at 2× on the AVD, crushed + into a sliver at the screen's edge.
        Secondary(
            stringResource(R.string.session_reps_repsDoneTarget, repsTargetText(repsTarget(step))),
            Modifier.padding(bottom = REPS_LABEL_GAP),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STEPPER_GAP)) {
            SymbolButton(R.drawable.icon_remove, stringResource(R.string.common_decrease, label), {
                set(reps - 1)
            }, look = SymbolLook.Big)
            Column(
                Modifier
                    .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                    .clickable(role = Role.Button) {
                        context.controls.openPad(
                            PadRequest(padLabel, reps.toDouble(), false, null) { set(Math.round(it)) },
                        )
                    }.semantics {
                        contentDescription =
                            resources.getString(R.string.session_reps_repsDoneAccessibility, "$reps")
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Numeral("$reps", REPS_NUMERAL)
            }
            SymbolButton(R.drawable.icon_add, stringResource(R.string.common_increase, label), {
                set(reps + 1)
            }, look = SymbolLook.Big)
        }
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

/**
 * The set's effort, 6 to 10, optional (#410): one outlined track, empty until a value is picked. The
 * picked one is outlined with a check rather than filled, so it can't be taken for the main action, and
 * picking it again clears it: "I didn't mean to rate this" is one tap. A radio group to a reader, each
 * option named by its own number, so Voice Control's "tap 9" matches the screen; the "Effort ·
 * optional" line read just before the group says what the numbers are.
 */
@Composable
private fun EffortTrack(context: StepContext) {
    val picked = context.snapshot.inputs.rpe
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        Secondary(stringResource(R.string.session_reps_effortLabel))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(TrackShape)
                .border(SLOT_BORDER, runnerColors.outline, TrackShape)
                .selectableGroup(),
        ) {
            EFFORT_OPTIONS.forEachIndexed { index, option ->
                if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(runnerColors.border))
                EffortOption(option, option == picked, Modifier.weight(1f)) {
                    context.send(RunnerCommand.SetRpe(if (option == picked) null else option))
                }
            }
        }
    }
}

@Composable
private fun EffortOption(
    option: Double,
    selected: Boolean,
    modifier: Modifier,
    pick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = MinTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = pick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            (if (selected) Modifier.border(SLOT_BORDER, runnerColors.text, PickedShape) else Modifier)
                .padding(horizontal = Spacing.two, vertical = Spacing.one),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.half),
        ) {
            if (selected) TypeIcon(R.drawable.icon_check, KettleType.small, runnerColors.text)
            Text(jsNumber(option), style = SlotLabel, color = runnerColors.text)
        }
    }
}

/** The canvas's effort scale: the top half of RPE, where a set worth rating sits. */
private const val EFFORT_LOW = 6
private const val EFFORT_HIGH = 10
private val EFFORT_OPTIONS = (EFFORT_LOW..EFFORT_HIGH).map(Int::toDouble)
private val TrackShape = RoundedCornerShape(14.dp)
private val PickedShape = RoundedCornerShape(10.dp)
private val REPS_NUMERAL = 88.sp
private val STEPPER_GAP = 22.dp
private val REPS_LABEL_GAP = 6.dp
private val LOAD_UNIT = 15.sp

/** A load target's slack around its circle, which fits under the card's 14dp padding. */
private val LOAD_SLACK = (MinTarget - SymbolLook.Load.size) / 2
