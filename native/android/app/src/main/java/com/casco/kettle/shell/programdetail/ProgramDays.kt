package com.casco.kettle.shell.programdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.GridSlot
import com.casco.kettle.domain.GridWeek
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.SlotPlace
import com.casco.kettle.domain.overrideNote
import com.casco.kettle.shell.R
import com.casco.kettle.shell.home.weekLabel
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.RowStartButton
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The selected week's days (#437): each its label, its workout, where it stands ("Earlier", or a Next
 * tag) and what the program changes in it. A tap opens its preview, and a training day keeps its own
 * Start, as agreed, so any day can still be run out of turn. A rest day is a plain row.
 */
@Composable
internal fun DayList(
    week: GridWeek,
    current: Long,
    slot: SlotActions,
) {
    val next = week.slots.indexOfFirst { it.place == SlotPlace.Current }
    // Its own column, so the screen's spacing between sections doesn't land between the rows too.
    Column {
        Heading(
            stringResource(R.string.programs_week, week.week),
            stringResource(R.string.programDetail_dayOfNext, week.slots.size, next + 1).takeIf {
                week.week == current &&
                    next >= 0
            },
        )
        ListHeaderRule()
        week.slots.forEachIndexed { n, day ->
            if (n > 0) ListRowSeparator()
            DayRow(day, n + 1, slot)
        }
    }
}

@Composable
private fun DayRow(
    day: GridSlot,
    number: Int,
    slot: SlotActions,
) {
    val colors = KettleTheme.colors
    // User data, verbatim; an empty `day` is none, as the schema takes `day: ""`.
    val label = day.slot.day?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.programDetail_dayN, number)
    val training = day.slot as? ProgramWeek.Training
    val preview = stringResource(R.string.programDetail_rowPreview)
    Box(
        if (training !=
            null
        ) {
            Modifier.clickable(role = Role.Button, onClickLabel = preview) { slot.preview(training) }
        } else {
            Modifier
        },
    ) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.half)) {
                Text(label, style = KettleType.smallMedium, color = colors.textSecondary)
                Text(
                    training?.let(slot::workoutName) ?: stringResource(R.string.programDetail_restDay),
                    style = KettleType.heading,
                    color = if (training != null) colors.text else colors.textSecondary,
                )
                training?.let { Changes(it, slot) }
                // A rest day has no preview to carry its note, so it says it here.
                if (training == null) {
                    day.slot.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = KettleType.small,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Place(day.place)
            training?.let {
                // Named by week and day, as the Next card's label is: a bare "Start Monday" says which Monday.
                RowStartButton(stringResource(R.string.programDetail_startAccessibility, weekLabel(it.week, it.day))) {
                    slot.run(it)
                }
            }
        }
    }
}

/** One line per override, in the shared format ("Sets 4 → 5 · Ring Pull-up"). */
@Composable
private fun Changes(
    training: ProgramWeek.Training,
    slot: SlotActions,
) {
    val workout = slot.library.workouts.firstOrNull { it.id == training.workoutId }
    training.overrides.orEmpty().forEach { override ->
        overrideLines(overrideNote(override, slot.library, workout)).forEach {
            Text(
                it,
                style = KettleType.small,
                color = KettleTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Earlier" in words, or the Next tag in the accent; nothing for a day still ahead. */
@Composable
private fun Place(place: SlotPlace) {
    val colors = KettleTheme.colors
    when (place) {
        SlotPlace.Behind -> {
            Text(stringResource(R.string.programDetail_earlier), style = KettleType.small, color = colors.textSecondary)
        }

        SlotPlace.Current -> {
            Text(
                stringResource(R.string.programDetail_nextTag),
                Modifier
                    .background(
                        colors.accentText,
                        TagShape,
                    ).padding(horizontal = Spacing.one + 2.dp, vertical = Spacing.half),
                style = KettleType.smallMedium,
                color = colors.background,
            )
        }

        SlotPlace.Ahead -> {}
    }
}

private val TagShape = RoundedCornerShape(6.dp)
