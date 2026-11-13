package com.casco.kettle.shell.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitPosition
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.blockTotal
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * A running session: the header, the circuit crumb and the step on screen, or the ad-hoc parked
 * screen. Hidden from accessibility while anything is open over it, so a screen reader stays there.
 */
@Composable
internal fun Frame(
    view: RunnerView.Running,
    context: StepContext,
    open: Set<Overlay>,
) {
    val hidden = if (open.isEmpty()) Modifier else Modifier.clearAndSetSemantics {}
    val controls = context.controls
    val step = view.snapshot.step
    if (step == null) {
        // Only an ad-hoc session parks here, at its start and after each exercise. Its Finish asks
        // nothing: there is no set on screen to lose.
        if (view.snapshot.adhoc) {
            Notice(
                hidden,
                stringResource(R.string.session_adhoc_title),
                stringResource(R.string.session_adhoc_body),
                DialogAction(stringResource(R.string.session_adhoc_addExercise)) { controls.open(Overlay.ADD_PICKER) },
                DialogAction(stringResource(R.string.session_finish_label)) {
                    controls.send(RunnerCommand.Finish(view.snapshot.visit))
                },
            )
        }
        return
    }
    // No top padding, unlike RN's 8: the header row is 48dp of targets around a line of small text, so
    // its words already sit 15dp down. Below it the same slack counts toward RN's 24dp, and the crumb,
    // a plain line with none, takes all 24 itself. The crumb sits 11dp lower than RN's 4dp under the
    // words: raising it into the header's slack made a tap on it open Coming up.
    Column(hidden.fillMaxSize().padding(bottom = Spacing.three)) {
        Header(view, Overlay.UPCOMING in open, controls)
        step.circuit?.let { CircuitCrumb(it) }
        Spacer(Modifier.height(if (step.circuit == null) Spacing.two else Spacing.four))
        Box(Modifier.weight(1f)) {
            when (step) {
                is RunnerStep.Hold -> HoldScreen(step, context)
                is RunnerStep.Reps -> RepsScreen(step, context)
                is RunnerStep.Interval -> IntervalScreen(step, context)
                is RunnerStep.Rest -> RestScreen(context)
            }
        }
    }
}

@Composable
private fun Header(
    view: RunnerView.Running,
    upcomingOpen: Boolean,
    controls: StepControls,
) {
    val snapshot = view.snapshot
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        WorkoutName(view, upcomingOpen, Modifier.weight(1f)) { controls.open(Overlay.UPCOMING) }
        ProgressTrack(blockTotal(snapshot), snapshot.step?.blockIndex ?: 0)
        // Ad-hoc only: a planned workout knows what it holds, and here it's the only way to queue anything.
        if (snapshot.adhoc) {
            TextControl(stringResource(R.string.session_adhoc_addShort), { controls.open(Overlay.ADD_PICKER) })
        }
        // Named by their own words, unlike RN's, whose differing labels Voice Control can't match.
        TextControl(stringResource(R.string.session_finish_label), controls.confirmFinish)
    }
}

/**
 * The workout's name, which opens Coming up: a chevron and nothing louder, being read mid-set. Named by
 * its own text, so it takes a hint rather than a label, which Voice Control would have to match.
 */
