package com.casco.kettle.shell.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.UpcomingItem
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.weightSuffix
import com.casco.kettle.shell.runner.formatClock
import com.casco.kettle.shell.runner.upcomingDetail
import com.casco.kettle.shell.runner.upcomingTarget
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The timeline both read-only outlines draw, the runner's Coming up and Home's preview (#280), so the
 * two can't drift into looking like different things. Only the colours differ: the runner is always
 * dark, Home follows the scheme.
 */
@Immutable
data class TimelineColors(
    val text: Color,
    val textSecondary: Color,
    val rail: Color,
    val circuit: Color,
    val kicker: Color,
)

/**
 * One outline item: its node, tinted for a circuit, and its rows. [preview] is Home's preview's units,
 * null in the runner. Given, it adds a reps step's target weight, since the preview's question is what
 * to pick up, which the runner's sheet, with the set on screen, doesn't have to answer; and it counts a
 * circuit's rounds rather than what's left of one, since nothing has started.
 */
@Composable
fun OutlineItem(
    item: UpcomingItem,
    colors: TimelineColors,
    last: Boolean,
    preview: UnitSystem? = null,
) {
    val circuit = item !is UpcomingItem.Exercise && item !is UpcomingItem.Rest
    TimelineItem(colors.rail, if (circuit) colors.circuit else colors.textSecondary, last) {
        UpcomingEntry(item, colors, preview)
    }
}

@Composable
private fun UpcomingEntry(
    item: UpcomingItem,
    colors: TimelineColors,
    preview: UnitSystem?,
) {
    val resources = LocalResources.current
    when (item) {
        is UpcomingItem.Exercise -> {
            Entry(item.step.exerciseName, resources.upcomingDetail(item) + weightText(item.step, preview), colors)
        }

        is UpcomingItem.Rest -> {
            Entry(stringResource(R.string.session_upcoming_rest), formatClock(item.seconds), colors)
        }

        is UpcomingItem.Round -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
                Kicker(roundKicker(item, preview), colors)
                for (member in item.members) {
                    Entry(
                        member.step.exerciseName,
                        resources.upcomingTarget(member.step) + weightText(member.step, preview),
                        colors,
                    )
                }
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
                colors,
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
                colors,
            )
        }
    }
}

/**
 * A round's kicker. In the preview it counts the circuit's rounds, nothing having started; in the
 * runner a one-round circuit counts what's left, and any other names the round you're in.
 */
@Composable
private fun roundKicker(
    item: UpcomingItem.Round,
    preview: UnitSystem?,
): String =
    when {
        preview != null -> {
            pluralText(
                R.plurals.session_upcoming_circuitRounds,
                R.string.session_upcoming_circuitRounds_zero,
                item.rounds.toInt(),
            )
        }

        item.rounds == 1L -> {
            pluralText(
                R.plurals.session_upcoming_circuitLeft,
                R.string.session_upcoming_circuitLeft_zero,
                item.members.size,
            )
        }

        else -> {
            stringResource(R.string.session_circuit_crumb, "${item.round}", "${item.rounds}")
        }
    }

/** A reps step's target weight in the preview's units; nothing outside the preview. */
@Composable
private fun weightText(
    step: WorkStep,
    preview: UnitSystem?,
): String = preview?.let { weightSuffix((step as? RunnerStep.Reps)?.targetWeightKg, it) }.orEmpty()

@Composable
private fun Entry(
    name: String,
    detail: String,
    colors: TimelineColors,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(name, style = KettleType.heading, color = colors.text)
        Text(detail, style = KettleType.small, color = colors.textSecondary)
    }
}

/** Rounds or a circuit folded into one item: its kicker, then the names it holds. */
@Composable
private fun Collapsed(
    kicker: String,
    names: List<String>,
    colors: TimelineColors,
) {
    Column {
        Kicker(kicker, colors)
        for (name in names) Text(name, style = KettleType.default, color = colors.text)
    }
}

@Composable
private fun Kicker(
    text: String,
    colors: TimelineColors,
) {
    Text(text, Modifier.semantics { heading() }, style = KettleType.code, color = colors.kicker)
}

/**
 * One timeline item: its node on the rail, the line through it, and the item beside them. The line is
 * drawn behind the whole row's height, top edge to bottom, so it meets the next item's line whatever
 * the item's height: a fixed-length line stopped short under any item taller than it, and left a gap
 * above the next node besides. The NOW item, always the first, starts its line at its node; the last
 * ends its line there.
 */
@Composable
fun TimelineItem(
    rail: Color,
    tint: Color,
    last: Boolean,
    now: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val node = if (now) 12.dp else 8.dp
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.three)) {
        Box(
            Modifier
                .width(16.dp)
                .fillMaxHeight()
                .drawBehind {
                    val middle = (Spacing.one + node / 2).toPx()
                    val top = if (now) middle else 0f
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
