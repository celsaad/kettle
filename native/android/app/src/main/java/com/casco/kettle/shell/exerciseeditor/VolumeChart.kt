package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.VOLUME_CHART_HEIGHT
import com.casco.kettle.domain.roundHalfUp
import com.casco.kettle.domain.volumeChartLayout
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The editor's Recent bars, ported from RN's `volume-chart.tsx`: a word-sized chart with no axes,
 * each bar labelled with its value above and its date below. Where the bars go is
 * `volumeChartLayout`'s, in `:shared`; this measures the labels and draws.
 *
 * A Canvas, so TalkBack passes over it: the list right under it spells out every session in words,
 * which is everything the drawing says.
 *
 * [bars] oldest first, as they read left to right: a value and its finished date label.
 */
@Composable
internal fun VolumeChart(bars: List<Pair<Double, String>>) {
    val colors = KettleTheme.colors
    val measurer = rememberTextMeasurer()
    // In dp, unscaled by the reader's text size, as RN's SVG text is: the room for them is fixed, and
    // every value is also in the list below, which does scale.
    val density = LocalDensity.current
    val valueStyle = TextStyle(fontSize = with(density) { VALUE_SIZE.dp.toSp() }, color = colors.textSecondary)
    val dateStyle = TextStyle(fontSize = with(density) { DATE_SIZE.dp.toSp() }, color = colors.textSecondary)

    Canvas(Modifier.fillMaxWidth().height(VOLUME_CHART_HEIGHT.dp)) {
        val layout = volumeChartLayout(bars.map { it.first }, size.width / this.density)
        layout.zip(bars).forEach { (bar, datum) ->
            val (value, date) = datum
            val centre = bar.centerX.dp.toPx()
            val left = centre - bar.width.dp.toPx() / 2
            val top = bar.top.dp.toPx()
            val radius = minOf(BAR_RADIUS.dp.toPx(), bar.width.dp.toPx() / 2, bar.height.dp.toPx())
            // Only the top corners rounded, so a bar stands on the line rather than floating as a pill.
            val shape =
                RoundRect(
                    left,
                    top,
                    left + bar.width.dp.toPx(),
                    top + bar.height.dp.toPx(),
                    topLeftCornerRadius = CornerRadius(radius),
                    topRightCornerRadius = CornerRadius(radius),
                )
            drawPath(Path().apply { addRoundRect(shape) }, colors.accent)
            // RN's `Math.round`.
            val label = roundHalfUp(value).toLong().toString()
            drawCentred(measurer.measure(label, valueStyle), centre, top - VALUE_RISE.dp.toPx())
            drawCentred(measurer.measure(date, dateStyle), centre, (VOLUME_CHART_HEIGHT - DATE_DROP).dp.toPx())
        }
    }
}

/** SVG's `textAnchor="middle"` on a baseline, which `drawText`'s top-left placement isn't. */
private fun DrawScope.drawCentred(
    text: TextLayoutResult,
    x: Float,
    baseline: Float,
) = drawText(text, topLeft = Offset(x - text.size.width / 2f, baseline - text.firstBaseline))

private const val VALUE_SIZE = 11
private const val DATE_SIZE = 10
private const val BAR_RADIUS = 4

/** How far above its bar a value's baseline sits. */
private const val VALUE_RISE = 6

/** How far above the chart's bottom the dates' baseline sits. */
private const val DATE_DROP = 4f
