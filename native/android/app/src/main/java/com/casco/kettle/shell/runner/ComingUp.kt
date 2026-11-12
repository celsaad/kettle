package com.casco.kettle.shell.runner

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UpcomingItem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

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
    val resources = LocalResources.current
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
                val item = items[index]
                val circuit = item !is UpcomingItem.Exercise && item !is UpcomingItem.Rest
                RailItem(
                    tint = if (circuit) RunnerColors.accentCalm else RunnerColors.textSecondary,
                    last = !ends && index == items.size - 1,
                ) {
                    UpcomingEntry(item, resources)
                }
            }
            if (ends) {
                item {
                    RailItem(RunnerColors.textSecondary, last = true) {
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
    RailItem(
        tint = if (resting) RunnerColors.accentCalm else RunnerColors.accent,
        last = last,
        first = true,
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

@Composable
private fun UpcomingEntry(
    item: UpcomingItem,
    resources: Resources,
) {
    when (item) {
        is UpcomingItem.Exercise -> {
            Entry(item.step.exerciseName, resources.upcomingDetail(item))
        }

        is UpcomingItem.Rest -> {
            Entry(stringResource(R.string.session_upcoming_rest), formatClock(item.seconds))
        }

        is UpcomingItem.Round -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
                Kicker(
                    if (item.rounds == 1L) {
                        pluralText(
                            R.plurals.session_upcoming_circuitLeft,
                            R.string.session_upcoming_circuitLeft_zero,
                            item.members.size,
                        )
                    } else {
                        stringResource(R.string.session_circuit_crumb, "${item.round}", "${item.rounds}")
                    },
                )
                for (member in item.members) Entry(member.step.exerciseName, resources.upcomingTarget(member.step))
            }
        }

        is UpcomingItem.MoreRounds -> {
            Collapsed(
                pluralText(
                    R.plurals.session_upcoming_roundsAfter,
                    R.string.session_upcoming_roundsAfter_zero,
                    item.count,
                ),
                item.names,
            )
        }

        is UpcomingItem.Circuit -> {
            Collapsed(
                pluralText(
                    R.plurals.session_upcoming_circuitRounds,
                    R.string.session_upcoming_circuitRounds_zero,
                    item.rounds,
                ),
                item.names,
            )
        }
    }
}

@Composable
private fun Entry(
    name: String,
    detail: String,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(name, style = KettleType.heading, color = RunnerColors.text)
        Text(detail, style = KettleType.small, color = RunnerColors.textSecondary)
    }
}

/** Rounds or a circuit folded into one item: its kicker, then the names it holds. */
@Composable
private fun Collapsed(
    kicker: String,
    names: List<String>,
) {
    Column {
        Kicker(kicker)
        for (name in names) Text(name, style = KettleType.default, color = RunnerColors.text)
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, Modifier.semantics { heading() }, style = KettleType.code, color = RunnerColors.accentCalmOnSoft)
}

/**
 * One timeline item: its node on the rail, the line through it, and the item beside them. The line is
 * drawn behind the whole row's height, top edge to bottom, so it meets the next item's line whatever
 * the item's height: a fixed-length line stopped short under any item taller than it, and left a gap
 * above the next node besides. The first item's starts at its node, the last's ends there.
 */
@Composable
private fun RailItem(
    tint: Color,
    last: Boolean,
    first: Boolean = false,
    now: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val node = if (now) 12.dp else 8.dp
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.three)) {
        val rail = RunnerColors.border
        Box(
            Modifier
                .width(16.dp)
                .fillMaxHeight()
                .drawBehind {
                    val middle = (Spacing.one + node / 2).toPx()
                    val top = if (first) middle else 0f
                    val bottom = if (last) middle else size.height
                    if (bottom > top) {
                        drawLine(rail, Offset(center.x, top), Offset(center.x, bottom), strokeWidth = 1.dp.toPx())
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .padding(top = Spacing.one)
                    .size(node)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
        Box(Modifier.padding(bottom = if (last) 0.dp else Spacing.three), content = content)
    }
}
