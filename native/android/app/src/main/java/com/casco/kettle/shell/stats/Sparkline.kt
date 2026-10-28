package com.casco.kettle.shell.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.SPARKLINE_HEIGHT
import com.casco.kettle.domain.sparklineBars
import com.casco.kettle.domain.sparklineWidth
import com.casco.kettle.shell.theme.KettleTheme

/**
 * One exercise's recent values as bars, beside the words that state them: RN's `sparkline.tsx`
 * (#201). Where the bars go is `sparklineBars`' decision in `:shared`. A Canvas has no semantics, so a
 * screen reader passes over it as RN's hidden one; every number it could say is printed beside it.
 *
 * The latest bar takes the accent and the rest `textSecondary`, not `border`: a hairline colour is far
 * too faint (1.28:1) for a mark carrying the shape this row exists to show.
 */
@Composable
fun Sparkline(points: List<Double>) {
    val colors = KettleTheme.colors
    val bars = sparklineBars(points)
    Canvas(Modifier.size(sparklineWidth(points.size).dp, SPARKLINE_HEIGHT.dp)) {
        bars.forEach { bar ->
            drawRoundRect(
                if (bar.latest) colors.accent else colors.textSecondary,
                topLeft = Offset(bar.x.dp.toPx(), (SPARKLINE_HEIGHT - bar.height).dp.toPx()),
                size = Size(bar.width.dp.toPx(), bar.height.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}
