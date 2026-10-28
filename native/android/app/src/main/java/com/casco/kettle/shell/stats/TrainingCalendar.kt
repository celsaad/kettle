package com.casco.kettle.shell.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CALENDAR_CELL_HEIGHT
import com.casco.kettle.domain.CALENDAR_LONG_MINUTES
import com.casco.kettle.domain.CALENDAR_MID_MINUTES
import com.casco.kettle.domain.CALENDAR_RING_INSET
import com.casco.kettle.domain.CALENDAR_RING_WIDTH
import com.casco.kettle.domain.CalendarBox
import com.casco.kettle.domain.CalendarLevel
import com.casco.kettle.domain.CalendarWeek
import com.casco.kettle.domain.calendarCells
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.i18n.formatNarrowWeekday
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleColors
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import java.util.Locale
import kotlin.math.ceil

/**
 * The last few weeks, a row each and a cell a day, shaded by how long you trained: RN's
 * `training-calendar.tsx` (#201). It keeps the weekly bars' one reading, the week's count in the
 * right-hand column, and adds which days, how long, and where the gaps are. Where the cells go is
 * `calendarCells`' decision in `:shared`; each week's seven are one Canvas.
 *
 * **Each week row is one accessible element**, named with its count and time: twenty-eight cells for a
 * screen reader to step through would be a table read a square at a time, so the cells and the weekday
 * header say nothing, and the row speaks for them.
 *
 * The dates and counts line up in columns although each week is its own row: every label is measured
 * and each column takes the widest, at the reader's text size, as RN's `useWidest` settled on.
 */
