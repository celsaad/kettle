package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Top, middle, the Next card and the controls, spread down the screen: no scroll, as RN's. The top
 * block is RN's 4dp rhythm, the name and last time's row taking a step more; [middleGap] is the reps
 * screen's wider one. The controls sit 14dp under the card and 8dp apart, as RN's Done under Pause.
 */
@Composable
internal fun StepLayout(
    top: @Composable ColumnScope.() -> Unit,
    middle: @Composable ColumnScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    next: StepPreview? = null,
    middleGap: Dp = Spacing.two,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.one), content = top)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(middleGap),
            content = middle,
        )
        Column {
            NextCard(next)
            Column(Modifier.padding(top = CARD_PAD), verticalArrangement = Arrangement.spacedBy(Spacing.two)) {
                controls()
            }
        }
    }
}

@Composable
internal fun ExerciseName(name: String) {
    Text(name, Modifier.padding(top = Spacing.one), style = KettleType.subtitle, color = runnerColors.text)
}

@Composable
internal fun Secondary(text: String) {
    Text(text, style = KettleType.small, color = runnerColors.textSecondary)
}

/** Last time's line with the PR badge beside it. Absent on a first-ever session: nothing to say yet. */
@Composable
internal fun PreviousRow(
    text: String?,
    beatsBest: Boolean,
    adopt: (@Composable () -> Unit)? = null,
) {
    if (text == null) return
    Row(
        Modifier.padding(top = Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        if (adopt != null) adopt() else Secondary(text)
        if (beatsBest) RecordBadge()
    }
}

/**
 * The bottom row on a screen the clock runs: Back, Pause or Resume in the middle, and [exit], the step's
 * Skip or Done, to the side, so the wide button a hand lands on from the floor is never the one that
 * ends the step.
 */
@Composable
internal fun ClockRow(
    context: StepContext,
    exit: RowAction,
) {
    val toggle = { context.send(RunnerCommand.TogglePause) }
    val main =
        if (context.paused) {
            RowAction(stringResource(R.string.session_resume), R.drawable.icon_play, toggle)
        } else {
            RowAction(stringResource(R.string.session_pause), R.drawable.icon_pause, toggle)
        }
    BottomRow(main, back = { context.send(RunnerCommand.Prev) }, side = exit)
}

/**
 * A hold counts up. Its bar spans the top of the range, with the minimum a mark to cross only on a real
 * range, and a max-effort hold, with no scale to draw against, has no bar at all.
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
            StepPill(stringResource(R.string.session_hold_label))
            ExerciseName(step.exerciseName)
            SetCountRow(stringResource(R.string.session_setOf, "${step.setIndex}", "${step.setTotal}"), context)
            StepNotes(step.notes)
            PreviousRow(
                (readings.previous as? PreviousSet.Hold)?.let { resources.previousSetText(it, context.units) },
                readings.beatsBest,
            )
        },
        middle = { HoldClock(step, context.elapsed) },
        controls = {
            ClockRow(context, RowAction(stringResource(R.string.session_done), R.drawable.icon_stop, context::advance))
        },
    )
}

@Composable
private fun HoldClock(
    step: RunnerStep.Hold,
    elapsed: Long,
) {
    val target = step.holdTargetSec
    Row {
        Numeral("$elapsed", HOLD_NUMERAL, Modifier.alignByBaseline())
        Numeral("s", UNIT_NUMERAL, Modifier.alignByBaseline(), runnerColors.textSecondary, leading = null)
    }
    if (target != null) {
        val barSec = step.holdTargetMaxSec?.takeIf { it > target } ?: target
        val marker = target.takeIf { barSec > it }?.let { (it / barSec).toFloat() }
        FillBar(if (barSec > 0) (elapsed / barSec).toFloat() else 0f, marker)
    }
    Secondary(
        holdTarget(step)?.let {
            stringResource(
                R.string.session_hold_caption,
                it.maxSec?.let { max -> "${jsNumber(it.minSec)}–${jsNumber(max)}" } ?: jsNumber(it.minSec),
            )
        } ?: stringResource(R.string.session_hold_captionOpen),
    )
}

/**
 * A rest counts down against its total, which +30s beside the clock moves. Straight after a commit it
 * shows what was logged with Undo, and then no Back: the two would be the same step back. With nothing
 * to undo (a restored session, a rest after a rest), Back takes Undo's place so there's always a way back.
 *
 * No Pause: a step change clears pause (#304), so a live rest never starts paused. A resume after process
 * death does land one paused (#307), and then Resume is the main action, Skip moving to the side, or the
 * clock would sit frozen with only Skip to move it on.
 */
@Composable
internal fun RestScreen(context: StepContext) {
    val logged = context.logged
    val skip = stringResource(R.string.session_rest_skip)
    StepLayout(
        top = { StepPill(stringResource(R.string.session_rest_label), calm = true) },
        middle = {
            Numeral(formatClock(context.remaining), REST_NUMERAL)
            Secondary(stringResource(R.string.session_rest_remaining, formatClock(context.snapshot.clock.stepEndSec)))
            OutlineButton(
                stringResource(R.string.session_rest_add30),
                { context.send(RunnerCommand.AddRestSeconds(REST_BUMP_SEC)) },
                look = ButtonLook.Compact,
            )
        },
        next = context.readings.next,
        controls = {
            if (logged != null) LoggedPanel(logged, context)
            val back = if (logged == null) ({ context.send(RunnerCommand.Prev) }) else null
            if (context.paused) {
                BottomRow(
                    RowAction(stringResource(R.string.session_resume), R.drawable.icon_play) {
                        context.send(RunnerCommand.TogglePause)
                    },
                    back,
                    side = RowAction(stringResource(R.string.session_skip), R.drawable.icon_skip, context::advance),
                )
            } else {
                BottomRow(RowAction(skip, null, context::advance), back)
            }
        },
    )
}

private val HOLD_NUMERAL = 96.sp
private val REST_NUMERAL = 100.sp
private val UNIT_NUMERAL = 38.sp
private const val REST_BUMP_SEC = 30.0
