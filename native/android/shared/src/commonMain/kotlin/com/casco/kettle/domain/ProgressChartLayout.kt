package com.casco.kettle.domain

import kotlinx.datetime.Instant
import kotlin.math.abs

/** The drawing's height. Fixed: nothing in it is a control, and every number is in the list below it. */
const val CHART_HEIGHT = 168f

/** Room above the plot for the value labels. */
private const val PAD_TOP = 20f

/** Room below the baseline for the dates. */
private const val PAD_BOTTOM = 24f

/** Keeps an end dot's ring inside the drawing rather than clipped at its edge. */
private const val PAD_X = 6f

/** How far above its point a value label's baseline sits. */
private const val LABEL_RISE = 10f

/** How far a value label keeps clear of its point sideways: the line through it is 2 wide. */
private const val LABEL_NUDGE = 4f

/** A label's height at the chart's 11, with a little room: two closer than this vertically can collide. */
private const val LABEL_HEIGHT = 14f

/** The least clear space between two labels side by side. */
private const val LABEL_GAP = 6f

/**
 * [NewBest] for a session that beat everything before it; [Latest] for the most recent when it didn't,
 * drawn differently so a dip at the end can't be read against the key's "New best".
 */
enum class ChartMark { NewBest, Latest }

data class ChartPoint(
    val x: Float,
    val y: Float,
    val at: Instant,
    val mark: ChartMark?,
)

/** A label's left edge and baseline. */
data class ChartLabel(
    val x: Float,
    val y: Float,
)

data class ChartLayout(
    val baseline: Float,
    val points: List<ChartPoint>,
    val firstLabel: ChartLabel?,
    val lastLabel: ChartLabel?,
)

/**
 * Where everything in the exercise progress chart goes, as plain numbers. Ported from RN's
 * `progress-chart-layout.ts`, deleted with the RN screen in #200.
 *
 * Split out of the drawing because the rules worth pinning (which dot a session gets, where a label
 * may sit, when one label gives way to the other) are arithmetic, and a test should reach them
 * without a Canvas. Units are the drawing's own, dp on Android; the caller measures the two labels and
 * draws what this returns. RN's also returned the line and area as SVG path strings; a Canvas builds
 * its path from [ChartLayout.points], so a string would only be parsed back.
 *
 * [sessions] oldest first, across [window], which the x axis spans by time rather than
 * by session order. A label width of 0 means not measured yet, and that label isn't placed.
 */
fun progressChartLayout(
    sessions: List<ProgressSession>,
    window: ClosedRange<Instant>,
    width: Float,
    firstLabelWidth: Float,
    lastLabelWidth: Float,
): ChartLayout {
    val baseline = CHART_HEIGHT - PAD_BOTTOM
    if (sessions.isEmpty()) return ChartLayout(baseline, emptyList(), null, null)

    val from = window.start
    val span = maxOf(1L, (window.endInclusive - from).inWholeMilliseconds).toFloat()
    val max = maxOf(sessions.maxOf { it.value }, 0.0)

    fun xOf(at: Instant) = PAD_X + (at - from).inWholeMilliseconds / span * (width - PAD_X * 2)

    // From zero, as the sparkline is: from the minimum, 10 kg to 10.5 kg would draw as a cliff.
    fun yOf(value: Double) = if (max <= 0) baseline else baseline - (value / max).toFloat() * (baseline - PAD_TOP)

    val points =
        sessions.mapIndexed { index, session ->
            val mark =
                when {
                    session.newBest -> ChartMark.NewBest
                    index == sessions.lastIndex -> ChartMark.Latest
                    else -> null
                }
            ChartPoint(xOf(session.at), yOf(session.value), session.at, mark)
        }

    val lastLabel = if (lastLabelWidth > 0) placeLabel(points.last(), lastLabelWidth, Side.End, width) else null
    val firstCandidate =
        if (firstLabelWidth > 0 &&
            points.size > 1
        ) {
            placeLabel(points.first(), firstLabelWidth, Side.Start, width)
        } else {
            null
        }
    // The latest value is the one the screen is about, so when the two would collide the first gives way.
    // Judged on both labels' real boxes, both ways: a fixed gap between the points missed two wide
    // labels ("137.5 lb" and "142.5 lb" need about 88), and close values, the change this chart shows
    // most often, also sit at almost the same height.
    val firstLabel =
        firstCandidate?.takeIf {
            lastLabel != null && !overlaps(it, firstLabelWidth, lastLabel, lastLabelWidth)
        }
    return ChartLayout(baseline, points, firstLabel, lastLabel)
}

private enum class Side { Start, End }

/**
 * A label beside its point, starting at the first and ending at the last, kept inside the drawing.
 *
 * The latest session can sit near the left edge (an exercise trained twice early in the window and not
 * since), where ending its label at the point runs it off the chart. Then the label **flips** to the
 * point's other side rather than sliding just inside the edge, where it lands across the line climbing
 * out of the point. The final clamp only matters for a label wider than the room on either side.
 */
private fun placeLabel(
    point: ChartPoint,
    labelWidth: Float,
    side: Side,
    width: Float,
): ChartLabel {
    val after = point.x + LABEL_NUDGE
    val before = point.x - LABEL_NUDGE - labelWidth
    var x = if (side == Side.Start) after else before
    if (x < 0) {
        x = after
    } else if (x + labelWidth > width) {
        x = before
    }
    x = x.coerceIn(0f, maxOf(0f, width - labelWidth))
    return ChartLabel(x, point.y - LABEL_RISE)
}

private fun overlaps(
    a: ChartLabel,
    aWidth: Float,
    b: ChartLabel,
    bWidth: Float,
): Boolean {
    val apart = a.x + aWidth + LABEL_GAP <= b.x || b.x + bWidth + LABEL_GAP <= a.x
    return !apart && abs(a.y - b.y) < LABEL_HEIGHT
}