@Composable
fun TrainingCalendar(
    weeks: List<CalendarWeek>,
    locale: Locale,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val weekLabels = weeks.map { formatMonthDay(it.weekStart, locale) }

    fun widest(
        texts: List<String>,
        style: TextStyle,
    ): Dp = with(density) { ceil(texts.maxOf { measurer.measure(it, style).size.width }.toDp().value).dp }
    val columns =
        Columns(widest(weekLabels, KettleType.small), widest(weeks.map { it.sessions.toString() }, CountStyle))

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        WeekRow(columns, Modifier.clearAndSetSemantics {}, label = {}, count = {}) {
            weeks.firstOrNull()?.days?.forEach { day ->
                Text(
                    formatNarrowWeekday(day.date, locale),
                    Modifier.weight(1f),
                    style = KettleType.small,
                    color = KettleTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        weeks.zip(weekLabels).forEach { (week, weekLabel) ->
            val time =
                stringResource(
                    R.string.analytics_hoursMinutes,
                    week.time.hours,
                    week.time.minutes,
                )
            val description =
                pluralText(
                    R.plurals.analytics_calendarWeekLabel,
                    R.string.analytics_calendarWeekLabel_zero,
                    week.sessions,
                    time,
                    weekLabel,
                )
            WeekRow(
                columns,
                Modifier.clearAndSetSemantics { contentDescription = description },
                label = {
                    Text(
                        weekLabel,
                        style = KettleType.small,
                        color = KettleTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                },
                count = {
                    Text(
                        week.sessions.toString(),
                        style = CountStyle,
                        color = if (week.sessions > 0) KettleTheme.colors.text else KettleTheme.colors.textSecondary,
                        textAlign = TextAlign.End,
                    )
                },
            ) { WeekCells(week) }
        }

        Legend(columns.label)
    }
}

/** The widths every row's date and count columns share, so they line up although each week is its own row. */
private data class Columns(
    val label: Dp,
    val count: Dp,
)

/** A date column, the seven days between, and a count column. */
@Composable
private fun WeekRow(
    columns: Columns,
    modifier: Modifier,
    label: @Composable () -> Unit,
    count: @Composable () -> Unit,
    days: @Composable RowScope.() -> Unit,
) = Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.width(columns.label)) { label() }
    Row(Modifier.weight(1f), content = days)
    Box(Modifier.width(columns.count), contentAlignment = Alignment.CenterEnd) { count() }
}

/**
 * One week's days. The slot carries today's ring and the cell the shade, so the ring sits around the
 * cell with a gap of card between rather than eating into the shade. A day still to come is an
 * outline, not an empty cell: rest is something you did, and Friday hasn't happened yet.
 */
@Composable
private fun RowScope.WeekCells(week: CalendarWeek) {
    val colors = KettleTheme.colors
    Canvas(
        Modifier
            .weight(1f)
            .height((CALENDAR_CELL_HEIGHT + 2 * CALENDAR_RING_INSET).dp),
    ) {
        calendarCells(size.width / density).zip(week.days).forEach { (cell, day) ->
            if (day.isToday) {
                val half = CALENDAR_RING_WIDTH / 2
                drawBox(cell.slot.inset(half), SLOT_RADIUS - half, colors.text, Stroke(CALENDAR_RING_WIDTH.dp.toPx()))
            }
            if (day.isFuture) {
                drawBox(cell.cell.inset(OUTLINE_WIDTH / 2), CELL_RADIUS, colors.border, Stroke(OUTLINE_WIDTH.dp.toPx()))
            } else {
                drawBox(cell.cell, CELL_RADIUS, colors.level(day.level))
            }
        }
    }
}

private fun DrawScope.drawBox(
    box: CalendarBox,
    radius: Float,
    color: Color,
    stroke: Stroke? = null,
) {
    val topLeft = Offset(box.x.dp.toPx(), box.y.dp.toPx())
    val size = Size(box.width.dp.toPx(), box.height.dp.toPx())
    val corner = CornerRadius(radius.dp.toPx())
    if (stroke == null) {
        drawRoundRect(color, topLeft, size, corner)
    } else {
        drawRoundRect(color, topLeft, size, corner, style = stroke)
    }
}

/** A stroke is centred on its path, so a box drawn [by] in keeps its stroke inside the original. */
private fun CalendarBox.inset(by: Float) = CalendarBox(x + by, y + by, width - 2 * by, height - 2 * by)

/** Under the first cell rather than under the dates, so the swatches read as the grid's. */
@Composable
private fun Legend(labelWidth: Dp) {
    val items =
        listOf(
            CalendarLevel.Rest to stringResource(R.string.analytics_calendarRest),
            CalendarLevel.Short to stringResource(R.string.analytics_calendarShort, CALENDAR_MID_MINUTES),
            CalendarLevel.Mid to
                stringResource(R.string.analytics_calendarMid, CALENDAR_MID_MINUTES, CALENDAR_LONG_MINUTES),
            CalendarLevel.Long to stringResource(R.string.analytics_calendarLong, CALENDAR_LONG_MINUTES),
        )
    FlowRow(
        Modifier.padding(start = labelWidth + Spacing.two, top = Spacing.two),
        horizontalArrangement = Arrangement.spacedBy(Spacing.three - 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.one),
    ) {
        items.forEach { (level, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decorative geometry, so a fixed size is right.
                Box(
                    Modifier
                        .size(SWATCH)
                        .background(KettleTheme.colors.level(level), RoundedCornerShape(3.dp)),
                )
                Text(label, style = KettleType.small, color = KettleTheme.colors.textSecondary)
            }
        }
    }
}

private fun KettleColors.level(level: CalendarLevel) =
    when (level) {
        CalendarLevel.Rest -> calendarEmpty
        CalendarLevel.Short -> calendarLevel1
        CalendarLevel.Mid -> calendarLevel2
        CalendarLevel.Long -> calendarLevel3
    }

/** Tabular, so a 1 and a 12 line up down a column too narrow for the eye to forgive it. */
private val CountStyle: TextStyle = KettleType.smallMedium.copy(fontFeatureSettings = "tnum")

private const val SLOT_RADIUS = 6f
private const val CELL_RADIUS = 4f
private const val OUTLINE_WIDTH = 1f
private val SWATCH = 10.dp
