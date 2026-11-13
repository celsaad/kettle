package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UpcomingItem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.timeline.OutlineItem
import com.casco.kettle.shell.timeline.TimelineColors
import com.casco.kettle.shell.timeline.TimelineItem

/**
 * Coming up (§3.6): a read-only timeline of what's left. Its NOW row shows the live clock: a rest
 * counting down against its total, a hold up, nothing for reps. An ad-hoc session has no end node.
 */
@Composable
internal fun ComingUp(
    step: RunnerStep,
    items: List<UpcomingItem>,
    ends: Boolean,
    context: StepContext,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.session_upcoming_title)
    val close = stringResource(R.string.common_close)
    // The backdrop is hidden from a reader, as RN's: the × already closes, and a second "Close"
    // covering the whole screen would only be one more stop to swipe past.
    OverlaySheet(null, onClose, title, inset = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f).semantics { heading() },
                style = KettleType.code.copy(letterSpacing = CapsTracking),
                color = RunnerColors.textSecondary,
            )
            SymbolButton(R.drawable.icon_close, close, onClose, look = SymbolLook.Close)
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            item { NowRow(step, context, last = items.isEmpty() && !ends) }
            items(items.size) { index ->
                OutlineItem(items[index], ComingUpColors, last = !ends && index == items.size - 1)
            }
            if (ends) {
                item {
                    TimelineItem(ComingUpColors, RunnerColors.textSecondary, last = true) {
                        Caps(stringResource(R.string.session_upcoming_end))
                    }
                }
            }
        }
    }
}

@Composable
private fun NowRow(
    step: RunnerStep,
    context: StepContext,
    last: Boolean,
) {
    val resources = LocalResources.current
    val resting = step is RunnerStep.Rest
    val clock =
        when (step) {
            is RunnerStep.Rest -> context.remaining
            is RunnerStep.Hold -> context.elapsed.toDouble()
            is RunnerStep.Interval -> if (step.countUp) context.elapsed.toDouble() else context.remaining
            is RunnerStep.Reps -> null
        }
    TimelineItem(
        ComingUpColors,
        tint = if (resting) RunnerColors.accentCalm else RunnerColors.accent,
        last = last,
        now = true,
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(if (resting) R.string.session_upcoming_nowRest else R.string.session_upcoming_now),
                style = KettleType.code,
                color = if (resting) RunnerColors.accentCalmOnSoft else RunnerColors.accentOnSoft,
            )
            if (step is WorkStep) Text(step.exerciseName, style = KettleType.heading, color = RunnerColors.text)
            if (clock != null) {
                Numeral(
                    formatClock(clock),
                    32.sp,
                    color = if (resting) RunnerColors.accentCalmOnSoft else RunnerColors.accentOnSoft,
                )
            }
            Text(
                resources.nowCaption(step, context.snapshot.clock.stepEndSec),
                style = KettleType.small,
                color = RunnerColors.textSecondary,
            )
        }
    }
}

private val ComingUpColors =
    TimelineColors(
        text = RunnerColors.text,
        textSecondary = RunnerColors.textSecondary,
        rail = RunnerColors.border,
        circuit = RunnerColors.accentCalm,
        kicker = RunnerColors.accentCalmOnSoft,
    )
