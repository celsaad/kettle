package com.casco.kettle.shell.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CHART_HEIGHT
import com.casco.kettle.domain.ChartLayout
import com.casco.kettle.domain.ChartMark
import com.casco.kettle.domain.ProgressSession
import com.casco.kettle.domain.progressChartLayout
import com.casco.kettle.shell.theme.KettleColors
import com.casco.kettle.shell.theme.KettleTheme
import kotlin.time.Instant

/**
 * One exercise's value, session by session, across [window]: the chart the Getting stronger row's
 * sparkline is a thumbnail of, ported from RN's `progress-chart.tsx` (#200). Where everything goes is
 * `progressChartLayout`'s decision, in `:shared`; this measures the labels and draws.
 *
 * **One series, one hue, and an axis.** The line takes the accent. A filled dot marks each session
 * that set a new best, which is what the key under the chart names; the latest, when it didn't, gets a
 * hollow ring instead, so a dip at the end can't be read as a record. A y axis of round steps from zero
 * (#413), dashed lines labelled at the right edge, the top one with its unit, replaced labels on the
 * first and last points, which collided with it there. Every value is still in the list below, so
 * there is no tap-for-a-value tooltip.
 *
 * A Canvas, so it has no semantics and TalkBack passes over it, as RN's chart is hidden: the list says
 * everything the drawing does, row by row, in words a screen reader can step through.
 *
 * [display] turns a stored value into the reader's units, [tickLabel] writes an axis line's value
 * (true for the top one), and [dateLabel] formats the dates.
 */
@Composable
fun ProgressChart(
    sessions: List<ProgressSession>,
    window: ClosedRange<Instant>,
    display: (Double) -> Double,
    tickLabel: (value: Double, top: Boolean) -> String,
    dateLabel: (Instant) -> String,
) {
    val colors = KettleTheme.colors
    val measurer = rememberTextMeasurer()
    // In dp and so unscaled by the reader's text size, as RN's SVG text is: the layout's room for the
    // labels is fixed, and every value is also in the list below, which does scale.
    val labelSize = with(LocalDensity.current) { LABEL_SIZE.dp.toSp() }
    val secondary = TextStyle(fontSize = labelSize, color = colors.textSecondary)
    val from = window.start
    val to = window.endInclusive
    val middle = from + (to - from) / 2

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT.dp),
    ) {
        // The layout works in dp, the units its paddings are written in.
        val layout = progressChartLayout(sessions, window, size.width / density, display)
        if (layout.points.isEmpty()) return@Canvas
        drawTicks(layout, colors) { tick, top -> measurer.measure(tickLabel(tick, top), secondary) }
        drawSeries(layout, colors)

        val dateBaseline = (CHART_HEIGHT - DATE_DROP).dp.toPx()

        fun date(at: Instant) = measurer.measure(dateLabel(at), secondary)
        drawAnchored(date(from), Anchor.Start, 0f, dateBaseline)
        drawAnchored(date(middle), Anchor.Middle, size.width / 2, dateBaseline)
        drawAnchored(date(to), Anchor.End, size.width, dateBaseline)
    }
}

/**
 * The axis: a dashed line at each step over the baseline, which [drawSeries] draws solid, and every
 * step's value right-aligned above its line, so the line it labels runs out from under it.
 */
private fun DrawScope.drawTicks(
    layout: ChartLayout,
    colors: KettleColors,
    measure: (Double, Boolean) -> TextLayoutResult,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.dp.toPx(), GAP.dp.toPx()))
    layout.ticks.forEachIndexed { index, tick ->
        val y = tick.y.dp.toPx()
        if (index > 0) drawLine(colors.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
        drawAnchored(
            measure(tick.value, index == layout.ticks.lastIndex),
            Anchor.End,
            size.width,
            y - TICK_RISE.dp.toPx(),
        )
    }
}

/** The baseline, the area under the line, the line, and the marked dots. */
private fun DrawScope.drawSeries(
    layout: ChartLayout,
    colors: KettleColors,
) {
    val points = layout.points.map { Offset(it.x.dp.toPx(), it.y.dp.toPx()) }
    val baseline = layout.baseline.dp.toPx()

    drawLine(colors.border, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
    val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    val area =
        Path().apply {
            addPath(line)
            lineTo(points.last().x, baseline)
            lineTo(points.first().x, baseline)
            close()
        }
    drawPath(area, colors.accent.copy(alpha = AREA_ALPHA))
    drawPath(line, colors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    layout.points.zip(points).forEach { (point, at) ->
        val mark = point.mark ?: return@forEach
        // A new best is a filled dot ringed in the card's colour, so it stays legible on the line;
        // the latest, when it isn't one, is the same size inverted: a ring on the card.
        val newBest = mark == ChartMark.NewBest
        val radius = DOT_RADIUS.dp.toPx()
        drawCircle(if (newBest) colors.accent else colors.backgroundElement, radius, at)
        drawCircle(
            if (newBest) colors.backgroundElement else colors.accent,
            radius,
            at,
            style = Stroke(2.dp.toPx()),
        )
    }
}

private enum class Anchor { Start, Middle, End }

/** SVG's `textAnchor` and baseline-placed text, which `drawText`'s top-left placement isn't. */
private fun DrawScope.drawAnchored(
    measured: TextLayoutResult,
    anchor: Anchor,
    x: Float,
    baseline: Float,
) {
    val width = measured.size.width
    val left =
        when (anchor) {
            Anchor.Start -> x
            Anchor.Middle -> x - width / 2f
            Anchor.End -> x - width
        }
    drawAtBaseline(measured, left, baseline)
}

private fun DrawScope.drawAtBaseline(
    text: TextLayoutResult,
    x: Float,
    baseline: Float,
) = drawText(text, topLeft = Offset(x, baseline - text.firstBaseline))

private const val LABEL_SIZE = 11
private const val DOT_RADIUS = 4
private const val AREA_ALPHA = 0.1f

/** An axis line's dash and gap, and how far above its line a value's baseline sits. */
private const val DASH = 3f
private const val GAP = 4f
private const val TICK_RISE = 3f

/** How far above the drawing's bottom the dates' baseline sits. */
private const val DATE_DROP = 6f