@Composable
private fun WorkoutName(
    view: RunnerView.Running,
    upcomingOpen: Boolean,
    modifier: Modifier,
    openUpcoming: () -> Unit,
) {
    val resources = LocalResources.current
    val hint = stringResource(R.string.session_upcoming_hint)
    Row(
        modifier
            .heightIn(min = MinTarget)
            // The hint as the click's own label: a second onClick in semantics lost to the clickable's.
            .clickable(role = Role.Button, onClickLabel = hint, onClick = openUpcoming)
            .semantics {
                if (!upcomingOpen) {
                    expand {
                        openUpcoming()
                        true
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        Text(
            resources.sessionName(view.plan.workout?.name),
            Modifier.weight(1f, fill = false),
            style = KettleType.small,
            color = runnerColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Chevron()
    }
}

/**
 * "Round n of N" with the members' track, as one node: split, it announced a bare round count and then
 * an unnamed bar duplicating the header's. Finish's width at the end lines the two tracks up.
 */
@Composable
private fun CircuitCrumb(circuit: CircuitPosition) {
    val spoken =
        stringResource(
            R.string.session_circuit_accessibility,
            circuit.round,
            circuit.member,
            circuit.memberTotal,
            circuit.rounds,
        )
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Text(
            stringResource(R.string.session_circuit_crumb, "${circuit.round}", "${circuit.rounds}"),
            Modifier.weight(1f),
            style = KettleType.code,
            color = runnerColors.accentCalmOnSoft,
            maxLines = 1,
        )
        ProgressTrack(circuit.memberTotal, circuit.member - 1, calm = true)
        Box(Modifier.padding(horizontal = Spacing.two)) { WidthOf(stringResource(R.string.session_finish_label)) }
    }
}

/** A down chevron, drawn: RN's is an SVG path, and a text glyph would change with the font. */
@Composable
private fun Chevron() {
    val color = runnerColors.textSecondary
    Canvas(Modifier.size(12.dp)) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val left = Offset(w * CHEVRON_INSET, w * CHEVRON_TOP)
        val tip = Offset(w / 2, w * CHEVRON_BOTTOM)
        val right = Offset(w * (1 - CHEVRON_INSET), w * CHEVRON_TOP)
        drawLine(color, left, tip, stroke, StrokeCap.Round)
        drawLine(color, tip, right, stroke, StrokeCap.Round)
    }
}

/** The shape the pre-runner and parked screens share: a title, a line, one action and maybe a second. */
@Composable
private fun Notice(
    modifier: Modifier,
    title: String,
    body: String,
    primary: DialogAction,
    secondary: DialogAction? = null,
) {
    CenteredColumn(modifier) {
        Text(
            title,
            Modifier.semantics {
                heading()
            },
            style = KettleType.subtitle,
            color = runnerColors.text,
            textAlign = TextAlign.Center,
        )
        Text(body, style = KettleType.small, color = runnerColors.textSecondary, textAlign = TextAlign.Center)
        CompactButton(primary.label, primary.onClick, Modifier.padding(top = Spacing.two))
        if (secondary != null) TextControl(secondary.label, secondary.onClick, Modifier.padding(top = Spacing.two))
    }
}

/** What a plan that doesn't start by itself says before any count-in (§3.1). Missing draws nothing. */
@Composable
internal fun Before(
    plan: RunnerPlan,
    actions: RunnerActions,
) {
    val close = DialogAction(stringResource(R.string.common_close), actions.leave)
    when (plan) {
        RunnerPlan.RestWeek -> {
            Notice(
                Modifier,
                stringResource(R.string.session_restDay_title),
                stringResource(R.string.session_restDay_body),
                close,
            )
        }

        is RunnerPlan.NoSteps -> {
            Notice(
                Modifier,
                stringResource(R.string.session_nothingToRun_title),
                stringResource(R.string.session_nothingToRun_body, plan.workout.name),
                close,
            )
        }

        // Said here, with a control, rather than as a banner over a count-in gone in three seconds.
        is RunnerPlan.TooLong -> {
            Notice(
                Modifier,
                stringResource(R.string.session_tooLong_title),
                stringResource(
                    R.string.session_tooLong_body,
                    plan.plan.workout
                        ?.name
                        .orEmpty(),
                ),
                DialogAction(stringResource(R.string.session_tooLong_startAnyway), actions.startAnyway),
                close,
            )
        }

        RunnerPlan.Missing, is RunnerPlan.Runnable -> {}
    }
}

private const val CHEVRON_INSET = 0.25f
private const val CHEVRON_TOP = 0.375f
private const val CHEVRON_BOTTOM = 0.625f
