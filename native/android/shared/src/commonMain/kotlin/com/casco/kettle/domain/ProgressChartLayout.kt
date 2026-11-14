package com.casco.kettle.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Instant

/** The drawing's height. Fixed: nothing in it is a control, and every number is in the list below it. */
const val CHART_HEIGHT = 168f

/** Room above the plot for the top tick's label. */
private const val PAD_TOP = 20f

/** Room below the baseline for the dates. */
private const val PAD_BOTTOM = 24f

/** Keeps an end dot's ring inside the drawing rather than clipped at its edge. */
private const val PAD_X = 6f

/** The steps a reader counts in, besides one: [niceStep]'s 1, 2 and 5 times a power of ten. */
private const val TWO = 2.0
private const val FIVE = 5.0
private const val TEN = 10.0

/** How many steps the axis aims for between zero and the top: three lines over the baseline, or about. */
private const val AXIS_STEPS = 3

/** A tick's value is its index times the step, which in binary is 0.6000000000000001 for 3 × 0.2. */
private const val TICK_PRECISION = 1e9

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

/** An axis line: its value in the reader's units, and its height. The one at zero is the baseline. */
data class ChartTick(
    val value: Double,
    val y: Float,
)

data class ChartLayout(
    val baseline: Float,
    val points: List<ChartPoint>,
    /** From zero up, the last at the plot's top; none when every value is zero, with nothing to scale. */
    val ticks: List<ChartTick>,
)

/**
 * Where everything in the exercise progress chart goes, as plain numbers. Ported from RN's
 * `progress-chart-layout.ts`, deleted with the RN screen in #200.
 *
 * Split out of the drawing because the rules worth pinning (which dot a session gets, where the
 * axis's lines fall) are arithmetic, and a test should reach them without a Canvas. Units are the
 * drawing's own, dp on Android; the caller measures the axis's labels and draws what this returns.
 * RN's also returned the line and area as SVG path strings; a Canvas builds its path from
 * [ChartLayout.points], so a string would only be parsed back.
 *
 * [sessions] oldest first, across [window], which the x axis spans by time rather than
 * by session order. [display] turns a stored value into the reader's (kilograms to pounds), so the
 * axis lands on round numbers in the units it is read in; a straight scale, so the line's shape is the
 * same either way. [minStep] is the least step the measure counts in: one for reps, rounds and
 * seconds, so a best of one rep doesn't put a line at half a rep.
 */
fun progressChartLayout(
    sessions: List<ProgressSession>,
    window: ClosedRange<Instant>,
    width: Float,
    display: (Double) -> Double = { it },
    minStep: Double = 0.0,
): ChartLayout {
    val baseline = CHART_HEIGHT - PAD_BOTTOM
    if (sessions.isEmpty()) return ChartLayout(baseline, emptyList(), emptyList())

    val from = window.start
    val span = maxOf(1L, (window.endInclusive - from).inWholeMilliseconds).toFloat()
    val max = maxOf(sessions.maxOf { display(it.value) }, 0.0)
    // The axis's top is a round number at or over the highest value (#413), and the line scales to it,
    // so a gridline's label reads true against the line crossing it.
    val step = if (max > 0) maxOf(niceStep(max / AXIS_STEPS), minStep) else 0.0
    val top = if (step > 0) ceil(max / step) * step else 0.0

    fun xOf(at: Instant) = PAD_X + (at - from).inWholeMilliseconds / span * (width - PAD_X * 2)

    // From zero, as the sparkline is: from the minimum, 10 kg to 10.5 kg would draw as a cliff.
    fun yOf(value: Double) = if (top <= 0) baseline else baseline - (value / top).toFloat() * (baseline - PAD_TOP)

    val points =
        sessions.mapIndexed { index, session ->
            val mark =
                when {
                    session.newBest -> ChartMark.NewBest
                    index == sessions.lastIndex -> ChartMark.Latest
                    else -> null
                }
            ChartPoint(xOf(session.at), yOf(display(session.value)), session.at, mark)
        }
    val ticks =
        if (step <= 0) {
            emptyList()
        } else {
            (0..(top / step).roundToInt()).map {
                val value = round(it * step * TICK_PRECISION) / TICK_PRECISION
                ChartTick(value, yOf(value))
            }
        }
    return ChartLayout(baseline, points, ticks)
}

/** 1, 2 or 5 times a power of ten, the least of them at or over [raw]: steps a reader counts in. */
internal fun niceStep(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    val fraction = raw / magnitude
    val nice =
        when {
            fraction <= 1 -> 1.0
            fraction <= TWO -> TWO
            fraction <= FIVE -> FIVE
            else -> TEN
        }
    return nice * magnitude
}
