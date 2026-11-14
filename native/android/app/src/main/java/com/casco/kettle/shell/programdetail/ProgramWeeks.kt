package com.casco.kettle.shell.programdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.GridSlot
import com.casco.kettle.domain.GridWeek
import com.casco.kettle.domain.ProgramPosition
import com.casco.kettle.domain.SlotPlace
import com.casco.kettle.domain.programGrid
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleColors
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The program's weeks (#437): a tab per week, each slot a square placed by position, not completion,
 * as Today's strip (#412). Earlier is filled, next a ring round a dot (a shape, not only a colour),
 * later an outline, rest a dash, and a dot under a slot the program changes. Then the selected week's
 * days as rows. It opens on the week the program stands in.
 */
@Composable
internal fun ProgramWeeks(
    position: ProgramPosition,
    slot: SlotActions,
) {
    val grid = remember(position) { programGrid(position) }
    val current = position.weekNumber
    var selected by rememberSaveable(position.program.id) { mutableLongStateOf(current) }
    val colors = KettleTheme.colors
    Column {
        Heading(stringResource(R.string.programDetail_weeks), stringResource(R.string.programDetail_weeksHint))
        Column(Modifier.selectableGroup()) {
            grid.forEach { week ->
                WeekTab(week, current, week.week == selected) { selected = week.week }
            }
        }
        Legend(colors)
    }
    grid.firstOrNull { it.week == selected }?.let { DayList(it, current, slot) }
}

/** A section's heading, and what it covers on the right. */
@Composable
internal fun Heading(
    text: String,
    aside: String?,
) = Row(Modifier.padding(top = Spacing.two, bottom = Spacing.one), verticalAlignment = Alignment.Bottom) {
    Text(text, Modifier.weight(1f).semantics { heading() }, style = KettleType.heading, color = KettleTheme.colors.text)
    aside?.let { Text(it, style = KettleType.small, color = KettleTheme.colors.textSecondary) }
}

/**
 * One week's tab: its label, its slots drawn, and "Earlier" or "This week". The drawing is decoration;
 * the tab says its week, where it stands, and how many of its days the program changes.
 */
@Composable
private fun WeekTab(
    week: GridWeek,
    current: Long,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KettleTheme.colors
    val label = stringResource(R.string.programs_week, week.week)
    val status =
        when {
            week.week < current -> stringResource(R.string.programDetail_earlier)
            week.week == current -> stringResource(R.string.programDetail_thisWeek)
            else -> null
        }
    val changed = week.slots.count { it.changed }
    val changedText =
        pluralText(R.plurals.programDetail_changedDays, R.string.programDetail_changedDays_zero, changed).takeIf {
            changed >
                0
        }
    val description = listOfNotNull(label, status, changedText).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(TabShape)
            .background(if (selected) colors.backgroundSelected else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = Spacing.two),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(WeekLabelWidth), style = KettleType.smallMedium, color = colors.text)
        Squares(week.slots, colors)
        Text(status.orEmpty(), Modifier.weight(1f), style = KettleType.small, color = colors.textSecondary)
    }
}

/** The week's slots, drawn: one square each, and a dot under a changed one. */
@Composable
private fun Squares(
    slots: List<GridSlot>,
    colors: KettleColors,
) {
    Canvas(Modifier.size(width = (SQUARE + GAP) * slots.size, height = SQUARE + DOT_ROOM)) {
        slots.forEachIndexed { i, slot ->
            val left = (SQUARE + GAP).toPx() * i
            drawSlot(slot, Offset(left, 0f), colors)
            if (slot.changed) {
                drawCircle(
                    colors.text,
                    DOT.toPx() / 2,
                    Offset(
                        left + SQUARE.toPx() / 2,
                        (SQUARE + DOT_ROOM / 2).toPx() + 1f,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawSlot(
    slot: GridSlot,
    topLeft: Offset,
    colors: KettleColors,
) {
    val side = SQUARE.toPx()
    val corner = CornerRadius(CORNER.toPx())
    if (slot.rest) {
        val dash = Size(side * DASH_WIDTH, DASH_HEIGHT.toPx())
        drawRect(
            colors.calendarEmpty,
            Offset(topLeft.x + (side - dash.width) / 2, topLeft.y + (side - dash.height) / 2),
            dash,
        )
        // The next slot ringed even when it's a rest, so the grid always shows where the program stands.
        if (slot.place == SlotPlace.Current) {
            val stroke = RING.toPx()
            drawRoundRect(
                colors.text,
                topLeft + Offset(stroke / 2, stroke / 2),
                Size(side - stroke, side - stroke),
                corner,
                style = Stroke(stroke),
            )
        }
        return
    }
    when (slot.place) {
        SlotPlace.Behind -> {
            drawRoundRect(colors.text, topLeft, Size(side, side), corner)
        }

        SlotPlace.Current -> {
            val stroke = RING.toPx()
            drawRoundRect(
                colors.text,
                topLeft + Offset(stroke / 2, stroke / 2),
                Size(side - stroke, side - stroke),
                corner,
                style = Stroke(stroke),
            )
            drawCircle(colors.text, CENTRE.toPx() / 2, topLeft + Offset(side / 2, side / 2))
        }

        SlotPlace.Ahead -> {
            val stroke = OUTLINE.toPx()
            drawRoundRect(
                colors.calendarEmpty,
                topLeft + Offset(stroke / 2, stroke / 2),
                Size(side - stroke, side - stroke),
                corner,
                style = Stroke(stroke),
            )
        }
    }
}

/** What each mark means, under the grid: the shapes in words. */
@Composable
private fun Legend(colors: KettleColors) {
    val items =
        listOf(
            SlotPlace.Behind to R.string.programDetail_earlier,
            SlotPlace.Current to R.string.programDetail_nextTag,
            SlotPlace.Ahead to R.string.programDetail_later,
        )
    FlowRow(
        Modifier.padding(top = Spacing.two).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        items.forEach { (place, label) ->
            LegendItem(stringResource(label)) { drawSlot(GridSlot(0, LegendSlot, place), Offset.Zero, colors) }
        }
        LegendItem(stringResource(R.string.programDetail_rest)) {
            drawSlot(GridSlot(0, LegendRest, SlotPlace.Ahead), Offset.Zero, colors)
        }
        LegendItem(stringResource(R.string.programDetail_changed)) {
            drawCircle(colors.text, DOT.toPx() / 2, Offset(SQUARE.toPx() / 2, SQUARE.toPx() / 2))
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    mark: DrawScope.() -> Unit,
) = Row(horizontalArrangement = Arrangement.spacedBy(Spacing.one), verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(SQUARE), onDraw = mark)
    Text(label, style = KettleType.small, color = KettleTheme.colors.textSecondary)
}

private val LegendSlot =
    com.casco.kettle.domain.ProgramWeek
        .Training(1, null, "")
private val LegendRest =
    com.casco.kettle.domain.ProgramWeek
        .RestDay(1)

private val TabShape = RoundedCornerShape(10.dp)
private val WeekLabelWidth = 72.dp
private val SQUARE = 16.dp
private val GAP = 6.dp
private val DOT = 5.dp
private val DOT_ROOM = 8.dp
private val CORNER = 4.dp
private val RING = 2.dp
private val OUTLINE = 1.5.dp
private val CENTRE = 6.dp
private val DASH_HEIGHT = 2.dp
private const val DASH_WIDTH = 0.6f
