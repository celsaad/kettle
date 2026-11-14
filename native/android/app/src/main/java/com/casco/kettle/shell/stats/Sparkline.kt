package com.casco.kettle.shell.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.SPARKLINE_BAR_WIDTH
import com.casco.kettle.domain.SPARKLINE_HEIGHT
import com.casco.kettle.domain.sparklineBars
import com.casco.kettle.domain.sparklineWidth
import com.casco.kettle.shell.theme.KettleTheme

/**
 * One exercise's recent values as a line beside the words that state them, in ink with a dot on the
 * latest (#414; bars until then, RN's `sparkline.tsx`, #201). Where each point goes is `sparklineBars`'
 * decision in `:shared`, the line joining the tops of the bars it would have drawn, so it keeps their
 * rule: **scaled from zero**, so a small gain looks small. A Canvas has no semantics, so a screen reader
 * passes over it; every number it could say is printed beside it.
 */
@Composable
fun Sparkline(points: List<Double>) {
    val ink = KettleTheme.colors.text
    val bars = sparklineBars(points)
    Canvas(Modifier.size(sparklineWidth(points.size).dp, SPARKLINE_HEIGHT.dp)) {
        val tops =
            bars.map { bar ->
                Offset((bar.x + SPARKLINE_BAR_WIDTH / 2).dp.toPx(), (SPARKLINE_HEIGHT - bar.height).dp.toPx())
            }
        val line = Path().apply { tops.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(line, ink, style = Stroke(LINE_WIDTH.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        tops.lastOrNull()?.let { drawCircle(ink, DOT_RADIUS.dp.toPx(), it) }
    }
}

private const val LINE_WIDTH = 2f
private const val DOT_RADIUS = 3f
