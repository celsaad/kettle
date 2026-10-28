package com.casco.kettle.domain

// Where Stats' two Canvas drawings put things, in dp, ported from the flexbox of RN's `sparkline.tsx`
// and `training-calendar.tsx`. Split from the drawing so the rules their comments state are tested
// here; how they look is the running app's to show.

/** The sparkline's height. Decorative geometry with no text in it, so a fixed height is right. */
const val SPARKLINE_HEIGHT = 20f

/** Every bar's width: the bars differ only in height. */
const val SPARKLINE_BAR_WIDTH = 4f
private const val BAR_GAP = 2f
private const val BAR_FLOOR = 2f

/**
 * How many sessions a sparkline shows: the most recent, so every row on Stats is the same width, and
 * the trend a reader wants is the recent end.
 */
private const val MAX_BARS = 7

/**
 * One bar, bottom-aligned and [SPARKLINE_BAR_WIDTH] wide: [x] is its left edge and [height] runs up
 * from the drawing's bottom.
 */
data class SparkBar(
    val x: Float,
    val height: Float,
    /** The most recent session's, the one the row is about; the rest are context. */
    val latest: Boolean,
)

/**
 * An exercise's recent values as a word-sized bar chart with no axes: it sits in a row that already
 * prints the latest value and the change, so it carries shape and nothing else.
 *
 * **Scaled from zero, not from the minimum**, which is how a sparkline lies: 10, 10.5 and 11 kg would
 * draw as an empty bar and a full one. From zero a small gain looks small, which the delta beside it
 * confirms. A bar never drops below 2, so a genuine zero reads as a session that happened.
 */
fun sparklineBars(points: List<Double>): List<SparkBar> {
    val shown = points.takeLast(MAX_BARS)
    val max = maxOf(shown.maxOrNull() ?: 0.0, 0.0)
    return shown.mapIndexed { index, value ->
        val height =
            if (max <=
                0
            ) {
                BAR_FLOOR
            } else {
                maxOf(BAR_FLOOR, roundHalfUp(value / max * SPARKLINE_HEIGHT).toFloat())
            }
        SparkBar(index * (SPARKLINE_BAR_WIDTH + BAR_GAP), height, latest = index == shown.lastIndex)
    }
}

/** How wide a sparkline of [count] points draws, so the row can give it exactly that. */
fun sparklineWidth(count: Int): Float {
    val bars = minOf(count, MAX_BARS)
    return if (bars == 0) 0f else bars * SPARKLINE_BAR_WIDTH + (bars - 1) * BAR_GAP
}

/** A day cell's height. Fixed, as the sparkline's: a cell has no text in it, so nothing clips. */
const val CALENDAR_CELL_HEIGHT = 24f

/** Today's ring's width, which every slot reserves so ringing a day moves nothing. */
const val CALENDAR_RING_WIDTH = 1.5f

/** The ring's width plus the gap of card between it and the cell. */
const val CALENDAR_RING_INSET = CALENDAR_RING_WIDTH + 1f

data class CalendarBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** One day: [slot] carries today's ring and [cell] the shade, inside it. */
data class CalendarCell(
    val slot: CalendarBox,
    val cell: CalendarBox,
)

/** One week's seven days across a row [width] wide, as RN's seven `flex: 1` slots. */
fun calendarCells(width: Float): List<CalendarCell> {
    val slotWidth = width / DAYS_PER_WEEK
    return (0 until DAYS_PER_WEEK).map { day ->
        val x = day * slotWidth
        CalendarCell(
            slot = CalendarBox(x, 0f, slotWidth, CALENDAR_CELL_HEIGHT + 2 * CALENDAR_RING_INSET),
            cell =
                CalendarBox(
                    x + CALENDAR_RING_INSET,
                    CALENDAR_RING_INSET,
                    slotWidth - 2 * CALENDAR_RING_INSET,
                    CALENDAR_CELL_HEIGHT,
                ),
        )
    }
}
