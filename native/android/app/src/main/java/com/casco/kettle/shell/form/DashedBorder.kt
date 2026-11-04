package com.casco.kettle.shell.form

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * RN's `borderStyle: 'dashed'`, which Compose's `border` has no form of: the workout editor's add
 * buttons and its rest rows, which read as a gap in the workout rather than a thing in it. Drawn
 * inside the bounds, as RN's border is, so it doesn't change the layout.
 */
internal fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    radius: Dp,
): Modifier =
    drawBehind {
        val stroke = width.toPx()
        val dash = stroke * DASH_TO_WIDTH
        drawRoundRect(
            color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = size.copy(width = size.width - stroke, height = size.height - stroke),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        )
    }

/** Android's own dashed border draws its dashes about three widths long, which is what RN shows. */
private const val DASH_TO_WIDTH = 3f
