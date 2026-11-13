package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep
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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.two), content = controls)
    }
}

/**
 * A step's header (#409): the label line, its kind and where it sits, then the name in the 26sp title,
 * absent on a rest, whose header says what's next instead.
 */
@Composable
internal fun StepHeader(
    label: String,
    name: String?,
    spoken: String? = null,
) {
    // A circuit's label says its round; [spoken] adds the member, which nothing else on screen does.
    val described = if (spoken == null) Modifier else Modifier.semantics { contentDescription = spoken }
    Text(label, described, style = SlotLabel, color = runnerColors.textSecondary)
    if (name != null) ExerciseName(name)
}

@Composable
internal fun ExerciseName(name: String) {
    Text(name, Modifier.padding(top = Spacing.one), style = KettleType.subtitle, color = runnerColors.text)
}

@Composable
internal fun Secondary(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, style = KettleType.small, color = runnerColors.textSecondary)
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
    BottomRow(pauseAction(context), back = { context.send(RunnerCommand.Prev) }, side = exit)
}

/**
 * A rest's header: its label, then what comes next in the name's place, which on the canvas replaced
 * the Next card the bottom of the rest screen used to carry. Nothing next names nothing.
 */
@Composable
private fun RestHeader(
    step: RunnerStep,
    context: StepContext,
) {
    val next = context.readings.next
    val resources = LocalResources.current
    StepHeader(resources.stepLabel(step, context.snapshot.inputs), null, resources.circuitSpoken(step))
    if (next == null) return
    Text(
        stringResource(R.string.session_header_next, next.label),
        Modifier.padding(top = Spacing.one),
        style = KettleType.heading,
        color = runnerColors.text,
    )
    Secondary(resources.previewDetail(next))
}

/**
 * A rest counts down against its total, which −15s and +30s under the clock move, where the canvas draws them.
 * Straight after a commit it shows what was logged with Undo, and then no Back: the two would be the
 * same step back. With nothing to undo (a restored session, a set that logged nothing, an edit since),
 * Back takes Undo's place so there's always a way back.
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
        top = { context.snapshot.step?.let { RestHeader(it, context) } },
        middle = {
            Numeral(formatClock(context.remaining), REST_NUMERAL)
            Secondary(stringResource(R.string.session_rest_remaining, formatClock(context.snapshot.clock.stepEndSec)))
            Row(Modifier.padding(top = Spacing.two), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                val width = Modifier.widthIn(min = REST_BUTTON_MIN_WIDTH)
                OutlineButton(
                    stringResource(R.string.session_rest_minus15),
                    { context.send(RunnerCommand.AddRestSeconds(-REST_CUT_SEC)) },
                    width,
                    look = ButtonLook.Compact,
                )
                OutlineButton(
                    stringResource(R.string.session_rest_add30),
                    { context.send(RunnerCommand.AddRestSeconds(REST_BUMP_SEC)) },
                    width,
                    look = ButtonLook.Compact,
                )
            }
        },
        controls = {
            if (logged != null) LoggedPanel(logged, context)
            val back = if (logged == null) ({ context.send(RunnerCommand.Prev) }) else null
            if (context.paused) {
                BottomRow(pauseAction(context), back, side = skipAction(context))
            } else {
                BottomRow(RowAction(skip, null, context::advance), back)
            }
        },
    )
}

private val REST_NUMERAL = 100.sp
private const val REST_BUMP_SEC = 30.0

/** −15s (#417): cut past what's left, the rest ends on the next tick, as it would have at its end. */
private const val REST_CUT_SEC = 15.0

/** The canvas's 84dp: a short label like "+30s" was a pill narrower than a finger. */
private val REST_BUTTON_MIN_WIDTH = 84.dp
