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
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.PreviousSet
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.StepPreview
import com.casco.kettle.domain.holdTarget
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

/** Top, middle, the Next card and the controls, spread down the screen: no scroll, as RN's. */
@Composable
internal fun StepLayout(
    top: @Composable ColumnScope.() -> Unit,
    middle: @Composable ColumnScope.() -> Unit,
    next: StepPreview?,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.two), content = top)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.two),
            content = middle,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.three)) {
            NextCard(next)
            controls()
        }
    }
}

@Composable
internal fun ExerciseName(name: String) {
    Text(name, style = KettleType.subtitle, color = RunnerColors.text)
}

@Composable
internal fun Secondary(text: String) {
    Text(text, style = KettleType.small, color = RunnerColors.textSecondary)
}

/** Last time's line with the PR badge beside it. Absent on a first-ever session: nothing to say yet. */
@Composable
internal fun PreviousRow(
    text: String?,
    beatsBest: Boolean,
    adopt: (@Composable () -> Unit)? = null,
) {
    if (text == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        if (adopt != null) adopt() else Secondary(text)
        if (beatsBest) RecordBadge()
    }
}

/** Back and Pause, the hold and interval screens' row. */
@Composable
internal fun PauseRow(context: StepContext) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.three), verticalAlignment = Alignment.CenterVertically) {
        PrevIconButton { context.send(RunnerCommand.Prev) }
        OutlineButton(
            stringResource(if (context.paused) R.string.session_resume else R.string.session_pause),
            { context.send(RunnerCommand.TogglePause) },
            Modifier.weight(1f),
        )
    }
}

/** Prev as words, on the screens with no clock to pause. */
@Composable
internal fun PrevTextButton(context: StepContext) {
    OutlineButton(
        stringResource(R.string.session_prev),
        { context.send(RunnerCommand.Prev) },
        style = QuietStyle,
    )
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
        next = readings.next,
        controls = {
            PauseRow(context)
            PrimaryButton(stringResource(R.string.session_hold_doneSet), context::advance)
        },
    )
}

@Composable
private fun HoldClock(
    step: RunnerStep.Hold,
    elapsed: Long,
) {
    val target = step.holdTargetSec
    Row(verticalAlignment = Alignment.Bottom) {
        Numeral("$elapsed", BIG_NUMERAL)
        Numeral("s", UNIT_NUMERAL, Modifier.padding(bottom = Spacing.three), RunnerColors.textSecondary)
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
 * A rest counts down against its total, which +30s moves. No Pause: a step change clears pause (#304),
 * so a live rest never starts paused. A resume after process death does land one paused (#307), and
 * then Resume takes +30s's place, or the clock would sit frozen with only Skip to move it on.
 */
@Composable
internal fun RestScreen(context: StepContext) {
    StepLayout(
        top = { StepPill(stringResource(R.string.session_rest_label), calm = true) },
        middle = {
            Numeral(formatClock(context.remaining), BIG_NUMERAL)
            Secondary(stringResource(R.string.session_rest_remaining, formatClock(context.snapshot.clock.stepEndSec)))
        },
        next = context.readings.next,
        controls = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                PrevTextButton(context)
                if (context.paused) {
                    OutlineButton(
                        stringResource(R.string.session_resume),
                        { context.send(RunnerCommand.TogglePause) },
                        Modifier.weight(1f),
                    )
                } else {
                    OutlineButton(
                        stringResource(R.string.session_rest_add30),
                        { context.send(RunnerCommand.AddRestSeconds(REST_BUMP_SEC)) },
                        Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    stringResource(R.string.session_rest_skip),
                    context::advance,
                    Modifier.weight(SKIP_WEIGHT),
                )
            }
        },
    )
}

internal val BIG_NUMERAL = 96.sp
private val UNIT_NUMERAL = 40.sp
private const val REST_BUMP_SEC = 30.0
private const val SKIP_WEIGHT = 1.4f
