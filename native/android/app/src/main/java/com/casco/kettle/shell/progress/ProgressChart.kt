package com.casco.kettle.shell.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CHART_HEIGHT
import com.casco.kettle.domain.ChartLayout
import com.casco.kettle.domain.ChartMark
import com.casco.kettle.domain.ProgressSession
import com.casco.kettle.domain.progressChartLayout
import com.casco.kettle.shell.theme.KettleColors
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.datetime.Instant
import kotlin.math.ceil

/**
 * One exercise's value, session by session, across [window]: the chart the Getting stronger row's
 * sparkline is a thumbnail of, ported from RN's `progress-chart.tsx` (#200). Where everything goes is
 * `progressChartLayout`'s decision, in `:shared`; this measures the labels and draws.
 *
 * **One series, one hue, and only two numbers.** The line takes the accent. A filled dot marks each
 * session that set a new best, which is what the key under the chart names; the latest, when it
 * didn't, gets a hollow ring instead, so a dip at the end can't be read as a record. Only the first and
 * last values are labelled: every value is in the session list directly below, which is the table this
 * chart would otherwise need, so there is no y axis, no gridlines, and no tap-for-a-value tooltip.
 *
 * A Canvas, so it has no semantics and TalkBack passes over it, as RN's chart is hidden: the list says
 * everything the drawing does, row by row, in words a screen reader can step through.
 *
 * [firstLabel] and [lastLabel] are finished text in the reader's units; [dateLabel] formats the axis.
 */
@Composable
fun ProgressChart(
    sessions: List<ProgressSession>,
    window: ClosedRange<Instant>,
    firstLabel: String,
    lastLabel: String,
    dateLabel: (Instant) -> String,
) {
    val colors = KettleTheme.colors
    val measurer = rememberTextMeasurer()
    // In dp and so unscaled by the reader's text size, as RN's SVG text is: the layout's room for the
    // labels is fixed, and every value is also in the list below, which does scale.
    val labelSize = with(LocalDensity.current) { LABEL_SIZE.dp.toSp() }
    val secondary = TextStyle(fontSize = labelSize, color = colors.textSecondary)
    val emphasis = TextStyle(fontSize = labelSize, fontWeight = FontWeight.SemiBold, color = colors.text)
    val from = window.start
    val to = window.endInclusive
    val middle = from + (to - from) / 2

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT.dp),
    ) {
        val first = measurer.measure(firstLabel, secondary)
        val last = measurer.measure(lastLabel, emphasis)

        // The layout works in dp, the units its paddings are written in.
        // Label widths round up, as RN's `Math.ceil` on each: a label is never given less room than it
        // takes, which is what decides whether the two collide.
        fun inDp(px: Number) = px.toFloat() / density

        fun labelWidth(px: Int) = ceil(inDp(px))
        val layout =
            progressChartLayout(
                sessions,
                window,
                inDp(size.width),
                labelWidth(first.size.width),
                labelWidth(last.size.width),
            )
        if (layout.points.isEmpty()) return@Canvas
        drawSeries(layout, colors)

        layout.firstLabel?.let { drawAtBaseline(first, it.x.dp.toPx(), it.y.dp.toPx()) }
        layout.lastLabel?.let { drawAtBaseline(last, it.x.dp.toPx(), it.y.dp.toPx()) }

        val dateBaseline = (CHART_HEIGHT - DATE_DROP).dp.toPx()

        fun date(at: Instant) = measurer.measure(dateLabel(at), secondary)
        drawAnchored(date(from), Anchor.Start, 0f, dateBaseline)
        drawAnchored(date(middle), Anchor.Middle, size.width / 2, dateBaseline)
        drawAnchored(date(to), Anchor.End, size.width, dateBaseline)
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

/** How far above the drawing's bottom the dates' baseline sits. */
private const val DATE_DROP = 6f
